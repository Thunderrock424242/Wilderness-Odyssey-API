"""Local operator policy and export regression tests; no model downloads or GPU needed."""
import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys
import os
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("laptop", Path(__file__).with_name("laptop.py"))
laptop = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(laptop)


class LaptopPolicyTest(unittest.TestCase):
    def setUp(self):
        temporary = ROOT / ".codex-build/aether-laptop/tests"
        temporary.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=temporary)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def dataset(self):
        path = self.root / "dataset.json"
        path.write_text(json.dumps([{"id":"reviewed-1","revision":"r1","prompt":"Hello","target":"Welcome"}]), encoding="utf-8")
        return path, hashlib.sha256(path.read_bytes()).hexdigest()

    def test_literal_snapshot_hash_and_strict_members(self):
        path, digest = self.dataset()
        self.assertEqual(laptop.read_dataset(path, digest, 8)[0]["id"], "reviewed-1")
        path.write_bytes(path.read_bytes() + b" ")
        with self.assertRaises(ValueError):
            laptop.read_dataset(path, digest, 8)
        path.write_text('[{"id":"x","revision":"1","prompt":"p","target":"t","extra":true}]')
        with self.assertRaises(ValueError):
            laptop.read_dataset(path, hashlib.sha256(path.read_bytes()).hexdigest(), 8)

    def test_duplicate_examples_and_oversize_utf8_rejected(self):
        path, _ = self.dataset()
        item = json.loads(path.read_text())[0]
        path.write_text(json.dumps([item,item]))
        with self.assertRaises(ValueError):
            laptop.read_dataset(path, hashlib.sha256(path.read_bytes()).hexdigest(), 8)
        path.write_bytes(("界" * 100000).encode("utf-8"))
        with self.assertRaises(ValueError):
            laptop.read_dataset(path, hashlib.sha256(path.read_bytes()).hexdigest(), 8)

    def test_output_must_be_new_and_outside_base_and_candidate(self):
        base = self.root / "base"; base.mkdir()
        with self.assertRaises(ValueError):
            laptop.new_output(base / "nested", [base])
        with self.assertRaises(ValueError):
            laptop.new_output(self.root, [base])
        self.assertEqual(laptop.new_output(self.root / "candidate", [base]), self.root / "candidate")

    def test_manifest_detects_modified_files_and_extra_files(self):
        package = self.root / "package"; package.mkdir()
        (package / "weights").write_bytes(b"verified bytes")
        laptop.write_manifest(package, {"baseHash":"f"*64})
        laptop.verify_manifest(package)
        nested = package / "nested"; nested.mkdir()
        (nested / "package.json").write_text("untracked nested manifest")
        with self.assertRaises(ValueError):
            laptop.verify_manifest(package)
        (nested / "package.json").unlink(); nested.rmdir()
        (package / "extra").write_text("unexpected")
        with self.assertRaises(ValueError):
            laptop.verify_manifest(package)
        (package / "extra").unlink()
        (package / "weights").write_bytes(b"corrupt")
        with self.assertRaises(ValueError):
            laptop.verify_manifest(package)

    def test_model_id_cannot_inject_modelfile_or_select_production_activation(self):
        self.assertEqual(laptop.model_id("aether-candidate:review-1"), "aether-candidate:review-1")
        for value in ["x\nFROM bad", "https://host/model", "../model", "aether-custom:8b"]:
            with self.assertRaises(ValueError):
                laptop.candidate_name(value)

    def test_chat_sample_explicitly_requests_token_lists_and_masks_only_prompt(self):
        class Tokenizer:
            def apply_chat_template(self, messages, **options):
                self_options = options
                if self_options.get("return_dict") is not False:
                    return {"input_ids": [1,2]}
                return [1,2] if len(messages) == 1 else [1,2,3,4]
        prompt, target = laptop.core().chat_sample(Tokenizer(), "prompt", "target")
        self.assertEqual(prompt, [1,2]); self.assertEqual(target, [3,4])

    def test_chat_sample_handles_context_dependent_bpe_at_completion_boundary(self):
        class Tokenizer:
            def apply_chat_template(self,messages,**options):
                if options["tokenize"]:
                    return [1,271] if len(messages) == 1 else [1,198,198,42,99]
                return "header\n\n" if len(messages) == 1 else "header\n\nanswer<end>"
            def encode(self,value,**options):
                self_test.assertEqual(value,"answer<end>")
                self_test.assertFalse(options["add_special_tokens"])
                return [42,99]
        self_test = self
        prompt, target = laptop.core().chat_sample(Tokenizer(),"prompt","answer")
        self.assertEqual(prompt,[1,271]); self.assertEqual(target,[42,99])

    def test_child_environment_preserves_windows_runtime_without_service_secrets(self):
        env = laptop.child_environment({"SYSTEMROOT":"C:/Windows", "PATH":"runtime", "TEMP":"temp",
                                        "AETHER_ADMINISTRATION_TOKEN":"private", "HF_TOKEN":"private"})
        self.assertEqual(env["SYSTEMROOT"], "C:/Windows")
        self.assertEqual(env["HF_HUB_OFFLINE"], "1")
        self.assertTrue(env.get("USERNAME"))
        self.assertNotIn("AETHER_ADMINISTRATION_TOKEN", env); self.assertNotIn("HF_TOKEN", env)

    def test_cli_parser_does_not_require_user_home_in_isolated_child(self):
        result = subprocess.run([sys.executable,"-I","-B",str(Path(__file__).with_name("laptop.py")),"--help"],
                                env=laptop.child_environment(os.environ),capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_memory_accounting_includes_windows_interpreter_children(self):
        from types import SimpleNamespace
        class Process:
            def __init__(self, rss, children=()): self.rss = rss; self.items = children
            def memory_info(self): return SimpleNamespace(rss=self.rss)
            def children(self, recursive=False): return list(self.items)
        child = Process(1000)
        self.assertEqual(laptop.observed_rss(Process(4, [child])), 1004)

    def test_ollama_source_pins_blob_and_license_without_tag_traversal(self):
        store = self.root / "ollama"
        manifest = store / "manifests/registry.ollama.ai/library/llama3.1/8b"
        manifest.parent.mkdir(parents=True)
        blobs = store / "blobs"; blobs.mkdir()
        model = b"GGUF-test"; license_text = b"License for verification"
        entries = []
        for kind, content in [("model", model), ("license", license_text)]:
            digest = hashlib.sha256(content).hexdigest()
            (blobs / ("sha256-" + digest)).write_bytes(content)
            entries.append({"mediaType":"application/vnd.ollama.image." + kind,
                            "digest":"sha256:" + digest,"size":len(content)})
        manifest.write_text(json.dumps({"layers":entries}))
        source = laptop.ollama_source(store, "llama3.1:8b")
        self.assertEqual(source["modelSha256"], hashlib.sha256(model).hexdigest())
        self.assertEqual(source["license"].read_bytes(), license_text)
        with self.assertRaises(ValueError): laptop.ollama_source(store, "../../private:8b")
        source["gguf"].write_bytes(b"changed")
        with self.assertRaises(ValueError): laptop.ollama_source(store, "llama3.1:8b")

    def test_portable_modelfile_preserves_template_and_binds_exact_blob(self):
        blob = self.root / ("sha256-" + "a"*64)
        source = 'FROM ' + str(blob) + '\nTEMPLATE """{{ .Prompt }}"""\nPARAMETER num_ctx 2048\n'
        result = laptop.portable_modelfile(source,blob)
        self.assertEqual(result,'FROM ./model.gguf\nTEMPLATE """{{ .Prompt }}"""\nPARAMETER num_ctx 2048\n')
        with self.assertRaises(ValueError): laptop.portable_modelfile(source.replace(str(blob),"https://example.com/model"),blob)
        with self.assertRaises(ValueError): laptop.portable_modelfile(source+'SYSTEM private production prompt\n',blob)

    def test_import_receipt_rejects_replaced_blob_under_same_candidate_tag(self):
        receipt = {"schemaVersion":"1.1","candidateName":"aether-candidate:review-1",
                   "mergedPackageManifestSha256":"a"*64,"ggufSha256":"b"*64,"quantization":"q4_K_M",
                   "runtimeModelfileSha256":"e"*64,"sourceTemplateSha256":"f"*64}
        laptop.validate_import_receipt(receipt,"aether-candidate:review-1","a"*64,"b"*64,"e"*64)
        with self.assertRaises(ValueError):
            laptop.validate_import_receipt(receipt,"aether-candidate:review-1","a"*64,"c"*64,"e"*64)
        with self.assertRaises(ValueError):
            laptop.validate_import_receipt(receipt,"aether-candidate:review-1","d"*64,"b"*64,"e"*64)
        with self.assertRaises(ValueError):
            laptop.validate_import_receipt(receipt,"aether-candidate:review-1","a"*64,"b"*64,"0"*64)

    def test_runtime_modelfile_preserves_reviewed_ollama_chat_template_and_stop_tokens(self):
        store = self.root / "ollama"; blobs = store / "blobs"; blobs.mkdir(parents=True)
        model = b"GGUF-test"; template = '{{ range .Messages }}{{ .Role }}:{{ .Content }}{{ end }}assistant:'
        layers = []
        for kind,content in [("model",model),("license",b"Model license"),("template",template.encode()),
                             ("params",b'{"stop":["<|eot_id|>"]}')]:
            digest = hashlib.sha256(content).hexdigest(); (blobs / ("sha256-"+digest)).write_bytes(content)
            layers.append({"mediaType":"application/vnd.ollama.image."+kind,"digest":"sha256:"+digest,"size":len(content)})
        manifest = store / "manifests/registry.ollama.ai/library/llama3.1/8b"; manifest.parent.mkdir(parents=True)
        manifest.write_text(json.dumps({"layers":layers}))
        base = self.root / "base"; base.mkdir()
        origin = {"ollamaTag":"llama3.1:8b","sourceGgufSha256":hashlib.sha256(model).hexdigest(),
                  "licenseSha256":hashlib.sha256(b"Model license").hexdigest()}
        (base / "source.json").write_text(json.dumps(origin))
        result,digest = laptop.runtime_modelfile(base,store,self.root / "model")
        self.assertIn('TEMPLATE """'+template+'"""',result)
        self.assertIn('PARAMETER stop "<|eot_id|>"',result)
        self.assertIn('LICENSE """Model license"""',result)
        self.assertEqual(digest,hashlib.sha256(template.encode()).hexdigest())
        origin["licenseSha256"] = "0"*64; (base / "source.json").write_text(json.dumps(origin))
        with self.assertRaisesRegex(ValueError,"SOURCE_OLLAMA_LICENSE_CHANGED"):
            laptop.runtime_modelfile(base,store,self.root / "model")

    def test_disk_watchdog_tolerates_serializer_removing_its_temporary_file(self):
        stable = self.root / "weights"; stable.write_bytes(b"saved")
        vanished = self.root / ".tmp-removed-by-serializer"
        with patch.object(laptop,"files_in",return_value=[stable,vanished]):
            self.assertEqual(laptop.observed_disk_bytes(self.root),5)

    def test_setup_failure_erases_owned_work_snapshot(self):
        work = self.root / "work"
        with self.assertRaises(ValueError):
            with laptop.temporary_work(work):
                (work / "dataset.json").write_text("private reviewed snapshot")
                raise ValueError("setup failed")
        self.assertFalse(work.exists())

    def test_ollama_import_environment_cannot_select_remote_host_or_inherit_tokens(self):
        env = laptop.ollama_environment({"SYSTEMROOT":"C:/Windows","OLLAMA_HOST":"https://remote.example",
            "AETHER_ADMINISTRATION_TOKEN":"private","HF_TOKEN":"private"},self.root)
        self.assertEqual(env["OLLAMA_HOST"],"http://127.0.0.1:11434")
        self.assertEqual(env["USERPROFILE"],str(self.root))
        self.assertEqual(env["LOCALAPPDATA"],str(self.root / "AppData/Local"))
        self.assertEqual(env["APPDATA"],str(self.root / "AppData/Roaming"))
        self.assertNotIn("AETHER_ADMINISTRATION_TOKEN",env); self.assertNotIn("HF_TOKEN",env)

    def test_runtime_import_requires_already_running_local_ollama(self):
        with patch("http.client.HTTPConnection",side_effect=ConnectionRefusedError):
            with self.assertRaisesRegex(ValueError,"LOCAL_OLLAMA_NOT_RUNNING"):
                laptop.require_local_ollama()

    def test_native_crash_diagnostic_exposes_only_stack_locations(self):
        diagnostic = 'secret prompt and token\n  File "C:/private/model/loader.py", line 17 in load\nother private diagnostic'
        self.assertEqual(laptop.native_frames(diagnostic),[{"file":"loader.py","line":17,"function":"load"}])


if __name__ == "__main__":
    unittest.main()
