"""Plain-text conversation; model text never becomes an active link or command."""
from PySide6.QtCore import Qt
from PySide6.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QListWidget, QListWidgetItem, QPlainTextEdit, QPushButton, QLabel


class TeachingView(QWidget):
    def __init__(self, controller):
        super().__init__()
        self.turns = []
        layout = QVBoxLayout(self)
        self.context_note = QLabel("Local chat · reviewed corrections only enter training")
        layout.addWidget(self.context_note)
        self.history = QListWidget()
        self.history.setWordWrap(True)
        layout.addWidget(self.history, 1)
        actions = QHBoxLayout()
        correction = QPushButton("Correct selected answer")
        correction.clicked.connect(controller.correct_answer)
        actions.addWidget(correction)
        proposal = QPushButton("Draft job & personality from our chat")
        proposal.clicked.connect(controller.propose_profile)
        actions.addWidget(proposal)
        layout.addLayout(actions)
        self.message = QPlainTextEdit()
        self.message.setPlaceholderText("Talk to Aether, or explain what you want it to do…")
        self.message.setMaximumHeight(110)
        layout.addWidget(self.message)
        buttons = QHBoxLayout()
        self.send = QPushButton("Send message")
        self.send.setObjectName("sendMessage")
        self.send.setEnabled(False)
        self.send.setToolTip("Complete local model setup before sending a message.")
        self.send.clicked.connect(controller.send_message)
        buttons.addWidget(self.send)
        cancel = QPushButton("Cancel reply")
        cancel.clicked.connect(lambda: controller.chat.cancel())
        buttons.addWidget(cancel)
        layout.addLayout(buttons)

    def show_turns(self, turns):
        self.turns = turns
        self.history.clear()
        for turn in turns:
            label = "You" if turn["role"] == "user" else "Aether"
            item = QListWidgetItem(label + "\n" + turn["content"])
            self.history.addItem(item)
        self.history.scrollToBottom()
