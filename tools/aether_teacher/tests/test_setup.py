from dataclasses import replace
from pathlib import Path
import tempfile
import unittest
from tools.aether_teacher.domain import RuntimeSettings, Rejected


class SetupTest(unittest.TestCase):
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
