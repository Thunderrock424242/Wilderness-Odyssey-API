"""Bounded worker-side execution of the reviewed local engine.

No shell and no user-provided command language. Windows jobs own all spawned
descendants, so GUI termination cannot leave training running independently.
"""
import json
import os
from pathlib import Path
import subprocess
import threading
import time
from tools.aether.local_process import OwnedJob
from tools.aether_teacher.domain import Rejected, require


def private_environment():
    allowed = {"SYSTEMROOT", "SYSTEMDRIVE", "WINDIR", "PATH", "PATHEXT", "COMSPEC", "TEMP", "TMP", "LOCALAPPDATA", "APPDATA"}
    result = {key: value for key, value in os.environ.items() if key.upper() in allowed}
    result.update(USERNAME="aether-local", HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1")
    return result


def run_owned(command, memory_mib, timeout_seconds, cancelled=None, cwd=None):
    """Run a fixed command in a worker, keeping output in bounded memory only."""
    cancelled = cancelled or threading.Event()
    require(not cancelled.is_set(), "CANCELLED")
    buffers = [bytearray(), bytearray()]
    overflow = threading.Event()

    def read(stream, buffer):
        try:
            while chunk := stream.read(4096):
                if len(buffer) + len(chunk) > 65536:
                    overflow.set()
                    break
                buffer.extend(chunk)
        finally:
            stream.close()

    with OwnedJob(memory_mib) as owner:
        process = owner.start(command, cwd=cwd, env=private_environment(), stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        readers = [threading.Thread(target=read, args=(stream, buffer), daemon=True)
                   for stream, buffer in zip((process.stdout, process.stderr), buffers)]
        try:
            for reader in readers:
                reader.start()
            deadline = time.monotonic() + timeout_seconds
            while process.poll() is None:
                require(not cancelled.is_set(), "CANCELLED")
                require(not overflow.is_set(), "ENGINE_RESPONSE_LIMIT")
                require(time.monotonic() <= deadline, "ENGINE_DEADLINE")
                time.sleep(.05)
            for reader in readers:
                reader.join(timeout=2)
            require(not overflow.is_set() and all(not reader.is_alive() for reader in readers), "ENGINE_RESPONSE_LIMIT")
            if process.returncode != 0:
                # Expose only the CLI's categorical error, never library stderr.
                category = "ENGINE_FAILED"
                try:
                    error = json.loads(buffers[1].decode("utf-8"))
                    import re
                    if isinstance(error, dict) and re.fullmatch("[A-Z0-9_]{1,96}", error.get("error", "")):
                        category = error["error"]
                except (ValueError, UnicodeError):
                    pass
                raise Rejected(category)
            try:
                result = json.loads(buffers[0].decode("utf-8"))
            except (ValueError, UnicodeError):
                raise Rejected("INVALID_ENGINE_RESPONSE") from None
            require(isinstance(result, dict), "INVALID_ENGINE_RESPONSE")
            return result
        finally:
            owner.close()
            process.wait(timeout=10)
            for reader in readers:
                if reader.ident:
                    reader.join(timeout=2)
            for stream in (process.stdout, process.stderr):
                if not stream.closed:
                    stream.close()


class EngineRunner:
    """Called exclusively by the serialized workflow worker."""
    def __init__(self, settings, manifest):
        self.settings = settings
        self.manifest = manifest
        self.cancelled = threading.Event()

    def cancel(self, operation_id=None):
        self.cancelled.set()

    def run(self, operation, arguments=(), memory_mib=24576, timeout_seconds=600):
        from tools.aether_teacher.setup import verify_engine
        require(operation in {"doctor", "approve-base", "import-ollama", "train", "export", "import-runtime", "bundle", "verify-package"}, "UNKNOWN_OPERATION")
        verify_engine(self.settings.engine_root, self.manifest)
        command = [str(self.settings.python_executable), "-I", "-B", str(self.settings.engine_root / "tools/aether/laptop.py"),
                   "--state-directory", str(self.settings.workspace_root / "engine-state"), "--runtime-port", "11435", operation, *map(str, arguments)]
        return run_owned(command, memory_mib, timeout_seconds, self.cancelled)
