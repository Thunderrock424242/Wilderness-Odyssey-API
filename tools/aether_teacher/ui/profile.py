"""Explicit revision-aware profile editor."""
from PySide6.QtWidgets import QWidget, QVBoxLayout, QLabel, QPlainTextEdit, QPushButton


class ProfileView(QWidget):
    def __init__(self, controller):
        super().__init__()
        layout = QVBoxLayout(self)
        self.revision = QLabel("Loading profile…")
        layout.addWidget(self.revision)
        for key, label in (("job", "What is Aether's job?"), ("personality", "How should Aether speak?"),
                           ("boundaries", "What should Aether be careful about?")):
            layout.addWidget(QLabel(label + " · up to 1,000 characters"))
            field = QPlainTextEdit()
            field.setMaximumHeight(120)
            setattr(self, key, field)
            layout.addWidget(field)
        self.save = QPushButton("Save profile")
        self.save.setEnabled(False)
        self.save.clicked.connect(controller.save_profile)
        layout.addWidget(self.save)
        layout.addWidget(QLabel("Saving changes the instructions used in your next chat. Training is a separate action."))
        layout.addStretch()

    def show_profile(self, profile):
        self.save.setEnabled(True)
        for key in ("job", "personality", "boundaries"):
            getattr(self, key).setPlainText(getattr(profile, key))
        self.revision.setText(f"Saved revision {profile.revision}")
