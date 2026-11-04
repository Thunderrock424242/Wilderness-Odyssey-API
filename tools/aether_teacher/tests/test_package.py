import json
from pathlib import Path
import tempfile
import unittest
from tools.aether_teacher.domain import Rejected


class PackageTest(unittest.TestCase):
    def test_package_requires_native_qt_resources_and_rejects_model_or_training_payload(self):
        from tools.aether_teacher.packaging.build import verify_package
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with self.assertRaises(Rejected):verify_package(root)
            for name in ('AetherTeacher.exe','_internal/PySide6/plugins/platforms/qwindows.dll',
                         '_internal/tools/aether_teacher/resources/engine-manifest.json','THIRD_PARTY_NOTICES.txt'):
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('fixture')
            verify_package(root)
            bad=root/'_internal/torch/weights.safetensors';bad.parent.mkdir();bad.write_bytes(b'private')
            with self.assertRaises(Rejected):verify_package(root)


if __name__ == '__main__':unittest.main()
