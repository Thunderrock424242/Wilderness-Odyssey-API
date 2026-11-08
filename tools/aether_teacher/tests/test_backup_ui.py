import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication, QMessageBox, QPushButton

from tools.aether_teacher.controller import Controller
from tools.aether_teacher.store import Store
from tools.aether_teacher.ui.window import create_window


class BackupUiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def wait_for(self, condition):
        deadline = time.monotonic() + 10
        while not condition() and time.monotonic() < deadline:
            QTest.qWait(20)
            time.sleep(.001)
        self.assertTrue(condition())

    def open(self, workspace):
        controller = Controller(workspace, {})
        window = create_window(controller)
        controller.attach(window)
        window.show()
        self.wait_for(lambda: controller.profile and not controller.storage.pending)
        self.addCleanup(window.close)
        self.addCleanup(controller.close)
        return controller, window

    def test_backup_and_import_buttons_round_trip_workspace_on_reopen(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target = root / "source", root / "target"
            archive = root / "my backup.zip"
            with Store(source / "workspace.sqlite3") as store:
                store.save_profile("Help restored players.", "Friendly.", "Ask when unsure.")
                store.save_theme("dark")
            source_controller, source_window = self.open(source)
            create, = [button for button in source_window.advanced_view.findChildren(QPushButton)
                       if button.text() == "Create Backup…"]
            with patch("tools.aether_teacher.workflow.QFileDialog.getSaveFileName", return_value=(str(archive), "")):
                create.click()
            self.wait_for(lambda: archive.is_file() and not source_controller.workspace_transfer)
            source_controller.close()
            source_window.close()

            target_controller, target_window = self.open(target)
            restore, = [button for button in target_window.advanced_view.findChildren(QPushButton)
                        if button.text() == "Import Backup…"]
            with (patch("tools.aether_teacher.workflow.QFileDialog.getOpenFileName", return_value=(str(archive), "")),
                  patch("tools.aether_teacher.workflow.QMessageBox.question", return_value=QMessageBox.StandardButton.Yes),
                  patch("tools.aether_teacher.workflow.QMessageBox.information") as complete):
                restore.click()
                self.wait_for(lambda: complete.called and not target_window.isVisible())
            target_controller.close()
            restored, restored_window = self.open(target)
            self.assertEqual("Help restored players.", restored.profile.job)
            self.assertEqual("dark", restored_window.theme)
            restored.close()
            restored_window.close()
            self.assertEqual(1, len(list(root.glob("target-before-import-*"))))
