"""Native shell. All actions are delegated to one controller."""
from PySide6.QtCore import Qt
from PySide6.QtCore import QTimer
from PySide6.QtWidgets import (
    QButtonGroup, QFrame, QHBoxLayout, QLabel, QMainWindow, QPushButton,
    QStackedWidget, QVBoxLayout, QWidget, QMessageBox,
)


class TeacherWindow(QMainWindow):
    def closeEvent(self, event):
        controller = self.controller
        if controller is not None and not controller.closed:
            busy = controller.operations.pending or controller.storage.pending or controller.chat.reply is not None
            if busy:
                if not getattr(self, "exit_requested", False):
                    answer = QMessageBox.question(self, "Work is in progress", "Cancel owned work and exit? Choose No to stay.")
                    if answer != QMessageBox.StandardButton.Yes:
                        event.ignore(); return
                    self.exit_requested = True
                    controller.stopping.set()
                    controller.chat.cancel()
                    controller.workflow.cancel()
                event.ignore()
                QTimer.singleShot(200, self.close)
                return
        event.accept()


def create_window(controller: object) -> QMainWindow:
    window = TeacherWindow()
    window.setWindowTitle("Aether Teacher")
    window.resize(1180, 780)
    window.setMinimumSize(880, 620)
    root = QWidget()
    layout = QHBoxLayout(root)
    layout.setContentsMargins(0, 0, 0, 0)
    layout.setSpacing(0)
    sidebar = QFrame()
    sidebar.setObjectName("sidebar")
    sidebar.setFixedWidth(220)
    navigation_layout = QVBoxLayout(sidebar)
    navigation_layout.setContentsMargins(18, 26, 18, 20)
    title = QLabel("AETHER\nTeacher")
    title.setObjectName("brand")
    navigation_layout.addWidget(title)
    navigation_layout.addSpacing(28)
    window.navigation = QButtonGroup(window)
    window.pages = QStackedWidget()
    descriptions = (
        ("Teach Aether", "Talk, try an answer, and choose what to teach."),
        ("Job & personality", "Save the instructions Aether uses in your next conversation."),
        ("Lessons", "Edit corrections and explicitly review the examples you want to teach."),
        ("Versions", "Compare verified candidates and choose which version you use here."),
        ("Advanced", "Inspect the local runtime, training settings, jobs, and artifacts."),
    )
    for index, (name, detail) in enumerate(descriptions):
        button = QPushButton(name.replace("&", "&&"))
        button.setCheckable(True)
        window.navigation.addButton(button, index)
        navigation_layout.addWidget(button)
        page = QWidget()
        body = QVBoxLayout(page)
        body.setContentsMargins(36, 32, 36, 28)
        heading = QLabel(name)
        heading.setObjectName("heading")
        body.addWidget(heading)
        subtitle = QLabel(detail)
        subtitle.setWordWrap(True)
        body.addWidget(subtitle)
        if index == 0:
            body.addStretch()
            label = QLabel("Connect your local model to begin.\nYour conversations stay on this laptop.")
            label.setAlignment(Qt.AlignmentFlag.AlignCenter)
            body.addWidget(label)
            body.addStretch()
            send = QPushButton("Send message")
            send.setObjectName("sendMessage")
            send.setEnabled(False)
            send.setToolTip("Complete local model setup before sending a message.")
            body.addWidget(send)
        else:
            body.addStretch()
        window.pages.addWidget(page)
    window.navigation.idClicked.connect(window.pages.setCurrentIndex)
    window.navigation.button(0).setChecked(True)
    navigation_layout.addStretch()
    setup = QPushButton("Local setup")
    setup.setObjectName("localSetup")
    navigation_layout.addWidget(setup)
    window.runtime_status=QLabel("LOCAL WORKSPACE\nSetup required")
    navigation_layout.addWidget(window.runtime_status)
    layout.addWidget(sidebar)
    layout.addWidget(window.pages, 1)
    window.setCentralWidget(root)
    window.statusBar().showMessage("Setup required · No model connected")
    window.setStyleSheet("""
        QMainWindow, QWidget { background: #f5f5f2; color: #252c30; font-family: 'Segoe UI'; font-size: 14px; }
        QFrame#sidebar, QFrame#sidebar QLabel { background: #e9ece8; }
        QLabel#brand { font-size: 23px; font-weight: 600; }
        QLabel#heading { font-size: 26px; font-weight: 600; margin-bottom: 9px; }
        QPushButton { padding: 11px 14px; border: 1px solid #cbd2cc; border-radius: 4px; text-align: left; }
        QPushButton:checked { background: #d6e2d7; border-color: #8ca893; }
        QPushButton:disabled { color: #858b87; }
        QStatusBar { border-top: 1px solid #d7ddd7; }
    """)
    window.controller = controller
    return window
