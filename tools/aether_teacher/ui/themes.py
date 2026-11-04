"""Finite native appearance palettes, independent of model/runtime settings."""
from PySide6.QtCore import QSignalBlocker
from PySide6.QtGui import QPalette, QColor
from tools.aether_teacher.domain import require

PALETTES = {
    "light": dict(background="#f5f5f2", sidebar="#e9ece8", surface="#ffffff", text="#252c30",
                  muted="#6b746f", border="#cbd2cc", selected="#d6e2d7", focus="#567d64", hover="#e4ebe4"),
    "dark": dict(background="#171c20", sidebar="#1e2529", surface="#222b30", text="#edf0ef",
                 muted="#a0aaa6", border="#48575d", selected="#314e42", focus="#9bc0ac", hover="#2c3b36"),
}


def apply_theme(window, theme):
    require(theme in PALETTES, "INVALID_THEME")
    colors = PALETTES[theme]
    palette = QPalette()
    for role, key in ((QPalette.ColorRole.Window, "background"), (QPalette.ColorRole.WindowText, "text"),
                      (QPalette.ColorRole.Base, "surface"), (QPalette.ColorRole.AlternateBase, "sidebar"),
                      (QPalette.ColorRole.Text, "text"), (QPalette.ColorRole.Button, "sidebar"),
                      (QPalette.ColorRole.ButtonText, "text"), (QPalette.ColorRole.Highlight, "selected"),
                      (QPalette.ColorRole.HighlightedText, "text"), (QPalette.ColorRole.ToolTipBase, "surface"),
                      (QPalette.ColorRole.ToolTipText, "text"), (QPalette.ColorRole.PlaceholderText, "muted")):
        palette.setColor(role, QColor(colors[key]))
    for role in (QPalette.ColorRole.Text, QPalette.ColorRole.WindowText, QPalette.ColorRole.ButtonText):
        palette.setColor(QPalette.ColorGroup.Disabled, role, QColor(colors["muted"]))
    window.setPalette(palette)
    window.setStyleSheet("""
        QMainWindow, QWidget { background: %(background)s; color: %(text)s; font-family: 'Segoe UI'; font-size: 14px; }
        QFrame#sidebar, QFrame#sidebar QLabel { background: %(sidebar)s; }
        QLabel#brand { font-size: 23px; font-weight: 600; }
        QLabel#heading { font-size: 26px; font-weight: 600; margin-bottom: 9px; }
        QPushButton { background: %(sidebar)s; padding: 11px 14px; border: 1px solid %(border)s; border-radius: 4px; text-align: left; }
        QPushButton:hover { background: %(hover)s; }
        QPushButton:checked { background: %(selected)s; border-color: %(focus)s; }
        QPushButton:disabled { color: %(muted)s; }
        QPushButton:focus, QComboBox:focus, QPlainTextEdit:focus, QLineEdit:focus { border: 1px solid %(focus)s; }
        QPlainTextEdit, QTextEdit, QLineEdit, QListWidget, QComboBox, QSpinBox, QAbstractItemView {
            background: %(surface)s; color: %(text)s; border: 1px solid %(border)s; border-radius: 3px;
            selection-background-color: %(selected)s; selection-color: %(text)s;
        }
        QComboBox, QLineEdit, QSpinBox { padding: 5px; }
        QTabWidget::pane { border: 1px solid %(border)s; }
        QTabBar::tab { background: %(sidebar)s; color: %(text)s; padding: 8px 12px; border: 1px solid %(border)s; }
        QTabBar::tab:selected { background: %(selected)s; }
        QHeaderView::section { background: %(sidebar)s; color: %(text)s; padding: 5px; border: 1px solid %(border)s; }
        QStatusBar { border-top: 1px solid %(border)s; }
        QToolTip { background: %(surface)s; color: %(text)s; border: 1px solid %(border)s; }
    """ % colors)
    window.theme = theme
    with QSignalBlocker(window.theme_choice):
        window.theme_choice.setCurrentIndex(window.theme_choice.findData(theme))
