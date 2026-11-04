"""Durable local workflow; no automatic optimizer replay after a crash."""
from dataclasses import asdict
from dataclasses import replace
import importlib.util
import json
import os
from pathlib import Path
import threading
import uuid
from tools.aether_teacher.domain import JobBinding, Rejected, digest, encode, local_path, require, snapshot
from tools.aether_teacher.setup import admit_disk, sha_file, verify_engine


def recover_records(store, validator):
    for job in store.jobs():
        if job.state not in ("queued", "running", "cancel-requested"):
            continue
        if job.state == "cancel-requested":
            store.stop_job(job.id, "cancelled", "CANCELLED_DURING_SHUTDOWN")
            continue
        if store.phase_claimed(job.id):
            evidence = validator(job)
            if evidence is not None:
                store.complete_phase(job.id, job.phase, evidence)
            elif job.phase == "import":
                store.stop_job(job.id, "needs-verification", "AMBIGUOUS_RUNTIME_IMPORT")
                continue
        if store.get_job(job.id).state != "completed":
            store.stop_job(job.id, "interrupted", "INTERRUPTED_EXPLICIT_RETRY_REQUIRED")
    return tuple(store.jobs())


class JobCoordinator:
    """Construct in the model worker with its own SQLite connection."""
    def __init__(self, store, settings, readiness, runner, runtime, progress=lambda job: None):
        self.store, self.settings, self.readiness = store, settings, readiness
        self.runner, self.runtime, self.progress = runner, runtime, progress

    def assert_binding(self, job):
        require(job.binding.engine_sha256 == self.readiness["engineSha256"]
                and job.binding.base_sha256 == self.readiness["base"]["baseSha256"], "ACCEPTED_BINDING_CHANGED")
        path=local_path(self.settings.approval_path)
        require(path.is_file() and path.stat().st_size<=65536,"BASE_APPROVAL_MISSING")
        current=json.loads(path.read_bytes())
        require(current.get("baseSha256")==job.binding.base_sha256,"ACCEPTED_BINDING_CHANGED")

    def module(self):
        verify_engine(self.settings.engine_root, self.runner.manifest)
        source = self.settings.engine_root / "tools/aether/laptop.py"
        spec = importlib.util.spec_from_file_location("teacher_verified_engine", source)
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        module.configure_local(self.settings.workspace_root / "engine-state", 11435)
        return module

    def submit(self, request_id, profile_id, lesson_refs, preset_id, limits):
        profile = self.store.active_profile()
        require(profile is not None and profile.id == profile_id, "PROFILE_MISSING")
        lessons = {lesson.id:lesson for lesson in self.store.lessons()}
        require(all(key in lessons and lessons[key].revision == revision for key, revision in lesson_refs), "STALE_LESSON")
        data = snapshot(profile, tuple(lessons[key] for key, _ in lesson_refs), preset_id)
        binding = JobBinding(request_id, self.readiness["base"]["baseSha256"], self.readiness["engineSha256"],
                             preset_id, data.dataset_sha256, data.profile_sha256, limits)
        admit_disk(((self.settings.workspace_root, self.readiness["estimatedRunBytes"]),), floor=60 * 1073741824)
        return self.store.accept_job(binding, data)

    def paths(self, job):
        root = local_path(self.settings.workspace_root / "jobs" / job.id)
        return {key: root / value for key, value in {"root":".", "dataset":"dataset.json", "profile":"profile.json",
                "candidate":"candidate", "merged":"merged", "receipt":"receipt.json", "bundle":"model-bundle",
                "probe":"probe.json"}.items()}

    def _inputs(self, job):
        paths = self.paths(job)
        local_path(paths["root"]).mkdir(parents=True, exist_ok=True)
        data = self.store.job_snapshot(job.id)
        for key, raw in (("dataset", data.dataset_bytes), ("profile", data.profile_bytes)):
            path = local_path(paths[key])
            if not path.exists():
                with path.open("xb") as stream:
                    stream.write(raw); stream.flush(); os.fsync(stream.fileno())
            require(path.read_bytes() == raw, "ACCEPTED_INPUT_CHANGED")
        return paths

    def verify_phase(self, job):
        self.assert_binding(job)
        module = self.module(); paths = self.paths(job)
        data = self.store.job_snapshot(job.id)
        try:
            if job.phase == "prepare":
                return None  # The read-only hardware check can be explicitly retried.
            if job.phase == "train":
                if not paths["candidate"].exists(): return None
                metadata = module.validate_candidate(paths["candidate"], self.readiness["base"])
                require(metadata["datasetHash"] == data.dataset_sha256 and metadata.get("profileSha256") == data.profile_sha256, "CANDIDATE_INPUT_MISMATCH")
                require(metadata["approvedBaseSha256"] == job.binding.base_sha256 and metadata["presetId"] == job.binding.preset_id, "CANDIDATE_INPUT_MISMATCH")
                return {"verified":True, "sha256":sha_file(paths["candidate"] / "package.json")}
            if job.phase == "export":
                if not paths["merged"].exists(): return None
                module.verify_manifest(paths["merged"])
                deployment = module.read_json(paths["merged"] / "deployment.json")
                require(deployment["baseSha256"] == job.binding.base_sha256 and deployment["datasetHash"] == data.dataset_sha256
                        and deployment.get("profileSha256") == data.profile_sha256, "MERGED_INPUT_MISMATCH")
                return {"verified":True,"sha256":sha_file(paths["merged"] / "package.json")}
            if job.phase == "import":
                if not paths["receipt"].is_file(): return None
                deployment = module.read_json(paths["merged"] / "deployment.json")
                receipt = module.read_json(paths["receipt"])
                # Offline blob and manifest verification; no runtime needed to reconcile.
                blob, actual = module.runtime_blob(self.runtime.store, deployment["candidateName"])
                module.validate_import_receipt(receipt, deployment["candidateName"], sha_file(paths["merged"] / "package.json"),
                                               actual, receipt.get("runtimeModelfileSha256"))
                return {"verified":True,"sha256":sha_file(paths["receipt"]),"model":deployment["candidateName"]}
            if job.phase == "probe":
                if not paths["probe"].is_file(): return None
                probe = module.read_json(paths["probe"])
                require(probe.get("profileSha256") == data.profile_sha256 and probe.get("receiptSha256") == sha_file(paths["receipt"])
                        and probe.get("replySha256") and probe.get("verified") is True, "PROBE_BINDING_MISMATCH")
                return {"verified":True,"sha256":sha_file(paths["probe"])}
            if job.phase == "bundle":
                if not paths["bundle"].exists(): return None
                module.verify_manifest(paths["bundle"])
                deployment = module.read_json(paths["bundle"] / "deployment.json")
                require(deployment["baseSha256"] == job.binding.base_sha256 and deployment["datasetHash"] == data.dataset_sha256
                        and deployment.get("profileSha256") == data.profile_sha256
                        and deployment["importReceiptSha256"] == sha_file(paths["receipt"]), "BUNDLE_INPUT_MISMATCH")
                return {"verified":True,"sha256":sha_file(paths["bundle"] / "package.json")}
        except (OSError, ValueError, KeyError):
            return None
        return None

    def recover(self):
        for job in self.store.jobs():
            if job.state in ("queued","running","cancel-requested"):
                try:self.assert_binding(job)
                except Rejected:self.store.stop_job(job.id,"needs-verification","ACCEPTED_BINDING_CHANGED")
        return recover_records(self.store, self.verify_phase)

    def model_for_job(self, job):
        require(job is not None and job.state=="completed","VERSION_NOT_VERIFIED")
        require(self.verify_phase(replace(job,phase="bundle")) is not None,"VERSION_ARTIFACT_CHANGED")
        paths=self.paths(job);module=self.module()
        deployment=module.read_json(paths["bundle"]/"deployment.json")
        receipt=module.read_json(paths["receipt"])
        self.runtime.ensure_started()
        blob,actual=module.runtime_blob(self.runtime.store,deployment["candidateName"])
        require(actual==deployment["ollamaGgufSha256"],"RUNTIME_MODEL_CHANGED")
        modelfile=module.runtime_show(deployment["candidateName"],blob,receipt.get("sourceTemplateSha256"))
        module.validate_import_receipt(receipt,deployment["candidateName"],deployment["mergedPackageManifestSha256"],actual,
                                       digest(modelfile.encode("utf-8")))
        return deployment["candidateName"]

    def cancel(self, job_id):
        job = self.store.cancel_job(job_id)
        self.runner.cancel(job_id)
        return job

    def execute(self, job_id):
        from tools.aether_teacher.chat import request_json, reply_text
        from tools.aether_teacher.domain import Profile, bounded_context
        job = self.store.get_job(job_id)
        require(job is not None and job.state in ("queued", "running"), "JOB_NOT_RUNNABLE")
        paths = self.paths(job)
        try:
            if self.runner.cancelled.is_set() or self.runtime.cancelled.is_set():
                return self.store.cancel_job(job_id)
            self.assert_binding(job)
            paths = self._inputs(job)
            name = "aether-candidate:teacher-" + job.id
            while True:
                job = self.store.get_job(job_id)
                if job.state == "cancelled": return job
                require(not self.runner.cancelled.is_set() and job.state != "cancel-requested", "CANCELLED")
                self.assert_binding(job)
                require(self.store.claim_phase(job_id, job.phase), "PHASE_ALREADY_CLAIMED")
                self.progress(self.store.get_job(job_id))
                phase = job.phase
                if phase == "prepare":
                    self.runtime.stop()
                    admit_disk(((self.settings.workspace_root, self.readiness["estimatedRunBytes"]),), floor=60 * 1073741824)
                    info = self.runner.run("doctor", memory_mib=job.binding.limits.ram_mib, timeout_seconds=90)
                    require(info.get("cudaKernelPassed") is True and info.get("freeGpuMiB", 0) >= job.binding.limits.gpu_mib, "GPU_CAPACITY_UNAVAILABLE")
                    evidence = {"verified":True,"gpu":info}
                elif phase == "train":
                    limits = job.binding.limits
                    self.runner.run("train", ["--base-approval", self.settings.approval_path, "--dataset", paths["dataset"],
                        "--dataset-sha256", job.binding.dataset_sha256, "--profile-snapshot", paths["profile"],
                        "--profile-sha256", job.binding.profile_sha256, "--preset", job.binding.preset_id, "--output", paths["candidate"],
                        "--acknowledge-reviewed-data", "--memory-mib", str(limits.ram_mib), "--gpu-memory-mib", str(limits.gpu_mib),
                        "--cpu-threads", str(limits.cpu_threads), "--timeout-seconds", str(limits.timeout_seconds)],
                        limits.ram_mib, limits.timeout_seconds + 180)
                    evidence = self.verify_phase(job)
                elif phase == "export":
                    self.runner.run("export", ["--base-approval", self.settings.approval_path, "--candidate", paths["candidate"],
                        "--output", paths["merged"], "--name", name, "--memory-mib", "8192", "--timeout-seconds", "1800"], 8192, 1980)
                    evidence = self.verify_phase(job)
                elif phase == "import":
                    self.runtime.ensure_started()
                    self.runner.run("import-runtime", ["--base-approval", self.settings.approval_path, "--merged-package", paths["merged"],
                        "--store", self.runtime.store, "--ollama-executable", self.settings.ollama_executable, "--receipt", paths["receipt"],
                        "--timeout-seconds", "1800"], 4096, 1980)
                    evidence = self.verify_phase(job)
                elif phase == "probe":
                    self.runtime.ensure_started()
                    # Use the exact accepted profile; never today's edited draft.
                    system = json.loads(paths["profile"].read_bytes())["systemPrompt"]
                    response = request_json("/api/chat", {"model":name, "messages":[{"role":"system","content":system},
                        {"role":"user","content":"Please introduce your role in one short sentence."}], "stream":False,
                        "keep_alive":0, "options":{"num_predict":128,"num_ctx":8192}}, cancelled=self.runner.cancelled)
                    answer = reply_text(response)
                    raw = encode({"verified":True,"profileSha256":job.binding.profile_sha256,
                                  "receiptSha256":sha_file(paths["receipt"]),"replySha256":digest(answer.encode("utf-8"))})
                    with paths["probe"].open("xb") as stream:
                        stream.write(raw); stream.flush(); os.fsync(stream.fileno())
                    evidence = self.verify_phase(job)
                else:
                    self.runtime.ensure_started()
                    self.runner.run("bundle", ["--merged-package", paths["merged"], "--model", name, "--store", self.runtime.store,
                        "--output", paths["bundle"], "--import-receipt", paths["receipt"]], 4096, 1800)
                    evidence = self.verify_phase(job)
                require(evidence is not None, "PHASE_ARTIFACT_NOT_VERIFIED")
                result = self.store.complete_phase(job_id, phase, evidence)
                self.progress(result)
                if result.state in ("completed", "cancelled"):
                    return result
        except Exception as error:
            import re
            category = str(error) if re.fullmatch("[A-Z0-9_]{1,96}", str(error)) else "WORKFLOW_FAILED"
            cancelled = self.runner.cancelled.is_set() or self.store.get_job(job_id).state == "cancel-requested"
            try:self.runtime.stop()
            finally:
                result = self.store.stop_job(job_id, "cancelled" if cancelled else "failed", "CANCELLED" if cancelled else category)
            details = getattr(error, "details", None)
            if isinstance(details, dict):
                try:
                    # Optional numeric diagnostics cannot block owned cleanup/state.
                    (paths["root"] / "failure.json").write_bytes(encode({"error":category, **details}))
                except (OSError, ValueError):pass
            self.progress(result)
            return result
