"""Read-only discovery and admission. Setup never installs or downloads models."""
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import shutil
from tools.aether_teacher.domain import RuntimeSettings, digest, encode, local_path, require

ENGINE_FILES = (
    "tools/aether/laptop.py", "tools/aether/local_process.py", "tools/aether/gguf_import.py",
    "tools/aether/merge_export.py", "tools/aether/laptop-requirements.txt",
    "tools/aether/qwen35.py", "tools/aether/qwen35-source.json",
    "aether-server/src/main/resources/training/trainer.py",
)


def sha_file(path):
    result = hashlib.sha256()
    with local_path(path).open("rb") as stream:
        while chunk := stream.read(1048576):
            result.update(chunk)
    return result.hexdigest()


def engine_manifest(root):
    return {name: digest(local_path(Path(root) / name).read_bytes().replace(b"\r\n", b"\n")) for name in ENGINE_FILES}


def verify_engine(root, manifest):
    root = local_path(root)
    require(isinstance(manifest, dict) and bool(manifest), "ENGINE_MANIFEST_REQUIRED")
    for name, expected in manifest.items():
        require(name in ENGINE_FILES, "INVALID_ENGINE_MANIFEST")
        path = local_path(root / name)
        require(root in path.parents and path.is_file(), "ENGINE_MISSING")
        # Git's Windows checkout may translate line endings, without changing code.
        require(digest(path.read_bytes().replace(b"\r\n", b"\n")) == expected, "ENGINE_CHANGED")


def trusted_manifest():
    path = Path(__file__).with_name("resources") / "engine-manifest.json"
    result = json.loads(path.read_bytes())
    require(set(result) == set(ENGINE_FILES), "INVALID_ENGINE_MANIFEST")
    return result


def admit_disk(requirements, floor=0):
    totals = {}
    for path, amount in requirements:
        path = local_path(path)
        while not path.exists():
            path = path.parent
        device = path.stat().st_dev
        current, total = totals.get(device, (path, 0))
        totals[device] = (current, total + amount)
    for path, amount in totals.values():
        require(shutil.disk_usage(path).free >= max(floor, amount), "DISK_CAPACITY")


def default_workspace():
    require(bool(os.environ.get("LOCALAPPDATA")), "LOCALAPPDATA_REQUIRED")
    return local_path(Path(os.environ["LOCALAPPDATA"]) / "WildernessOdyssey/AetherTeacher")


def read_settings(workspace):
    path = local_path(workspace / "settings.json")
    require(path.is_file() and path.stat().st_size <= 16384, "SETUP_REQUIRED")
    data = json.loads(path.read_bytes())
    fields=set(RuntimeSettings.__dataclass_fields__)
    require(set(data) in (fields,fields-{"converter_manifest"}), "INVALID_SETTINGS")
    return RuntimeSettings(**{key: value if key == "port" or value is None else local_path(Path(value)) for key, value in data.items()})


def save_settings(settings):
    require(settings.port == 11435, "PRIVATE_RUNTIME_REQUIRED")
    root = local_path(settings.workspace_root)
    root.mkdir(parents=True, exist_ok=True)
    path = local_path(root / "settings.json")
    temporary = root / "settings.new"
    with temporary.open("wb") as stream:
        stream.write(encode({key: str(value) if isinstance(value, Path) else value for key, value in asdict(settings).items()}))
        stream.flush(); os.fsync(stream.fileno())
    temporary.replace(path)


def validate_qwen_origin(approval, origin, config, pins):
    require(approval.get("modelId")==origin.get("ollamaTag")=="qwen3.5:4b" and origin.get("kind")=="official-qwen35-text"
            and origin.get("textOnly") is True and origin.get("repository")==pins["repository"]
            and origin.get("revision")==pins["revision"],"APPROVED_QWEN35_REQUIRED")
    require(config.get("model_type")=="qwen3_5_text" and config.get("hidden_size")==2560
            and config.get("num_hidden_layers")==32 and not config.get("auto_map"),"QWEN35_4B_TEXT_REQUIRED")


class SetupProbe:
    def __init__(self, manifest):
        self.manifest = manifest

    def inspect(self, settings):
        require(settings.port == 11435, "PRIVATE_RUNTIME_REQUIRED")
        for value in asdict(settings).values():
            if isinstance(value, Path):
                local_path(value)
        verify_engine(settings.engine_root, self.manifest)
        require(settings.python_executable.name.lower() == "python.exe" and settings.python_executable.is_file(), "TRAINING_INTERPRETER_MISSING")
        require(settings.ollama_executable.name.lower() == "ollama.exe" and settings.ollama_executable.is_file(), "OLLAMA_MISSING")
        require(settings.approval_path.is_file() and settings.approval_path.stat().st_size <= 65536, "BASE_APPROVAL_MISSING")
        approval = json.loads(settings.approval_path.read_bytes())
        require(set(approval) == {"schemaVersion", "modelId", "modelDirectory", "baseSha256"} and approval["schemaVersion"] == "1.0", "INVALID_BASE_APPROVAL")
        base = local_path(Path(approval["modelDirectory"]))
        require(base.is_dir(), "APPROVED_BASE_MISSING")
        origin = json.loads((base / "source.json").read_bytes())
        pins=json.loads((settings.engine_root/"tools/aether/qwen35-source.json").read_bytes())
        validate_qwen_origin(approval,origin,json.loads((base/"config.json").read_bytes()),pins)
        require(settings.converter_manifest is not None and sha_file(settings.converter_manifest)==origin.get("toolchainManifestSha256"),"QWEN_CONVERTER_REQUIRED")
        private = local_path(settings.workspace_root / "runtime/qwen35-models")
        require(settings.source_store != private and settings.source_store not in private.parents
                and private not in settings.source_store.parents, "OVERLAPPING_RUNTIME_STORE")
        from tools.aether_teacher.engine import run_owned
        script = "import importlib.metadata as m,json; print(json.dumps({k:m.version(k) for k in ('torch','transformers','peft','bitsandbytes','psutil','gguf')}))"
        versions = run_owned([str(settings.python_executable), "-I", "-c", script], 1024, 30)
        require(versions == {"torch":"2.11.0+cu128", "transformers":"5.16.1", "peft":"0.18.1",
                             "bitsandbytes":"0.50.2", "psutil":"7.2.2", "gguf":"0.19.0"}, "TRAINING_ENVIRONMENT_CHANGED")
        weights = sum(p.stat().st_size for p in base.glob("*.safetensors"))
        require(weights > 0, "APPROVED_BASE_MISSING")
        estimate = max(60 * 1073741824, weights * 3 + 12 * 1073741824)
        # Full-run disk admission is separate from chat/setup readiness.
        disk_path = settings.workspace_root
        while not disk_path.exists():
            disk_path = disk_path.parent
        disk_ready = shutil.disk_usage(disk_path).free >= estimate
        return {"versions":versions, "base":approval, "origin":origin, "engineSha256":digest(encode(self.manifest)),
                "estimatedRunBytes":estimate, "trainingDiskReady":disk_ready}
