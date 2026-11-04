from dataclasses import replace
from pathlib import Path
import tempfile
import unittest
from tools.aether_teacher.domain import RuntimeSettings, Rejected


class SetupTest(unittest.TestCase):
    def test_only_pinned_text_only_qwen_base_is_admitted(self):
        from tools.aether_teacher.setup import validate_qwen_origin
        pins={"repository":"Qwen/Qwen3.5-4B","revision":"fixed"}
        approval={"modelId":"qwen3.5:4b"}
        origin={"ollamaTag":"qwen3.5:4b","kind":"official-qwen35-text","textOnly":True,**pins}
        config={"model_type":"qwen3_5_text","hidden_size":2560,"num_hidden_layers":32}
        validate_qwen_origin(approval,origin,config,pins)
        for changed in ({**origin,"ollamaTag":"llama3.1:8b"},{**origin,"revision":"other"},{**origin,"textOnly":False}):
            with self.assertRaisesRegex(Rejected,"APPROVED_QWEN35_REQUIRED"):validate_qwen_origin(approval,changed,config,pins)
        for changed in ({**config,"model_type":"qwen3_5"},{**config,"hidden_size":128},{**config,"auto_map":{"AutoConfig":"remote"}}):
            with self.assertRaisesRegex(Rejected,"QWEN35_4B_TEXT_REQUIRED"):validate_qwen_origin(approval,origin,changed,pins)

    def test_modified_engine_is_rejected_and_no_installation_is_attempted(self):
        from tools.aether_teacher.setup import engine_manifest, verify_engine
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "engine with spaces 界"; root.mkdir()
            expected = {"tools/aether/laptop.py":"a" * 64}
            with self.assertRaises(Rejected): verify_engine(root, expected)
            file = root / "tools/aether/laptop.py"; file.parent.mkdir(parents=True); file.write_text("original")
            from hashlib import sha256
            expected[file.relative_to(root).as_posix()] = sha256(b"original").hexdigest()
            verify_engine(root, expected)
            file.write_text("modified")
            with self.assertRaisesRegex(Rejected, "ENGINE_CHANGED"):
                verify_engine(root, expected)

    def test_disk_admission_sums_required_space_on_shared_volume(self):
        from tools.aether_teacher.setup import admit_disk
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            admit_disk(((root, 1),))
            with self.assertRaisesRegex(Rejected, "DISK_CAPACITY"):
                admit_disk(((root, 2**61),))


if __name__ == "__main__": unittest.main()
