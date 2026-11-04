from pathlib import Path
import socket
import tempfile
import unittest
from dataclasses import replace
import hashlib
import json
from tools.aether_teacher.domain import RuntimeSettings
from tools.aether_teacher.domain import Rejected


class RuntimeTest(unittest.TestCase):
    def test_managed_runtime_has_private_home_and_never_inherits_remote_host(self):
        from tools.aether_teacher.runtime import runtime_environment
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env = runtime_environment(root)
            self.assertEqual(env["USERPROFILE"], str(root / "home"))
            self.assertEqual(env["OLLAMA_HOST"], "127.0.0.1:11435")
            self.assertEqual(env["OLLAMA_MODELS"], str(root / "models"))

    def test_occupied_runtime_port_is_left_untouched(self):
        from tools.aether_teacher.runtime import reserve_port
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 11435)); listener.listen()
            with self.assertRaisesRegex(Rejected, "RUNTIME_PORT_OCCUPIED"):
                reserve_port()
            with socket.create_connection(("127.0.0.1", 11435), timeout=1): pass

    def test_private_source_copy_preserves_every_blob_and_rejects_tampering(self):
        from tools.aether_teacher.runtime import ManagedRuntime
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"; blobs = source / "blobs"; blobs.mkdir(parents=True)
            layers = []
            for kind, raw in (("model", b"GGUF fixture"), ("license", b"License"), ("template", b"{{ .Prompt }}"), ("params", b'{}')):
                digest = hashlib.sha256(raw).hexdigest()
                (blobs / ("sha256-" + digest)).write_bytes(raw)
                layers.append({"mediaType":"application/vnd.ollama.image." + kind, "digest":"sha256:" + digest, "size":len(raw)})
            config = b'{}'; digest = hashlib.sha256(config).hexdigest()
            (blobs / ("sha256-" + digest)).write_bytes(config)
            manifest = source / "manifests/registry.ollama.ai/library/llama3.1/8b"; manifest.parent.mkdir(parents=True)
            manifest.write_text(json.dumps({"layers":layers, "config":{"digest":"sha256:"+digest,"size":2}}))
            origin = {"ollamaTag":"llama3.1:8b", "sourceGgufSha256":layers[0]["digest"][7:], "licenseSha256":layers[1]["digest"][7:]}
            settings = RuntimeSettings(root, root, root, source, root, root / "private")
            runtime = ManagedRuntime(settings, origin)
            runtime.import_source("llama3.1:8b")
            self.assertEqual((runtime.store / manifest.relative_to(source)).read_bytes(), manifest.read_bytes())
            # Params and config contain identical bytes and share one digest.
            self.assertEqual(len(list((runtime.store / "blobs").iterdir())), 4)
            (blobs / layers[2]["digest"].replace(":", "-")).write_bytes(b"tampered")
            with self.assertRaisesRegex(Rejected, "SOURCE_BLOB_CHANGED"):
                runtime.import_source("llama3.1:8b")


if __name__ == "__main__": unittest.main()
