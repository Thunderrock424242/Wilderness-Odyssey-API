"""App-owned private Ollama. Existing services/stores are never taken over."""
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import threading
import time
import uuid
from tools.aether.local_process import OwnedJob
from tools.aether_teacher.domain import Rejected, local_path, require
from tools.aether_teacher.engine import private_environment
from tools.aether_teacher.setup import admit_disk, sha_file


def reserve_port():
    sock = socket.socket()
    try:
        if os.name == "nt":
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        sock.bind(("127.0.0.1", 11435))
        return sock
    except OSError:
        sock.close()
        raise Rejected("RUNTIME_PORT_OCCUPIED") from None


def runtime_environment(root):
    root = local_path(root)
    environment = private_environment()
    environment.update(OLLAMA_HOST="127.0.0.1:11435", OLLAMA_MODELS=str(root / "qwen35-models"),
                       USERPROFILE=str(root / "home"), HOME=str(root / "home"),
                       OLLAMA_NUM_PARALLEL="1", OLLAMA_MAX_LOADED_MODELS="1", OLLAMA_NO_CLOUD="1")
    return environment


def portable_source_manifest(manifest):
    """Ollama can record an absolute import path on its model layer."""
    require(isinstance(manifest, dict) and isinstance(manifest.get("layers"), list), "PRIVATE_MANIFEST_CHANGED")
    layers = []
    for layer in manifest["layers"]:
        require(isinstance(layer, dict), "PRIVATE_MANIFEST_CHANGED")
        if layer.get("mediaType") == "application/vnd.ollama.image.model":
            layer = {key: value for key, value in layer.items() if key != "from"}
        layers.append(layer)
    return {**manifest, "layers": layers}


class ManagedRuntime:
    def __init__(self, settings, origin, memory_mib=32768):
        require(settings.port == 11435)
        require(type(memory_mib) is int and 8192 <= memory_mib <= 32768, "RUNTIME_MEMORY_LIMIT")
        self.settings, self.origin, self.memory_mib = settings, origin, memory_mib
        self.store = local_path(settings.workspace_root / "runtime/qwen35-models")
        self.process = self.owner = None
        self.cancelled = threading.Event()

    def stop(self):
        if self.owner:
            self.owner.close()
            self.owner = None
        if self.process:
            self.process.wait(timeout=10)
            self.process = None

    def ensure_started(self):
        if self.process is not None and self.process.poll() is None:
            return
        self.stop()
        require(not self.cancelled.is_set(), "CANCELLED")
        guard = reserve_port()
        self.store.mkdir(parents=True, exist_ok=True)
        environment = runtime_environment(self.store.parent)
        local_path(self.store.parent / "home").mkdir(parents=True, exist_ok=True)
        self.owner = OwnedJob(self.memory_mib)
        try:
            guard.close()
            self.process = self.owner.start([str(self.settings.ollama_executable), "serve"], env=environment,
                                            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                            creationflags=subprocess.CREATE_NO_WINDOW)
            deadline = time.monotonic() + 30
            while self.process.poll() is None and time.monotonic() < deadline:
                require(not self.cancelled.is_set(), "CANCELLED")
                try:
                    from tools.aether_teacher.chat import request_json
                    response = request_json("/api/version", None, timeout=1)
                    if isinstance(response.get("version"), str):
                        # The ready listener must belong to our service tree.
                        import psutil
                        owned = {self.process.pid, *(p.pid for p in psutil.Process(self.process.pid).children(recursive=True))}
                        listeners = [c.pid for c in psutil.net_connections(kind="tcp") if c.status == "LISTEN" and c.laddr.port == 11435]
                        require(bool(listeners) and all(pid in owned for pid in listeners), "RUNTIME_OWNERSHIP_FAILED")
                        return
                except (OSError, Rejected):
                    pass
                time.sleep(.1)
            raise Rejected("RUNTIME_START_FAILED")
        except BaseException:
            self.stop()
            raise

    def import_source(self, source_tag):
        import re
        require(self.process is None, "STOP_RUNTIME_BEFORE_COPY")
        require(source_tag == self.origin["ollamaTag"] == "qwen3.5:4b", "SOURCE_TAG_MISMATCH")
        name, tag = source_tag.split(":")
        relative = Path("manifests/registry.ollama.ai/library") / name / tag
        source_manifest = local_path(self.settings.source_store / relative)
        require(source_manifest.stat().st_size <= 65536, "SOURCE_MANIFEST_LIMIT")
        raw = source_manifest.read_bytes()
        manifest = json.loads(raw)
        layers = manifest.get("layers")
        require(isinstance(layers, list) and 1 <= len(layers) <= 32, "SOURCE_LAYERS_INVALID")
        model = [v for v in layers if v.get("mediaType") == "application/vnd.ollama.image.model"]
        license_layers = [v for v in layers if v.get("mediaType") == "application/vnd.ollama.image.license"]
        require(len(model) == len(license_layers) == 1, "SOURCE_LICENSE_REQUIRED")
        require(model[0]["digest"] == "sha256:" + self.origin["sourceGgufSha256"], "SOURCE_MODEL_CHANGED")
        require(license_layers[0]["digest"] == "sha256:" + self.origin["licenseSha256"], "SOURCE_LICENSE_CHANGED")
        require(any(v.get("mediaType") == "application/vnd.ollama.image.template" for v in layers), "SOURCE_TEMPLATE_REQUIRED")
        inventory = [*layers, manifest["config"]]
        require(sum(item["size"] for item in inventory) <= 64 * 1073741824, "SOURCE_SIZE_LIMIT")
        admit_disk(((self.settings.workspace_root, sum(item["size"] for item in inventory) + 256 * 1048576),))
        blobs = local_path(self.store / "blobs"); blobs.mkdir(parents=True, exist_ok=True)
        for layer in inventory:
            require(re.fullmatch("sha256:[a-f0-9]{64}", layer["digest"]) and type(layer["size"]) is int and layer["size"] >= 0)
            filename = layer["digest"].replace(":", "-")
            source = local_path(self.settings.source_store / "blobs" / filename)
            target = local_path(blobs / filename)
            require(source.stat().st_size == layer["size"] and sha_file(source) == layer["digest"][7:], "SOURCE_BLOB_CHANGED")
            if target.exists():
                require(target.stat().st_size == layer["size"] and sha_file(target) == layer["digest"][7:], "PRIVATE_BLOB_CHANGED")
                continue
            temporary = blobs / ("copy-" + uuid.uuid4().hex)
            try:
                with source.open("rb") as src, temporary.open("xb") as dst:
                    while chunk := src.read(1048576):
                        require(not self.cancelled.is_set(), "CANCELLED")
                        dst.write(chunk)
                    dst.flush(); os.fsync(dst.fileno())
                require(sha_file(temporary) == layer["digest"][7:], "COPY_HASH_MISMATCH")
                temporary.rename(target)
            finally:
                temporary.unlink(missing_ok=True)
        require(source_manifest.read_bytes() == raw, "SOURCE_MANIFEST_CHANGED")
        destination = local_path(self.store / relative)
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            require(destination.stat().st_size <= 65536, "PRIVATE_MANIFEST_CHANGED")
            previous = destination.read_bytes()
            if previous != raw:
                from tools.aether_teacher.chat import strict_json
                require(portable_source_manifest(strict_json(previous)) == portable_source_manifest(manifest),
                        "PRIVATE_MANIFEST_CHANGED")
        else:
            previous = None
        if previous != raw:
            temporary = destination.with_name(f".aether-manifest-{uuid.uuid4().hex}")
            try:
                with temporary.open("xb") as stream:
                    stream.write(raw)
                temporary.replace(destination)
            finally:
                temporary.unlink(missing_ok=True)
        return {"model":source_tag, "modelSha256":self.origin["sourceGgufSha256"]}
