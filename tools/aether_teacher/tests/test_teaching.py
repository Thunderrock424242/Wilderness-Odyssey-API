import os
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
from pathlib import Path
import tempfile
import unittest
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication


class TeachingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.app = QApplication.instance() or QApplication([])

    def wait_for(self, condition):
        for _ in range(100):
            if condition(): return
            QTest.qWait(20)
        self.fail("worker did not complete")

    def test_saved_profile_and_explicit_lesson_review_round_trip_in_native_views(self):
        from tools.aether_teacher.controller import Controller
        from tools.aether_teacher.ui.window import create_window
        from tools.aether_teacher.store import Store
        with tempfile.TemporaryDirectory() as tmp:
            controller = Controller(Path(tmp), manifest={})
            window = create_window(controller)
            controller.attach(window)
            try:
                self.wait_for(lambda: controller.profile is not None)
                window.profile_view.job.setPlainText("Help players find their way")
                window.profile_view.save.click()
                self.wait_for(lambda: controller.profile.revision == 2)
                self.assertEqual(controller.profile.job, "Help players find their way")
                view = window.lessons_view
                view.prompt.setPlainText("Which way?")
                view.target.setPlainText("Check your map.")
                view.save.click()
                self.wait_for(lambda: view.table.rowCount() == 1)
                self.assertEqual(view.table.item(0, 0).text(), "Draft")
                view.table.selectRow(0)
                view.review.click()
                self.wait_for(lambda: view.table.item(0, 0).text() == "Reviewed")
                self.assertFalse(window.findChild(__import__('PySide6.QtWidgets', fromlist=['QPushButton']).QPushButton, "sendMessage").isEnabled())
            finally:
                controller.close()
                window.close()
            with Store(Path(tmp) / "workspace.sqlite3") as store:
                self.assertEqual(store.active_profile().revision, 2)
                self.assertEqual(store.lessons()[0].reviewed_revision, 1)


if __name__ == "__main__": unittest.main()
