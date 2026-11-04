import os
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
from pathlib import Path
import tempfile
import unittest
import threading
from types import SimpleNamespace
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication


class TeachingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.app = QApplication.instance() or QApplication([])

    def wait_for(self, condition):
        for _ in range(500):
            if condition(): return
            QTest.qWait(20)
        self.fail("worker did not complete within ten seconds")

    def test_close_while_initial_storage_is_queued_releases_database_and_lease(self):
        from tools.aether_teacher.controller import Controller
        from tools.aether_teacher.ui.window import create_window
        from tools.aether_teacher.store import Store
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            controller=Controller(Path(tmp),manifest={})
            entered=threading.Event();gate=threading.Event()
            def delayed_open(path):
                entered.set();gate.wait(2)
                return Store(path)
            with patch('tools.aether_teacher.controller.Store',delayed_open):
                window=create_window(controller);controller.attach(window)
                self.assertTrue(entered.wait(2))
                timer=threading.Timer(.1,gate.set);timer.start()
                try:
                    controller.close()
                    controller.database.unlink()
                finally:timer.join();window.close()

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

    def test_full_session_refuses_inference_but_keeps_new_session_action_available(self):
        from tools.aether_teacher.controller import Controller
        from tools.aether_teacher.ui.window import create_window
        with tempfile.TemporaryDirectory() as tmp:
            controller=Controller(Path(tmp),manifest={});window=create_window(controller);controller.attach(window)
            try:
                self.wait_for(lambda:controller.profile is not None and controller.storage.pending==0)
                def fill(store):
                    for _ in range(100):store.append_turn(controller.session,"Q","A")
                controller.submit(fill,lambda ignored:None,storage=True)
                self.wait_for(lambda:controller.storage.pending==0)
                controller.runtime=SimpleNamespace(cancelled=threading.Event(),stop=lambda:None)
                window.teaching_view.message.setPlainText("Next")
                controller.send_message()
                self.wait_for(lambda:controller.storage.pending==0 and controller.chat.reply is None)
                self.assertIn("session",window.statusBar().currentMessage().lower())
                self.assertTrue(window.teaching_view.new_session.isEnabled())
                old=controller.session
                window.teaching_view.new_session.click()
                self.wait_for(lambda:controller.session!=old)
                self.assertEqual(controller.turns,[])
            finally:controller.close();window.close()


if __name__ == "__main__": unittest.main()
