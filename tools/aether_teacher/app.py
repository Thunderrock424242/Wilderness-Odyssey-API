"""Aether Teacher desktop entry point."""
import sys


def main(argv: list[str] | None = None) -> int:
    from PySide6.QtWidgets import QApplication
    from tools.aether_teacher.ui.window import create_window
    from tools.aether_teacher.controller import Controller
    from tools.aether_teacher.setup import default_workspace, trusted_manifest
    app = QApplication(sys.argv if argv is None else argv)
    app.setApplicationName("Aether Teacher")
    app.setOrganizationName("WildernessOdyssey")
    controller = Controller(default_workspace(), trusted_manifest())
    window = create_window(controller)
    controller.attach(window)
    app.aboutToQuit.connect(controller.close)
    window.show()
    return app.exec()
