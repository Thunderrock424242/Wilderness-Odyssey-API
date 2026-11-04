"""Explicit local GPU training and portable Ollama export. Never imported by HTTP routes.

The hosted worker keeps its Linux/cgroup admission. This CLI is a local operator
workflow, with a separate CUDA environment, immutable approved inputs and a bounded
child process. No command downloads a model or activates a production model.
"""
import argparse
from contextlib import contextmanager
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / ".codex-build/aether-laptop"
PROFILES = {
    "laptop-lora-smoke": {"bounds": (8, 256, 8), "chatTemplate": True, "quantization": "none"},
    "laptop-qlora-smoke": {"bounds": (8, 256, 8), "chatTemplate": True, "quantization": "nf4"},
    "laptop-qlora-small": {"bounds": (100, 1024, 100), "chatTemplate": True, "quantization": "nf4"},
}


def require(valid, category="REJECTED_LOCAL_INPUT"):
    if not valid:
        raise ValueError(category)


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        while chunk := stream.read(1048576):
            digest.update(chunk)
    return digest.hexdigest()


def safe_path(path):
    path = Path(path).absolute()
    for part in [path, *path.parents]:
        require(not part.is_symlink() and not (hasattr(part, "is_junction") and part.is_junction()), "LINKED_PATH")
    return path.resolve()


def new_output(path, inputs):
    path = safe_path(path)
    require(not path.exists(), "OUTPUT_ALREADY_EXISTS")
    for source in inputs:
        source = safe_path(source)
        require(path != source and source not in path.parents and path not in source.parents, "OVERLAPPING_OUTPUT")
    return path


def strict_json(raw):
    def pairs(entries):
        result = {}
        for key, value in entries:
            require(key not in result, "DUPLICATE_JSON_MEMBER")
            result[key] = value
        return result
    def invalid_constant(value):
        raise ValueError("INVALID_JSON_NUMBER")
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=invalid_constant)


def read_json(path, limit=65536):
    path = safe_path(path)
    require(path.is_file() and path.stat().st_size <= limit, "INPUT_FILE_LIMIT")
    return strict_json(path.read_bytes().decode("utf-8", errors="strict"))


def text(value, minimum, maximum):
    require(isinstance(value, str))
    # Match the peer contract's UTF-16 character bounds and reject lone surrogates.
    units = len(value.encode("utf-16-le", errors="strict")) // 2
    require(minimum <= units <= maximum)
    return value


def read_dataset(path, digest, maximum):
    path = safe_path(path)
    require(re.fullmatch("[a-f0-9]{64}", digest) is not None)
    require(path.is_file() and path.stat().st_size <= 262144, "DATASET_BYTE_LIMIT")
    require(sha(path) == digest, "SNAPSHOT_HASH_MISMATCH")
    items = read_json(path, 262144)
    require(isinstance(items, list) and 1 <= len(items) <= maximum, "EXAMPLE_LIMIT")
    identities = set()
    for item in items:
        require(isinstance(item, dict) and set(item) == {"id", "revision", "prompt", "target"})
        identifier = text(item["id"], 1, 96)
        require(re.fullmatch("[A-Za-z0-9_-]+", identifier) is not None and identifier not in identities)
        identities.add(identifier)
        text(item["revision"], 1, 120); text(item["prompt"], 1, 4000); text(item["target"], 1, 12000)
    return items


def core():
    source = ROOT / "aether-server/src/main/resources/training/trainer.py"
    spec = importlib.util.spec_from_file_location("aether_training_core", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def owned_job(memory_mib):
    spec = importlib.util.spec_from_file_location("aether_local_process", Path(__file__).with_name("local_process.py"))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module.OwnedJob(memory_mib)


@contextmanager
def temporary_work(path):
    path.mkdir()
    try: yield path
    finally: shutil.rmtree(path)


def model_id(value):
    require(isinstance(value, str) and re.fullmatch("[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}", value)
            and "://" not in value and ".." not in value, "INVALID_MODEL_ID")
    return value


def candidate_name(value):
    model_id(value)
    require(re.fullmatch("aether-candidate:[a-z0-9][a-z0-9_-]{0,79}", value) is not None, "CANDIDATE_NAME_REQUIRED")
    return value


def files_in(directory):
    directory = safe_path(directory)
    paths = []
    for path in directory.rglob("*"):
        safe_path(path)
        if path.is_file():
            require(len(paths) < 4096, "FILE_COUNT_LIMIT")
            paths.append(path)
    return sorted(paths)


def observed_disk_bytes(directory):
    total = 0
    if Path(directory).exists():
        for path in files_in(directory):
            try: total += path.stat().st_size
            except FileNotFoundError: pass  # Serializer atomically renamed its own temporary file.
    return total


def native_frames(raw):
    return [{"file":Path(filename).name,"line":int(line),"function":function}
            for filename,line,function in re.findall(r'File "([^"]+)", line ([0-9]+) in ([A-Za-z0-9_<>]+)',raw)[-8:]]


def write_json(path, value):
    with Path(path).open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n"); stream.flush(); os.fsync(stream.fileno())


def write_manifest(directory, provenance):
    directory = safe_path(directory)
    inventory = [{"path": p.relative_to(directory).as_posix(), "bytes": p.stat().st_size, "sha256": sha(p)}
                 for p in files_in(directory) if p != directory / "package.json"]
    write_json(directory / "package.json", {"schemaVersion": "1.0", "provenance": provenance, "files": inventory})


def verify_manifest(directory):
    directory = safe_path(directory); manifest = read_json(directory / "package.json", 1048576)
    require(set(manifest) == {"schemaVersion", "provenance", "files"} and manifest["schemaVersion"] == "1.0")
    require(isinstance(manifest["files"], list) and 1 <= len(manifest["files"]) <= 4096)
    actual = {p.relative_to(directory).as_posix() for p in files_in(directory) if p != directory / "package.json"}
    names = set()
    for entry in manifest["files"]:
        require(set(entry) == {"path", "bytes", "sha256"})
        relative = entry["path"]
        require(isinstance(relative, str) and "\\" not in relative and not relative.startswith("/")
                and all(part not in ("", ".", "..") for part in relative.split("/")))
        require(relative not in names); names.add(relative)
        path = safe_path(directory / relative)
        require(directory in path.parents and path.is_file())
        require(path.stat().st_size == entry["bytes"] and sha(path) == entry["sha256"], "PACKAGE_HASH_MISMATCH")
    require(actual == names, "PACKAGE_FILE_SET_MISMATCH")
    return manifest


def portable_modelfile(value, blob):
    lines = value.splitlines(); found = 0
    for index,line in enumerate(lines):
        if line.startswith("FROM "):
            require(found == 0 and safe_path(line[5:].strip().strip('"')) == safe_path(blob), "MODELFILE_BASE_MISMATCH")
            lines[index] = "FROM ./model.gguf"; found += 1
        require(not re.match(r"^(SYSTEM|MESSAGE|ADAPTER)\s",line,re.IGNORECASE), "UNEXPECTED_MODELFILE_DIRECTIVE")
    require(found == 1,"MODELFILE_BASE_REQUIRED")
    return "\n".join(lines) + "\n"


def runtime_blob(store, name):
    base_name, tag = candidate_name(name).split(":")
    manifest = read_json(store / "manifests/registry.ollama.ai/library" / base_name / tag)
    layers = [v for v in manifest["layers"] if v["mediaType"] == "application/vnd.ollama.image.model"]
    require(len(layers) == 1,"ONE_MODEL_REQUIRED"); layer = layers[0]
    require(re.fullmatch("sha256:[a-f0-9]{64}",layer["digest"]) is not None)
    blob = safe_path(store / "blobs" / layer["digest"].replace(":","-"))
    require(blob.stat().st_size == layer["size"] and blob.stat().st_size <= 16 * 1073741824)
    require(sha(blob) == layer["digest"][7:], "OLLAMA_BLOB_HASH_MISMATCH")
    return blob, layer["digest"][7:]


def validate_import_receipt(receipt, name, merged_hash, blob_hash, runtime_hash=None, allow_legacy=False):
    keys = {"schemaVersion","candidateName","mergedPackageManifestSha256","ggufSha256","quantization"}
    if receipt["schemaVersion"] == "1.0":
        require(allow_legacy and set(receipt) == keys,"CHAT_TEMPLATE_RECEIPT_REQUIRED")
    else:
        require(receipt["schemaVersion"] == "1.1" and set(receipt) == keys | {"runtimeModelfileSha256","sourceTemplateSha256"})
        require(re.fullmatch("[a-f0-9]{64}",receipt["sourceTemplateSha256"]) is not None)
        require(runtime_hash is not None and receipt["runtimeModelfileSha256"] == runtime_hash,"RUNTIME_TEMPLATE_BINDING_MISMATCH")
    require(receipt["quantization"] == "q4_K_M"
            and receipt["candidateName"] == name and receipt["mergedPackageManifestSha256"] == merged_hash
            and receipt["ggufSha256"] == blob_hash,"RUNTIME_IMPORT_BINDING_MISMATCH")


def runtime_modelfile(base, store, model_source):
    origin = read_json(base / "source.json")
    source = ollama_source(store,origin["ollamaTag"])
    require(source["modelSha256"] == origin["sourceGgufSha256"],"SOURCE_OLLAMA_MODEL_CHANGED")
    require(source["licenseSha256"] == origin["licenseSha256"],"SOURCE_OLLAMA_LICENSE_CHANGED")
    name, tag = source["tag"].split(":")
    manifest = read_json(store / "manifests/registry.ollama.ai/library" / name / tag)
    def layer(kind):
        entries = [v for v in manifest["layers"] if v["mediaType"] == "application/vnd.ollama.image."+kind]
        require(len(entries)==1,"SOURCE_OLLAMA_TEMPLATE_REQUIRED"); entry=entries[0]
        require(re.fullmatch("sha256:[a-f0-9]{64}",entry["digest"]) is not None)
        path = safe_path(store / "blobs" / entry["digest"].replace(":","-"))
        require(path.stat().st_size == entry["size"] and path.stat().st_size <= 65536
                and sha(path)==entry["digest"][7:],"SOURCE_TEMPLATE_HASH_MISMATCH")
        return path
    template = layer("template").read_text(encoding="utf-8")
    require(source["license"].stat().st_size <= 65536,"INVALID_SOURCE_TEMPLATE")
    license_text = source["license"].read_text(encoding="utf-8")
    require(template.strip() and '"""' not in template and '\x00' not in template
            and '"""' not in license_text and len(license_text.encode("utf-8")) <= 65536,"INVALID_SOURCE_TEMPLATE")
    parameters = read_json(layer("params")); stops=parameters.get("stop",[])
    require(isinstance(stops,list) and len(stops)<=16)
    for stop in stops: text(stop,1,128)
    content = 'FROM "'+safe_path(model_source).as_posix()+'"\nTEMPLATE """'+template+'"""\n'
    content += "PARAMETER num_ctx 2048\nPARAMETER num_predict 256\n"
    content += "".join("PARAMETER stop "+json.dumps(stop)+"\n" for stop in stops)
    content += 'LICENSE """'+license_text+'"""\n'
    return content, hashlib.sha256(template.strip().encode("utf-8")).hexdigest()


def runtime_show(name, blob, expected_template_hash=None):
    import http.client
    connection = http.client.HTTPConnection("127.0.0.1",11434,timeout=30)
    try:
        connection.request("POST","/api/show",body=json.dumps({"model":name}),headers={"Content-Type":"application/json"})
        response = connection.getresponse(); require(response.status == 200,"OLLAMA_SHOW_FAILED")
        raw = response.read(131073); require(len(raw) <= 131072,"OLLAMA_SHOW_RESPONSE_LIMIT")
        data = strict_json(raw.decode("utf-8"))
        if expected_template_hash is not None:
            require(isinstance(data.get("template"),str) and
                    hashlib.sha256(data["template"].strip().encode("utf-8")).hexdigest() == expected_template_hash,
                    "RUNTIME_TEMPLATE_NOT_PRESERVED")
        return portable_modelfile(data["modelfile"],blob)
    finally: connection.close()


def require_local_ollama():
    import http.client
    connection = None
    try:
        connection = http.client.HTTPConnection("127.0.0.1",11434,timeout=5)
        connection.request("GET","/api/version"); response = connection.getresponse()
        raw = response.read(4097)
        require(response.status == 200 and len(raw) <= 4096 and isinstance(strict_json(raw.decode("utf-8")).get("version"),str),
                "LOCAL_OLLAMA_NOT_RUNNING")
    except (OSError,http.client.HTTPException): raise ValueError("LOCAL_OLLAMA_NOT_RUNNING") from None
    finally:
        if connection is not None: connection.close()


def import_runtime(args):
    require_local_ollama()
    merged = safe_path(args.merged_package); verify_manifest(merged)
    deployment = read_json(merged / "deployment.json"); name = candidate_name(deployment["candidateName"])
    merged_hash = sha(merged / "package.json")
    approved, base = approval(args.base_approval)
    require(deployment["baseSha256"] == approved["baseSha256"] and deployment["baseModelId"] == approved["modelId"],"CANDIDATE_BASE_MISMATCH")
    store = safe_path(args.store or Path.home() / ".ollama/models")
    executable = safe_path(args.ollama_executable)
    require(executable.is_file() and executable.name.lower() in ("ollama","ollama.exe"),"OLLAMA_EXECUTABLE_REQUIRED")
    receipt_path = new_output(args.receipt,[merged,store])
    require(10 <= args.timeout_seconds <= 1800)
    base_name, tag = name.split(":")
    work = STATE / ("runtime-"+uuid.uuid4().hex)
    with TrainingLock(), owned_job(1024) as job, temporary_work(work):
        model_source = merged / "model"; previous_digest = None
        if args.resume_import_receipt:
            model_source, previous_digest = runtime_blob(store,name)
            current = runtime_show(name,model_source)
            validate_import_receipt(read_json(args.resume_import_receipt),name,merged_hash,previous_digest,
                                    hashlib.sha256(current.encode("utf-8")).hexdigest(),allow_legacy=True)
        else:
            require(not (store / "manifests/registry.ollama.ai/library" / base_name / tag).exists(),"CANDIDATE_TAG_ALREADY_EXISTS")
        content, source_template_hash = runtime_modelfile(base,store,model_source)
        # Ollama retains newlines inside triple-quoted Go templates. Windows
        # text-mode CRLF translation would alter the trained header formatting.
        (work / "Modelfile").write_bytes(content.encode("utf-8"))
        process = None; started = time.monotonic()
        try:
            command = [str(executable),"create",name,"-f","Modelfile"]
            if previous_digest is None: command += ["--quantize","q4_K_M"]
            process = subprocess.Popen(command,cwd=work,
                                       env=ollama_environment(os.environ,Path.home()),
                                       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            job.attach(process)
            while process.poll() is None:
                require(time.monotonic()-started <= args.timeout_seconds,"OLLAMA_IMPORT_DEADLINE_EXCEEDED")
                time.sleep(.5)
            require(process.returncode == 0,"OLLAMA_IMPORT_FAILED")
            verify_manifest(merged); require(sha(merged / "package.json") == merged_hash,"MERGED_PACKAGE_CHANGED")
            blob, digest = runtime_blob(store,name)
            require(previous_digest is None or previous_digest == digest,"RUNTIME_WEIGHTS_CHANGED")
            actual_modelfile = runtime_show(name,blob,source_template_hash)
            # Bind the actual rendered runtime template/parameters, not a mutable tag alone.
            receipt = {"schemaVersion":"1.1","candidateName":name,"mergedPackageManifestSha256":merged_hash,
                       "ggufSha256":digest,"quantization":"q4_K_M","sourceTemplateSha256":source_template_hash,
                       "runtimeModelfileSha256":hashlib.sha256(actual_modelfile.encode("utf-8")).hexdigest()}
            receipt_path.parent.mkdir(parents=True,exist_ok=True); write_json(receipt_path,receipt)
            return {"model":name,"receipt":str(receipt_path),"ggufSha256":digest,"activation":"not-performed"}
        finally:
            if process is not None: stop_owned(process)


def bundle(args):
    merged = safe_path(args.merged_package); verify_manifest(merged)
    deployment = read_json(merged / "deployment.json")
    name = candidate_name(args.model); require(name == deployment["candidateName"], "CANDIDATE_NAME_MISMATCH")
    store = safe_path(args.store or Path.home() / ".ollama/models")
    blob, digest = runtime_blob(store,name)
    modelfile = runtime_show(name,blob)
    validate_import_receipt(read_json(args.import_receipt),name,sha(merged / "package.json"),digest,
                            hashlib.sha256(modelfile.encode("utf-8")).hexdigest())
    output = new_output(args.output,[merged,store]); work = STATE / ("bundle-" + uuid.uuid4().hex)
    with TrainingLock():
        work.mkdir(); staged = work / "output"; staged.mkdir()
        try:
            output.parent.mkdir(parents=True,exist_ok=True)
            require(output.parent.stat().st_dev == work.stat().st_dev,"OUTPUT_MUST_SHARE_STATE_VOLUME")
            require(shutil.disk_usage(work).free >= blob.stat().st_size + 256 * 1048576,"BUNDLE_DISK_HEADROOM_REQUIRED")
            shutil.copyfile(blob,staged / "model.gguf")
            require(sha(staged / "model.gguf") == digest,"PACKAGE_HASH_MISMATCH")
            (staged / "Modelfile").write_bytes(modelfile.encode("utf-8"))
            for filename in ("LICENSE","LICENSE.txt","README.md"):
                if (merged / "model" / filename).is_file(): shutil.copyfile(merged / "model" / filename,staged / filename)
            provenance = {**deployment,"mergedPackageManifestSha256":sha(merged / "package.json"),
                          "ollamaGgufSha256":digest,"importReceiptSha256":sha(args.import_receipt),
                          "activation":"separate-reviewed-operation"}
            write_json(staged / "deployment.json",provenance); write_manifest(staged,provenance); verify_manifest(staged)
            require(not output.exists(),"OUTPUT_ALREADY_EXISTS"); staged.rename(output)
            return {"package":str(output),"model":name,"ggufSha256":digest,"activation":"not-performed"}
        finally: shutil.rmtree(work)


def approve(args):
    base = safe_path(args.directory); output = new_output(args.output, [base])
    require(base.is_dir() and (base / "config.json").is_file() and list(base.glob("*.safetensors")))
    require((base / "tokenizer_config.json").is_file())
    output.parent.mkdir(parents=True, exist_ok=True)
    write_json(output, {"schemaVersion": "1.0", "modelId": model_id(args.model_id), "modelDirectory": str(base),
                        "baseSha256": core().model_digest(base)})
    return {"approval": str(output), "baseSha256": read_json(output)["baseSha256"]}


def ollama_source(store, tag):
    require(re.fullmatch("[a-z0-9][a-z0-9._-]{0,79}:[a-z0-9][a-z0-9._-]{0,79}", tag) is not None, "LOCAL_OLLAMA_TAG_REQUIRED")
    store = safe_path(store); name, version = tag.split(":")
    manifest = read_json(store / "manifests/registry.ollama.ai/library" / name / version)
    result = {"tag": tag}
    for kind, key in [("model", "gguf"), ("license", "license")]:
        layers = [v for v in manifest["layers"] if v["mediaType"] == "application/vnd.ollama.image." + kind]
        require(len(layers) == 1, "ONE_MODEL_AND_LICENSE_REQUIRED")
        layer = layers[0]; digest = layer["digest"]
        require(re.fullmatch("sha256:[a-f0-9]{64}", digest) is not None)
        path = safe_path(store / "blobs" / digest.replace(":", "-"))
        require(path.stat().st_size == layer["size"] and sha(path) == digest[7:], "OLLAMA_BLOB_HASH_MISMATCH")
        result[key] = path
        result[kind + "Sha256"] = digest[7:]
    return result


def import_ollama(args):
    store = args.store or str(Path.home() / ".ollama/models")
    source = ollama_source(store, args.model)
    output = new_output(args.output, [store])
    return bounded_child("_import", {"store":str(safe_path(store)), "model":args.model,
                         "output":str(output), "sourceSha256":source["modelSha256"]},
                         args.memory_mib, args.timeout_seconds)


def import_child(settings):
    source = ollama_source(settings["store"], settings["model"])
    require(source["modelSha256"] == settings["sourceSha256"], "OLLAMA_BLOB_HASH_MISMATCH")
    # This adapter deliberately supports Llama only; no remote model code.
    import gguf_import
    output = new_output(settings["output"], [settings["store"]])
    gguf_import.convert(source["gguf"], output)
    from transformers import AutoTokenizer
    require(bool(AutoTokenizer.from_pretrained(output, local_files_only=True, trust_remote_code=False).chat_template), "CHAT_TEMPLATE_REQUIRED")
    shutil.copyfile(source["license"], output / "LICENSE")
    provenance = {"ollamaTag":settings["model"], "sourceGgufSha256":source["modelSha256"],
                  "licenseSha256":source["licenseSha256"], "dequantizedFromGguf":True,
                  "originalPrecisionRecovered":False, "converterSha256":sha(Path(__file__).with_name("gguf_import.py"))}
    write_json(output / "source.json", provenance)
    return {"directory":str(output), "source":provenance, "baseSha256":core().model_digest(output)}


def approval(path):
    data = read_json(path)
    require(set(data) == {"schemaVersion", "modelId", "modelDirectory", "baseSha256"} and data["schemaVersion"] == "1.0")
    model_id(data["modelId"]); base = safe_path(data["modelDirectory"])
    require(core().model_digest(base) == data["baseSha256"], "APPROVED_BASE_HASH_MISMATCH")
    return data, base


def validate_candidate(candidate, approved):
    candidate = safe_path(candidate); verify_manifest(candidate)
    metadata = read_json(candidate / "metadata.json")
    require(metadata["baseModelId"] == approved["modelId"] and metadata["approvedBaseSha256"] == approved["baseSha256"], "CANDIDATE_BASE_MISMATCH")
    require(metadata["presetId"] in PROFILES and metadata["steps"] == PROFILES[metadata["presetId"]]["bounds"][0])
    require(metadata["weightsChanged"] is True and math.isfinite(metadata["loss"]) and metadata["loss"] >= 0)
    require(sha(candidate / "adapter_model.safetensors") == metadata["weightsSha256"], "ADAPTER_HASH_MISMATCH")
    require(read_json(candidate / "adapter_config.json")["peft_type"] == "LORA")
    return metadata


class TrainingLock:
    def __enter__(self):
        STATE.mkdir(parents=True, exist_ok=True)
        self.stream = (STATE / "training.lock").open("a+b")
        if self.stream.tell() == 0: self.stream.write(b"0"); self.stream.flush()
        self.stream.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(self.stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            self.stream.close(); raise ValueError("ANOTHER_LOCAL_TRAINER_IS_RUNNING")
        return self

    def __exit__(self, *ignored):
        if os.name == "nt":
            import msvcrt
            self.stream.seek(0); msvcrt.locking(self.stream.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.flock(self.stream.fileno(), fcntl.LOCK_UN)
        self.stream.close()


def child_environment(source):
    allowed = {"SYSTEMROOT", "SYSTEMDRIVE", "WINDIR", "PATH", "PATHEXT", "COMSPEC", "TEMP", "TMP", "HOME"}
    environment = {k:v for k,v in source.items() if k.upper() in allowed}
    # PyTorch's Windows cache initialization uses getpass.getuser(); without an
    # identity variable it falls back to the unavailable POSIX pwd module.
    environment["USERNAME"] = "aether-local"
    environment.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1")
    return environment


def ollama_environment(source, home):
    environment = child_environment(source)
    environment["USERPROFILE"] = str(home)
    locations = {k.upper():v for k,v in source.items() if k.upper() in ("LOCALAPPDATA","APPDATA")}
    environment["LOCALAPPDATA"] = locations.get("LOCALAPPDATA",str(Path(home) / "AppData/Local"))
    environment["APPDATA"] = locations.get("APPDATA",str(Path(home) / "AppData/Roaming"))
    environment["OLLAMA_HOST"] = "http://127.0.0.1:11434"
    return environment


def observed_rss(root):
    import psutil
    total = 0
    try:
        processes = [root, *root.children(recursive=True)]
    except psutil.NoSuchProcess:
        return 0
    for process in processes:
        try:
            total += process.memory_info().rss
        except psutil.NoSuchProcess:
            pass
    return total


def stop_owned(process):
    import psutil
    try:
        descendants = psutil.Process(process.pid).children(recursive=True)
    except psutil.NoSuchProcess:
        descendants = []
    for child in reversed(descendants):
        try: child.kill()
        except psutil.NoSuchProcess: pass
    if process.poll() is None: process.kill()
    process.wait()
    psutil.wait_procs(descendants, timeout=5)


def run_training(args):
    require(args.acknowledge_reviewed_data, "REVIEWED_DATA_ACKNOWLEDGEMENT_REQUIRED")
    approved, base = approval(args.base_approval)
    profile = PROFILES[args.preset]
    read_dataset(args.dataset, args.dataset_sha256, profile["bounds"][2])
    output = new_output(args.output, [base, args.dataset, args.base_approval])
    require(1024 <= args.gpu_memory_mib <= 16384 and 1024 <= args.memory_mib <= 131072)
    require(10 <= args.timeout_seconds <= 3600 and 1 <= args.cpu_threads <= 16)
    work = STATE / ("job-" + uuid.uuid4().hex)
    with TrainingLock(), owned_job(args.memory_mib) as job, temporary_work(work):
        output.parent.mkdir(parents=True, exist_ok=True)
        require(work.stat().st_dev == output.parent.stat().st_dev, "OUTPUT_MUST_SHARE_STATE_VOLUME")
        staged = work / "candidate"
        snapshot = work / "dataset.json"; snapshot.write_bytes(safe_path(args.dataset).read_bytes())
        read_dataset(snapshot, args.dataset_sha256, profile["bounds"][2])
        config = {"baseModelId": approved["modelId"], "modelDirectory": str(base), "approvedBaseSha256": approved["baseSha256"],
                  "presetId": args.preset, "quantization": profile["quantization"], "datasetHash": args.dataset_sha256,
                  "datasetPath": str(snapshot), "outputDirectory": str(staged), "cpuThreads": args.cpu_threads,
                  "device": "cuda", "minFreeGpuMiB": args.gpu_memory_mib, "memoryMiB": args.memory_mib}
        write_json(work / "config.json", config)
        environment = child_environment(os.environ)
        started = time.monotonic(); process = None
        try:
            process = subprocess.Popen([sys.executable, "-I", "-B", str(Path(__file__).resolve()), "_train", str(work / "config.json")],
                                       env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            job.attach(process)
            import psutil
            child = psutil.Process(process.pid); peak = 0
            while process.poll() is None:
                if time.monotonic() - started > args.timeout_seconds: raise ValueError("TRAINING_DEADLINE_EXCEEDED")
                try:
                    rss = observed_rss(child); peak = max(peak, rss)
                    require(rss <= args.memory_mib * 1048576, "TRAINING_RAM_LIMIT")
                except psutil.NoSuchProcess: break
                size = observed_disk_bytes(staged)
                require(size <= 256 * 1048576, "CANDIDATE_DISK_LIMIT")
                time.sleep(0.5)
            process.wait()
            if process.returncode != 0 or not (work / "success.json").is_file():
                diagnostic = read_json(work / "failure.json") if (work / "failure.json").is_file() else {"error":"GPU_TRAINING_FAILED"}
                print(json.dumps({"childFailure":diagnostic}),file=sys.stderr)
                failure = diagnostic["error"]
                require(False, failure if re.fullmatch("[A-Za-z0-9_]+", failure) else "GPU_TRAINING_FAILED")
            metadata = read_json(staged / "metadata.json")
            write_json(staged / "local-run.json", {"durationSeconds": time.monotonic()-started, "peakObservedRssMiB": peak/1048576,
                "gpu": read_json(work / "success.json")["gpu"], "cpuThreads": args.cpu_threads})
            write_manifest(staged, {"baseSha256": approved["baseSha256"], "datasetHash": args.dataset_sha256,
                                   "trainerSha256": sha(ROOT / "aether-server/src/main/resources/training/trainer.py")})
            validate_candidate(staged, approved)
            require(not output.exists(), "OUTPUT_ALREADY_EXISTS"); staged.rename(output)
            return {"candidate": str(output), "metadata": metadata, "resources": read_json(output / "local-run.json")}
        finally:
            if process is not None: stop_owned(process)


def training_child(path):
    config = read_json(path); require(config["presetId"] in PROFILES)
    profile = PROFILES[config["presetId"]]
    require(config["quantization"] == profile["quantization"] and config["device"] == "cuda")
    read_dataset(config["datasetPath"], config["datasetHash"], profile["bounds"][2])
    module = core(); runtime = module.runtime(config, laptop=True)
    gpu = runtime[0].cuda.get_device_name(0)
    module.train(config, runtime, profile)
    write_json(Path(path).parent / "success.json", {"gpu": gpu})


def export_model(args):
    approved, base = approval(args.base_approval); candidate = safe_path(args.candidate)
    metadata = validate_candidate(candidate, approved)
    output = new_output(args.output, [base, candidate, args.base_approval]); name = candidate_name(args.name)
    output.mkdir(parents=True); destination = output / "model"
    spec = importlib.util.spec_from_file_location("aether_merge_export", Path(__file__).with_name("merge_export.py"))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    merged = module.merge(base,candidate,destination)
    for filename in ("LICENSE", "LICENSE.txt", "README.md"):
        if (base / filename).is_file(): shutil.copyfile(base / filename, destination / filename)
    (output / "IMPORT.txt").write_text("Import using tools/aether/laptop.py import-runtime with this merged package and its matching base approval.\n"
        "The importer preserves the reviewed source Ollama chat template, stop tokens and license.\n",encoding="utf-8")
    provenance = {"candidateName": name, "baseModelId": approved["modelId"], "baseSha256": approved["baseSha256"],
                  "datasetHash": metadata["datasetHash"], "adapterSha256": metadata["weightsSha256"],
                  "activation": "separate-reviewed-operation", **merged}
    write_json(output / "deployment.json", provenance)
    write_manifest(output, provenance); verify_manifest(output)
    return {"package": str(output), "model": name, "nextStep": "import-runtime with this merged package and matching base approval",
            "activation": "not-performed"}


def export(args):
    require(1024 <= args.memory_mib <= 131072 and 10 <= args.timeout_seconds <= 3600)
    approved, base = approval(args.base_approval)
    validate_candidate(args.candidate, approved)
    new_output(args.output, [base, args.candidate, args.base_approval]); candidate_name(args.name)
    return bounded_child("_export", {"base_approval":str(safe_path(args.base_approval)),"candidate":str(safe_path(args.candidate)),
                         "output":str(safe_path(args.output)),"name":args.name}, args.memory_mib, args.timeout_seconds)


def bounded_child(command, config, memory_mib, timeout_seconds):
    require(command in ("_export", "_import"))
    require(1024 <= memory_mib <= 131072 and 10 <= timeout_seconds <= 3600)
    work = STATE / ("operation-" + uuid.uuid4().hex)
    output = safe_path(config["output"])
    with TrainingLock(), owned_job(memory_mib) as job, temporary_work(work):
        process = None
        output.parent.mkdir(parents=True, exist_ok=True)
        require(work.stat().st_dev == output.parent.stat().st_dev, "OUTPUT_MUST_SHARE_STATE_VOLUME")
        staged = work / "output"; config = {**config,"output":str(staged)}
        write_json(work / "config.json", config)
        started = time.monotonic()
        try:
            native_log = work / "native.log"
            # Import/export have no dataset input. Keep transient native diagnostics
            # bounded and private; expose only stack locations, never raw messages.
            with native_log.open("wb") as diagnostics:
                process = subprocess.Popen([sys.executable,"-I","-u","-B","-X","faulthandler",str(Path(__file__).resolve()),command,str(work / "config.json")],
                                           env=child_environment(os.environ),stdout=subprocess.DEVNULL,stderr=diagnostics)
            job.attach(process)
            import psutil
            child = psutil.Process(process.pid); peak = 0
            while process.poll() is None:
                require(time.monotonic()-started <= timeout_seconds,"LOCAL_OPERATION_DEADLINE_EXCEEDED")
                rss = observed_rss(child); peak = max(peak,rss)
                require(rss <= memory_mib*1048576,"LOCAL_OPERATION_RAM_LIMIT")
                size = observed_disk_bytes(staged)
                require(size <= 64 * 1073741824,"LOCAL_OPERATION_DISK_LIMIT")
                require(native_log.stat().st_size <= 65536,"LOCAL_NATIVE_DIAGNOSTIC_LIMIT")
                time.sleep(0.5)
            process.wait()
            if process.returncode != 0 or not (work / "success.json").is_file():
                diagnostic = read_json(work / "failure.json") if (work / "failure.json").is_file() else {
                    "error":"LOCAL_OPERATION_FAILED","exitCode":process.returncode,"peakObservedRssMiB":peak/1048576,
                    "observedOutputBytes":observed_disk_bytes(staged)}
                require(native_log.stat().st_size <= 65536,"LOCAL_NATIVE_DIAGNOSTIC_LIMIT")
                if "frames" not in diagnostic: diagnostic["frames"] = native_frames(native_log.read_text(encoding="utf-8",errors="replace"))
                print(json.dumps({"childFailure":diagnostic}),file=sys.stderr)
                failure = diagnostic["error"]
                require(False, failure if re.fullmatch("[A-Za-z0-9_]+", failure) else "LOCAL_OPERATION_FAILED")
            result = read_json(work / "success.json")
            require(not output.exists(), "OUTPUT_ALREADY_EXISTS"); staged.rename(output)
            result["directory" if command == "_import" else "package"] = str(output)
            result["resources"] = {"durationSeconds":time.monotonic()-started,"peakObservedRssMiB":peak/1048576}
            return result
        finally:
            if process is not None:stop_owned(process)


def doctor():
    import torch
    result = {"python": sys.version.split()[0], "torch": torch.__version__, "cudaAvailable": torch.cuda.is_available()}
    if torch.cuda.is_available():
        free, total = torch.cuda.mem_get_info()
        result.update(gpu=torch.cuda.get_device_name(0), freeGpuMiB=free//1048576, totalGpuMiB=total//1048576)
        # A real CUDA kernel detects incompatible driver/runtime installations.
        result["cudaKernelPassed"] = float((torch.ones(8,device="cuda")*2).sum().cpu()) == 16.0
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("doctor")
    a = sub.add_parser("approve-base"); a.add_argument("--directory", required=True); a.add_argument("--model-id", required=True); a.add_argument("--output", required=True)
    i = sub.add_parser("import-ollama"); i.add_argument("--store"); i.add_argument("--model", required=True); i.add_argument("--output", required=True)
    i.add_argument("--memory-mib",type=int,default=24576); i.add_argument("--timeout-seconds",type=int,default=1800)
    t = sub.add_parser("train"); t.add_argument("--base-approval", required=True); t.add_argument("--dataset", required=True); t.add_argument("--dataset-sha256", required=True)
    t.add_argument("--preset", choices=PROFILES, required=True); t.add_argument("--output", required=True); t.add_argument("--acknowledge-reviewed-data", action="store_true")
    t.add_argument("--gpu-memory-mib", type=int, default=11000); t.add_argument("--memory-mib", type=int, default=16384); t.add_argument("--cpu-threads", type=int, default=2); t.add_argument("--timeout-seconds", type=int, default=900)
    e = sub.add_parser("export"); e.add_argument("--base-approval", required=True); e.add_argument("--candidate", required=True); e.add_argument("--output", required=True); e.add_argument("--name", required=True)
    e.add_argument("--memory-mib",type=int,default=24576);e.add_argument("--timeout-seconds",type=int,default=1800)
    v = sub.add_parser("verify-package"); v.add_argument("--directory", required=True)
    b = sub.add_parser("bundle"); b.add_argument("--merged-package",required=True); b.add_argument("--model",required=True)
    b.add_argument("--store"); b.add_argument("--output",required=True); b.add_argument("--import-receipt",required=True)
    r = sub.add_parser("import-runtime"); r.add_argument("--merged-package",required=True); r.add_argument("--store")
    r.add_argument("--ollama-executable",required=True); r.add_argument("--receipt",required=True)
    r.add_argument("--base-approval",required=True); r.add_argument("--resume-import-receipt")
    r.add_argument("--timeout-seconds",type=int,default=900)
    hidden = sub.add_parser("_train", help=argparse.SUPPRESS); hidden.add_argument("config")
    hidden_export = sub.add_parser("_export",help=argparse.SUPPRESS);hidden_export.add_argument("config")
    hidden_import = sub.add_parser("_import",help=argparse.SUPPRESS);hidden_import.add_argument("config")
    args = parser.parse_args()
    if args.command == "doctor": result = doctor()
    elif args.command == "approve-base": result = approve(args)
    elif args.command == "import-ollama": result = import_ollama(args)
    elif args.command == "train": result = run_training(args)
    elif args.command == "export": result = export(args)
    elif args.command == "bundle": result = bundle(args)
    elif args.command == "import-runtime": result = import_runtime(args)
    elif args.command == "verify-package": verify_manifest(args.directory); result = {"verified": True}
    elif args.command=="_export":
        settings=read_json(args.config);result=export_model(argparse.Namespace(**settings));write_json(Path(args.config).parent / "success.json",result);return
    elif args.command=="_import":
        # Isolated Python excludes the script directory from sys.path.
        sys.path.insert(0,str(Path(__file__).resolve().parent))
        result=import_child(read_json(args.config));write_json(Path(args.config).parent / "success.json",result);return
    else: training_child(args.config); return
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as failure:
        # Local errors stay categorical: no private prompt, target, service token or library diagnostics.
        category = str(failure) if re.fullmatch("[A-Z0-9_]+", str(failure)) else type(failure).__name__.upper()
        if len(sys.argv) == 3 and sys.argv[1] in ("_train", "_export", "_import"):
            try:
                import traceback
                frames = [{"file":Path(frame.filename).name,"line":frame.lineno,"function":frame.name}
                          for frame in traceback.extract_tb(failure.__traceback__)[-8:]]
                diagnostic = {"error": category,"frames":frames}
                for key in ("errno","winerror"):
                    value = getattr(failure,key,None)
                    if isinstance(value,int): diagnostic[key] = value
                write_json(Path(sys.argv[2]).parent / "failure.json", diagnostic)
            except Exception:
                pass
        print(json.dumps({"error": category}), file=sys.stderr)
        sys.exit(1)
