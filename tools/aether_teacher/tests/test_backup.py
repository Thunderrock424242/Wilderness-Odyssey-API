from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

from tools.aether_teacher.backup import FORMAT, STOCK_BLOB, create_backup, open_workspace, pending_path, queue_import
from tools.aether_teacher.domain import Rejected
from tools.aether_teacher.lease import WorkspaceLease
from tools.aether_teacher.store import Store


class BackupTest(unittest.TestCase):
    def test_backup_and_restore_preserve_data_and_keep_new_machine_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target = root / "source", root / "target"
            archive = root / "backup.zip"
            blobs = source / "runtime/qwen35-models/blobs"
            blobs.mkdir(parents=True)
            (blobs / STOCK_BLOB).write_bytes(b"regenerable stock model")
            candidate = "sha256-" + "a" * 64
            (blobs / candidate).write_bytes(b"candidate")
            (blobs / ("copy-" + "a" * 32)).write_bytes(b"interrupted stock import")
            (source / "settings.json").write_bytes(b"old host")
            with Store(source / "workspace.sqlite3") as store:
                store.save_profile("Aether helps players.", "Kind.", "Ask when unsure.")
                store.save_theme("dark")
                store.save_lesson("Where am I?", "Check your map.")
                create_backup(source, store.connection, archive)
            with ZipFile(archive) as bundle:
                self.assertNotIn("settings.json", bundle.namelist())
                self.assertNotIn(f"runtime/qwen35-models/blobs/{STOCK_BLOB}", bundle.namelist())
            target.mkdir()
            (target / "settings.json").write_bytes(b"new host")
            with Store(target / "workspace.sqlite3") as store:
                store.save_theme("light")
            queue_import(target, archive)
            with WorkspaceLease(target), self.assertRaisesRegex(Rejected, "WORKSPACE_ALREADY_OPEN"):
                open_workspace(target)
            self.assertTrue(pending_path(target).is_dir())
            with open_workspace(target), Store(target / "workspace.sqlite3") as store:
                self.assertEqual("dark", store.theme())
                self.assertEqual("Aether helps players.", store.active_profile().job)
                self.assertEqual(1, len(store.lessons()))
                self.assertEqual(b"new host", (target / "settings.json").read_bytes())
                self.assertEqual(b"candidate", (target / "runtime/qwen35-models/blobs" / candidate).read_bytes())
            recovery, = root.glob("target-before-import-*")
            with Store(recovery / "workspace.sqlite3") as store:
                self.assertEqual("light", store.theme())

    def test_invalid_archives_leave_the_workspace_intact(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            workspace = root / "workspace"
            archive = root / "bad.zip"
            with Store(workspace / "workspace.sqlite3") as store:
                store.save_theme("dark")
            original = (workspace / "workspace.sqlite3").read_bytes()
            for name in ("../outside", "jobs/../outside", "jobs/report.txt:payload", "jobs/AUX.txt", "settings.json"):
                with self.subTest(name=name), ZipFile(archive, "w") as bundle:
                    bundle.writestr("aether-backup.json", FORMAT)
                    bundle.writestr("workspace.sqlite3", original)
                    bundle.writestr(name, b"bad")
                with self.assertRaises(Rejected):
                    queue_import(workspace, archive)
            with Store(workspace / "workspace.sqlite3") as store:
                self.assertEqual("dark", store.theme())
            self.assertEqual([workspace], [path for path in root.iterdir() if path.is_dir()])

    def test_import_rejects_database_job_ids_that_escape_the_workspace(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            workspace, archive = root / "workspace", root / "bad.zip"
            with Store(workspace / "workspace.sqlite3") as store:
                store.connection.execute("INSERT INTO jobs(id,request,binding,dataset,profile,state,phase,attempt) "
                                         "VALUES('../outside','request','','','','queued','train','attempt')")
                store.connection.commit()
                create_backup(workspace, store.connection, archive)
            with self.assertRaisesRegex(Rejected, "INVALID_ID"):
                queue_import(workspace, archive)
            self.assertFalse(pending_path(workspace).exists())

    def test_restart_finishes_verified_import_after_previous_folder_was_moved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            workspace, old = root / "workspace", root / "workspace-before-import-interrupted"
            archive = root / "backup.zip"
            with Store(workspace / "workspace.sqlite3") as store:
                store.save_theme("dark")
                create_backup(workspace, store.connection, archive)
                store.save_theme("light")
            queue_import(workspace, archive)
            workspace.rename(old)
            with open_workspace(workspace), Store(workspace / "workspace.sqlite3") as store:
                self.assertEqual("dark", store.theme())
            self.assertTrue((old / "workspace.sqlite3").is_file())
