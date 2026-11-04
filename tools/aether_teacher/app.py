"""Aether Teacher desktop entry point."""
import sys


def main(argv: list[str] | None = None) -> int:
    from PySide6.QtWidgets import QApplication
    from tools.aether_teacher.ui.window import create_window
    app = QApplication(sys.argv if argv is None else argv)
    app.setApplicationName("Aether Teacher")
    app.setOrganizationName("WildernessOdyssey")
    window = create_window(None)
    window.show()
    return app.exec()
