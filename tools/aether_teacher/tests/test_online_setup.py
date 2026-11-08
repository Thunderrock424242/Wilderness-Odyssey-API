from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

from tools.aether_teacher.domain import Rejected
from tools.aether_teacher.packaging import online_setup, windows_entry


class OnlineSetupTest(unittest.TestCase):
    def test_downloads_only_allow_pinned_names_hosts_and_sizes(self):
        spec = {"file": "pinned.whl", "url": "https://files.pythonhosted.org/packages/pinned.whl",
                "size": 8, "sha256": "a" * 64}
        online_setup.validate_download(spec, online_setup.WHEEL_HOSTS)
        for change in ({"file": "../pinned.whl"}, {"url": "https://example.com/pinned.whl"}, {"size": -1}):
            with self.assertRaises(Rejected):
                online_setup.validate_download({**spec, **change}, online_setup.WHEEL_HOSTS)

    def test_archive_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "runtime.zip"
            with ZipFile(archive, "w") as bundle:
                bundle.writestr("lib/../../escape.dll", b"bad")
            with self.assertRaises(Rejected):
                online_setup.extract_ollama(archive, root / "ollama")
            self.assertFalse((root / "escape.dll").exists())

    def test_online_launcher_requires_completed_provisioning(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(windows_entry, "ROOT", root):
                self.assertEqual(root, windows_entry.runtime_home())
                (root / "online").mkdir()
                (root / "online/wheels.json").touch()
                with self.assertRaisesRegex(Rejected, "ONLINE_RUNTIME_NOT_READY"):
                    windows_entry.runtime_home()
                (root / "online-runtime").mkdir()
                (root / "online-runtime/ready").write_bytes(online_setup.READY)
                self.assertEqual(root / "online-runtime", windows_entry.runtime_home())
