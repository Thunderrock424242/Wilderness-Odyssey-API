import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.aether_teacher.domain import Rejected
from tools.aether_teacher.packaging import windows_entry
from tools.aether_teacher.packaging.build_windows import verify_payload
from tools.aether_teacher.setup import read_settings
from tools.aether_teacher.store import Store


class WindowsPackageTest(unittest.TestCase):
    def test_api_handoff_folder_is_saved_in_the_private_workspace(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "Private export"
            target.mkdir()
            database = root / "workspace.sqlite3"
            with Store(database) as store:
                self.assertEqual("", store.export_folder())
                with self.assertRaisesRegex(Rejected, "EXPORT_FOLDER_MISSING"):
                    store.save_export_folder(root / "missing")
                store.save_export_folder(target)
            with Store(database) as store:
                self.assertEqual(str(target.resolve()), store.export_folder())

    def test_rebind_preserves_the_restored_database_and_uses_installed_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = root / "Aether Playground"
            workspace = root / "restored"
            workspace.mkdir()
            database = workspace / "workspace.sqlite3"
            database.write_bytes(b"restored database fixture")
            settings = workspace / "settings.json"
            settings.write_text('{"oldMachine":true}', encoding="utf-8")
            files = {
                "models/base/config.json": "{}",
                "models/toolchain/toolchain.json": "{}",
                "python/python.exe": "",
                "ollama/ollama.exe": "",
                "models/source-store/manifests/registry.ollama.ai/library/qwen3.5/4b": "{}",
                "models/base-identity.json": json.dumps({"schemaVersion": "1.0", "modelId": "qwen3.5:4b",
                                                          "baseSha256": "f" * 64}),
            }
            for name, contents in files.items():
                path = app / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(contents, encoding="utf-8")
            with patch.object(windows_entry, "ROOT", app), patch.object(windows_entry, "verify_engine"):
                windows_entry.configure(workspace)
                self.assertEqual('{"oldMachine":true}', settings.read_text(encoding="utf-8"))
                windows_entry.configure(workspace, replace=True)
            configured = read_settings(workspace)
            self.assertEqual(app / "python/python.exe", configured.python_executable)
            self.assertEqual(app / "models/source-store", configured.source_store)
            approval = json.loads(configured.approval_path.read_bytes())
            self.assertEqual(str(app / "models/base"), approval["modelDirectory"])
            self.assertEqual("f" * 64, approval["baseSha256"])
            self.assertEqual(b"restored database fixture", database.read_bytes())

    def test_payload_rejects_workspace_settings_database_and_candidate_weights(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stock = root / "models/base/model.safetensors"
            stock.parent.mkdir(parents=True)
            stock.touch()
            verify_payload(root)
            for name in ("settings.json", "workspace.sqlite3", "candidate.safetensors", "id_ed25519"):
                with self.subTest(name=name):
                    path = root / name
                    path.touch()
                    with self.assertRaises(Rejected):
                        verify_payload(root)
                    path.unlink()
