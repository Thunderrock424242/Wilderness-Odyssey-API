"""Pure policy regressions. Run with the same approved trainer runtime as the smoke."""
import importlib.util
from pathlib import Path
import sys
sys.dont_write_bytecode = True
import tempfile
import unittest

if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
    sys.path.insert(0, str(Path(sys.argv.pop(1)).resolve()))
source = Path(__file__).resolve().parents[2] / "main/resources/training/trainer.py"
spec = importlib.util.spec_from_file_location("aether_trainer_policy", source)
trainer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trainer)


class TrainingPolicyTest(unittest.TestCase):
    def test_cli_resource_admission_fails_without_linux_isolation(self):
        if sys.platform == "linux":
            self.skipTest("Linux cgroups require a host integration test")
        with self.assertRaisesRegex(RuntimeError, "LINUX_RESOURCE_ISOLATION_REQUIRED"):
            trainer.host_limits({})

    def test_snapshot_substitution_fails_before_model_loading(self):
        temporary_root = Path(__file__).resolve().parents[3] / ".codex-build"
        temporary_root.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=temporary_root) as directory:
            dataset = Path(directory) / "dataset.json"
            dataset.write_text("[]")
            c = {"presetId": "lora-smoke", "datasetPath": str(dataset), "datasetHash": "f" * 64}
            class Torch:
                @staticmethod
                def manual_seed(seed):
                    pass
            with self.assertRaisesRegex(RuntimeError, "SNAPSHOT_HASH_MISMATCH"):
                trainer.train(c, (Torch(), None, None, None))

    def test_preset_has_finite_steps_context_and_examples(self):
        self.assertEqual(trainer.PRESETS, {"lora-smoke": (8, 256, 8), "lora-small": (100, 1024, 100)})

    def test_approved_base_digest_detects_replacement_at_same_path(self):
        temporary_root = Path(__file__).resolve().parents[3] / ".codex-build"
        temporary_root.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=temporary_root) as directory:
            base = Path(directory)
            (base / "config.json").write_text("{}")
            weights = base / "model.safetensors"
            weights.write_bytes(b"original test weights")
            approved = trainer.model_digest(base)
            weights.write_bytes(b"replacement weights")
            self.assertNotEqual(approved, trainer.model_digest(base))


if __name__ == "__main__":
    unittest.main()
