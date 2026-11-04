"""Explicit registration of the already installed, reviewed laptop engine."""
from pathlib import Path
import os
import shutil
from PySide6.QtWidgets import QDialog, QVBoxLayout, QFormLayout, QLineEdit, QLabel, QPushButton
from tools.aether_teacher.domain import RuntimeSettings, local_path


class SetupDialog(QDialog):
    def __init__(self, controller):
        super().__init__(controller.window)
        self.setWindowTitle("Local model setup")
        self.resize(760, 420)
        layout = QVBoxLayout(self)
        note = QLabel("Connect the installed Llama 3.1 8B and reviewed training environment.\n"
                      "The source model is copied into private app storage (about 5 GiB). No models are downloaded.")
        note.setWordWrap(True)
        layout.addWidget(note)
        current = controller.settings
        root = Path(__file__).resolve().parents[3]
        defaults = {
            "engine_root":root,
            "python_executable":root / ".codex-build/aether-laptop/.venv/Scripts/python.exe",
            "ollama_executable":Path(shutil.which("ollama") or Path(os.environ.get("LOCALAPPDATA", "")) / "Programs/Ollama/ollama.exe"),
            "source_store":Path.home() / ".ollama/models",
            "approval_path":root / ".codex-build/aether-laptop/llama31-ollama-base.json",
        }
        form = QFormLayout()
        self.fields = {}
        for key, label in (("engine_root", "Reviewed engine folder"), ("python_executable", "Training Python"),
                           ("ollama_executable", "Installed Ollama"), ("source_store", "Original Ollama model store"),
                           ("approval_path", "Reviewed base approval")):
            field = QLineEdit(str(getattr(current, key) if current else defaults[key]))
            self.fields[key] = field
            form.addRow(label, field)
        layout.addLayout(form)
        workspace = QLabel("Private workspace: " + str(controller.workspace) + "\nPrivate runtime: 127.0.0.1:11435")
        workspace.setWordWrap(True)
        layout.addWidget(workspace)
        connect = QPushButton("Verify and connect local model")
        def accept():
            values = {key:local_path(Path(field.text())) for key, field in self.fields.items()}
            settings = RuntimeSettings(**values, workspace_root=controller.workspace)
            controller.connect_runtime(settings)
            self.accept()
        connect.clicked.connect(accept)
        layout.addWidget(connect)
