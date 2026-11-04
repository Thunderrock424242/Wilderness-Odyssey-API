"""Desktop actions around the durable worker; shared by simple and Advanced UI."""
from dataclasses import asdict
import json
from pathlib import Path
import uuid
from PySide6.QtCore import QObject, Signal, Qt
from PySide6.QtWidgets import QDialog, QVBoxLayout, QLabel, QPlainTextEdit, QPushButton, QFileDialog
from tools.aether_teacher.domain import JobBinding, Rejected, bounded_context, compile_profile, digest, encode, require, snapshot
from tools.aether_teacher.jobs import JobCoordinator
from tools.aether_teacher.store import Store
from tools.aether_teacher.setup import admit_disk, sha_file


def restore_selection(store,coordinator):
    selected=store.selection()
    profile=store.active_profile()
    require(profile is not None,"PROFILE_MISSING")
    if selected["version"]=="original":
        return profile,"qwen3.5:4b"
    return profile,coordinator.model_for_job(store.get_job(selected["version"]))


class Workflow(QObject):
    progress = Signal(object)

    def __init__(self, controller):
        super().__init__(controller)
        self.c = controller
        self.current = None
        self.progress.connect(self._progress, Qt.ConnectionType.QueuedConnection)

    def coordinator(self, store):
        return JobCoordinator(store,self.c.settings,self.c.readiness,self.c.runner,self.c.runtime,self.progress.emit)

    def available(self):
        require(self.c.runtime is not None and self.c.runner is not None, "LOCAL_SETUP_REQUIRED")
        require(self.c.operations.pending == 0 and self.c.chat.reply is None and not self.c.chat_pending, "FINISH_CURRENT_OPERATION_FIRST")

    def refresh(self):
        def inspect(store):
            import psutil
            observed={"availableRamMiB":psutil.virtual_memory().available//1048576,"runtimeRssMiB":None}
            runtime=self.c.runtime
            if runtime and runtime.process and runtime.process.poll() is None:
                try:observed["runtimeRssMiB"]=psutil.Process(runtime.process.pid).memory_info().rss//1048576
                except (psutil.NoSuchProcess,psutil.AccessDenied):pass
            jobs=store.jobs()
            if self.c.readiness:
                jobs=tuple(job for job in jobs if job.binding.base_sha256==self.c.readiness["base"]["baseSha256"]
                           and job.binding.engine_sha256==self.c.readiness["engineSha256"])
            return jobs,store.selection(),observed
        self.c.submit(inspect,self._show,storage=True)

    def _show(self, data):
        jobs,selection,observed=data
        self.c.window.versions_view.show_jobs(jobs)
        self.c.window.versions_view.selection.setText(f"App selection: {selection['version']} · selection revision {selection['revision']}")
        self.c.window.advanced_view.jobs.setPlainText("\n".join(f"{job.id}\n{job.state} · {job.phase} · {job.error_code or 'No error'}" for job in jobs) or "No training jobs")
        self.c.window.advanced_view.artifacts.setPlainText("\n\n".join(
            f"{job.id}\nBase: {job.binding.base_sha256}\nDataset: {job.binding.dataset_sha256}\nProfile: {job.binding.profile_sha256}\nEngine: {job.binding.engine_sha256}" for job in jobs) or "No candidate artifacts")
        gpu=next((dict(job.artifact_refs).get("prepare",{}).get("gpu") for job in jobs
                  if dict(job.artifact_refs).get("prepare",{}).get("gpu")),None)
        self.c.window.advanced_view.observed.setPlainText(
            f"Observed available RAM at refresh: {observed['availableRamMiB']} MiB\n"
            f"Observed owned runtime RSS: {observed['runtimeRssMiB'] if observed['runtimeRssMiB'] is not None else 'unavailable'} MiB\n"
            f"Last recorded GPU preparation observation: {gpu if gpu else 'unavailable'}\n"
            "These are observations, separate from the configured memory ceilings.")

    def review(self):
        try:
            self.available()
            selected=self.c.window.lessons_view.selected()
            profile=self.c.profile
            preset=self.c.window.advanced_view.preset.currentText()
            limits=self.c.window.advanced_view.limits()
            data=snapshot(profile,selected,preset)
            # Fixed, frozen bytes are the same ones accepted if the owner proceeds.
            request_id=str(uuid.uuid4())
            binding=JobBinding(request_id,self.c.readiness["base"]["baseSha256"],self.c.readiness["engineSha256"],
                               preset,data.dataset_sha256,data.profile_sha256,limits)
            dialog=QDialog(self.c.window);dialog.setWindowTitle("Review training batch");dialog.resize(760,600)
            layout=QVBoxLayout(dialog)
            layout.addWidget(QLabel(f"{len(selected)} reviewed lessons · {preset}\nProfile revision {profile.revision}\n"
                                    f"RAM ceiling {limits.ram_mib} MiB · GPU budget {limits.gpu_mib} MiB · deadline {limits.timeout_seconds}s"))
            details=QPlainTextEdit();details.setReadOnly(True)
            details.setPlainText(compile_profile(profile)+"\n\n"+"\n\n".join(f"QUESTION\n{v.prompt}\nTARGET\n{v.target}" for v in selected)
                                +f"\n\nDataset SHA256: {data.dataset_sha256}\nProfile SHA256: {data.profile_sha256}")
            layout.addWidget(details)
            accept=QPushButton("Train this reviewed batch")
            accept.clicked.connect(lambda:(dialog.accept(),self.start(binding,data)))
            layout.addWidget(accept)
            dialog.exec()
        except Rejected as error:self.c.status(str(error))

    def start(self,binding,data):
        try:self.available()
        except Rejected as error:self.c.status(str(error));return
        c=self.c
        c.runner.cancelled.clear()
        c.window.teaching_view.send.setEnabled(False)
        c.status("Accepting immutable training job…")
        def execute():
            with Store(c.database) as store:
                admit_disk(((c.workspace,c.readiness["estimatedRunBytes"]),),floor=60*1073741824)
                job=store.accept_job(binding,data)
                self.progress.emit(job)
                if job.state in ("completed","failed","cancelled","interrupted","needs-verification"):
                    return job
                coordinator=self.coordinator(store)
                return coordinator.execute(job.id)
        c.submit(execute,self._finished)

    def _progress(self,job):
        self.current=job if job.state in ("queued","running","cancel-requested") else None
        self.c.status(f"Training {job.state} · {job.phase} · {job.error_code or 'Measured phase; no estimated percentage'}")
        self.refresh()

    def _finished(self,job):
        self.current=None
        self._progress(job)
        if job.state=="completed":
            self.c.status("Candidate verified and ready to test · Choose it explicitly in Versions")
            self.c.window.teaching_view.send.setEnabled(True)
        else:
            self.c.window.runtime_status.setText("LOCAL WORKSPACE\nRuntime stopped")
            self.c.status(f"{job.state} · {job.error_code} · Existing model selection retained")
        self.refresh()

    def cancel(self):
        c=self.c
        if c.runner:c.runner.cancel()
        if c.runtime:c.runtime.cancelled.set()
        if self.current:
            job_id=self.current.id
            c.submit(lambda store:store.cancel_job(job_id),lambda job:self._progress(job),storage=True)
        c.status("Cancellation requested · Owned work is stopping")

    def retry(self):
        try:
            self.available()
            selected=self.c.window.versions_view.selected()
            require(selected is not None,"SELECT_A_VERSION")
        except Rejected as error:self.c.status(str(error));return
        c=self.c;c.runner.cancelled.clear()
        def retry():
            with Store(c.database) as store:
                coordinator=self.coordinator(store)
                store.retry_job(selected.id)
                return coordinator.execute(selected.id)
        c.submit(retry,self._finished)

    def select(self):
        try:
            self.available()
            job=self.c.window.versions_view.selected()
            require(job is not None and job.state=="completed","SELECT_A_VERIFIED_VERSION")
        except Rejected as error:self.c.status(str(error));return
        c=self.c
        def select():
            with Store(c.database) as store:
                coordinator=self.coordinator(store)
                c.runtime.cancelled.clear();c.runtime.ensure_started()
                model=coordinator.model_for_job(job)
                raw=store.job_snapshot(job.id).profile_bytes;profile=json.loads(raw)
                store.select_version(job.id,profile["profileId"],profile["revision"],store.selection()["revision"])
                return store.active_profile(),model
        c.submit(select,self._selected)

    def _selected(self,result):
        self.c.profile,self.c.model=result
        self.c.window.profile_view.show_profile(self.c.profile)
        self.c.window.teaching_view.send.setEnabled(True)
        self.c.status("App model and recorded profile selected · No production activation")
        self.refresh()

    def rollback(self):
        try:self.available()
        except Rejected as error:self.c.status(str(error));return
        c=self.c
        def rollback():
            with Store(c.database) as store:
                c.runtime.cancelled.clear()
                previous=json.loads(store.setting("previousSelection","null"))
                require(previous is not None,"NO_PREVIOUS_SELECTION")
                version=previous["version"]
                model="qwen3.5:4b"
                if version!="original":
                    job=store.get_job(version);require(job is not None and job.state=="completed","VERSION_NOT_VERIFIED")
                    coordinator=self.coordinator(store)
                    model=coordinator.model_for_job(job)
                c.runtime.cancelled.clear();c.runtime.ensure_started()
                store.select_version(version,previous["profileId"],previous["profileRevision"],store.selection()["revision"])
                return store.active_profile(),model
        c.submit(rollback,self._selected)

    def compare(self):
        try:
            self.available()
            job=self.c.window.versions_view.selected()
            require(job is not None and job.state=="completed","SELECT_A_VERIFIED_VERSION")
            questions=tuple(line.strip() for line in self.c.window.versions_view.questions.toPlainText().splitlines() if line.strip())
            require(1<=len(questions)<=8,"COMPARISON_QUESTION_LIMIT")
            profile=self.c.profile
            contexts=tuple(bounded_context(profile,(),question) for question in questions)
        except Rejected as error:self.c.status(str(error));return
        c=self.c;c.runner.cancelled.clear()
        def compare():
            from tools.aether_teacher.chat import request_json,reply_text,chat_payload
            with Store(c.database) as store:
                coordinator=self.coordinator(store);module=coordinator.module()
                bundle=coordinator.paths(job)["bundle"];module.verify_manifest(bundle)
                deployment=module.read_json(bundle/"deployment.json")
                _,actual=module.runtime_blob(c.runtime.store,deployment["candidateName"])
                require(actual==deployment["ollamaGgufSha256"],"RUNTIME_MODEL_CHANGED")
                c.runtime.cancelled.clear();c.runtime.ensure_started()
                results=[]
                for question,context in zip(questions,contexts):
                    for model in ("qwen3.5:4b",deployment["candidateName"]):
                        require(not c.runner.cancelled.is_set(),"CANCELLED")
                        payload=chat_payload(model,profile,context);payload["keep_alive"]=0
                        answer=reply_text(request_json("/api/chat",payload,cancelled=c.runner.cancelled))
                        results.append(f"QUESTION: {question}\nMODEL: {model}\nPROFILE REVISION: {profile.revision}\n{answer}")
                return "\n\n".join(results)
        c.submit(compare,c.window.versions_view.answers.setPlainText)

    def export(self):
        try:
            self.available()
            job=self.c.window.versions_view.selected()
            require(job is not None and job.state=="completed","SELECT_A_VERIFIED_VERSION")
        except Rejected as error:self.c.status(str(error));return
        parent=QFileDialog.getExistingDirectory(self.c.window,"Choose parent folder for a new export")
        if not parent:return
        destination=Path(parent)/("aether-teacher-"+job.id)
        c=self.c;c.runner.cancelled.clear()
        def export():
            from tools.aether_teacher.exports import export_bundle
            with Store(c.database) as store:
                coordinator=self.coordinator(store);module=coordinator.module()
                return export_bundle(coordinator.paths(job)["bundle"],store.job_snapshot(job.id).profile_bytes,destination,
                                     module.verify_manifest,c.runner.cancelled)
        c.submit(export,lambda path:c.status("Verified handoff exported to "+str(path)))
