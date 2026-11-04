import os
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
from pathlib import Path
import tempfile
import time
import unittest
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication
from tools.aether_teacher.controller import Controller
from tools.aether_teacher.store import Store
from tools.aether_teacher.ui.window import create_window


class ThemeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.app = QApplication.instance() or QApplication([])

    def wait_for(self, condition):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if condition(): return
            QTest.qWait(20); time.sleep(.001)
        self.fail("Appearance operation did not finish")

    def test_theme_choice_persists_across_native_controller_restarts(self):
        with tempfile.TemporaryDirectory() as tmp:
            workspace = Path(tmp)
            for expected, selected in (("light", "dark"), ("dark", "light"), ("light", None)):
                controller = Controller(workspace, manifest={})
                window = create_window(controller)
                controller.attach(window)
                try:
                    self.assertTrue(hasattr(window, "theme_choice"), "Appearance selector is missing")
                    self.wait_for(lambda: window.theme_choice.isEnabled() and controller.storage.pending == 0)
                    self.assertEqual(window.theme_choice.currentData(), expected)
                    self.assertEqual(window.theme, expected)
                    if selected:
                        window.theme_choice.setCurrentIndex(window.theme_choice.findData(selected))
                        self.wait_for(lambda: window.theme == selected and controller.storage.pending == 0)
                finally:
                    controller.close(); window.close()
                with Store(workspace / "workspace.sqlite3") as store:
                    self.assertEqual(store.theme(), selected or expected)
                    self.assertEqual(store.active_profile().revision, 1)
                    self.assertEqual(store.jobs(), [])


if __name__ == "__main__": unittest.main()
