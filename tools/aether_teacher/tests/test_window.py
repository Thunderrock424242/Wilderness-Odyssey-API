"""The shell must be native and remain safe before setup completes."""
import os
import sys
import unittest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
from PySide6.QtCore import Qt
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication, QPushButton, QMainWindow


class WindowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def test_setup_required_shell_disables_model_actions_and_reaches_advanced(self):
        from tools.aether_teacher.ui.window import create_window
        window = create_window(None)
        self.addCleanup(window.close)
        self.assertIsInstance(window, QMainWindow)
        self.assertEqual(window.pages.currentIndex(), 0)
        send = window.findChild(QPushButton, "sendMessage")
        self.assertFalse(send.isEnabled())
        self.assertIn("setup", send.toolTip().lower())
        window.show()
        QTest.mouseClick(window.navigation.button(4), Qt.MouseButton.LeftButton)
        self.assertEqual(window.pages.currentIndex(), 4)
        self.assertNotIn("torch", sys.modules)
        self.assertNotIn("PySide6.QtWebEngineWidgets", sys.modules)


if __name__ == "__main__":
    unittest.main()
