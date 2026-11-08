"""Technical view of the same bounded workflow used by Lessons."""
from PySide6.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QTabWidget, QLabel, QPlainTextEdit, QPushButton, QFormLayout, QComboBox, QSpinBox, QLineEdit
from tools.aether_teacher.domain import PRESETS, RunLimits


class AdvancedView(QWidget):
    def __init__(self, workflow):
        super().__init__()
        layout = QVBoxLayout(self)
        tabs = QTabWidget()
        layout.addWidget(tabs)
        training = QWidget(); form = QFormLayout(training)
        self.preset = QComboBox(); self.preset.addItems(PRESETS)
        form.addRow("Approved preset", self.preset)
        self.resolved = QLabel(); self.resolved.setWordWrap(True)
        form.addRow("Resolved settings", self.resolved)
        self.preset.currentTextChanged.connect(self.refresh_preset); self.refresh_preset()
        for key, label, minimum, maximum, value in (("ram", "Training committed RAM / MiB",8192,32768,24576),
                ("gpu","GPU allocation budget / MiB",4096,15000,11000),("threads","CPU threads",1,8,2),
                ("timeout","Training deadline / seconds",10,3600,600)):
            field=QSpinBox();field.setRange(minimum,maximum);field.setValue(value)
            setattr(self,key,field);form.addRow(label,field)
        train=QPushButton("Review selected lessons and train…");train.clicked.connect(workflow.review)
        form.addRow(train)
        tabs.addTab(training,"Training setup")
        self.resources=QPlainTextEdit();self.resources.setReadOnly(True)
        self.resources.setPlainText("Observed GPU/RAM: unavailable until measured\nTraining RAM ceiling: 24576 MiB\nManaged runtime committed-memory ceiling: 32768 MiB\nNo optimizer percentage or quality grade is estimated.")
        resource_page=QWidget();resource_layout=QVBoxLayout(resource_page)
        resource_layout.addWidget(self.resources)
        self.observed=QPlainTextEdit();self.observed.setReadOnly(True);resource_layout.addWidget(self.observed)
        refresh=QPushButton("Refresh observed resources");refresh.clicked.connect(workflow.refresh);resource_layout.addWidget(refresh)
        tabs.addTab(resource_page,"Resources")
        diagnostics=QWidget();box=QVBoxLayout(diagnostics)
        self.jobs=QPlainTextEdit();self.jobs.setReadOnly(True);box.addWidget(self.jobs)
        actions=QHBoxLayout()
        cancel=QPushButton("Cancel current job");cancel.clicked.connect(workflow.cancel);actions.addWidget(cancel)
        retry=QPushButton("Retry selected interrupted version");retry.clicked.connect(workflow.retry);actions.addWidget(retry)
        box.addLayout(actions);tabs.addTab(diagnostics,"Jobs & diagnostics")
        self.artifacts=QPlainTextEdit();self.artifacts.setReadOnly(True)
        tabs.addTab(self.artifacts,"Artifacts")
        handoff = QWidget()
        handoff_form = QFormLayout(handoff)
        self.export_folder = QLineEdit()
        self.export_folder.setReadOnly(True)
        self.export_folder.setPlaceholderText("Choose a folder when exporting")
        handoff_form.addRow("Default export folder", self.export_folder)
        browse = QPushButton("Choose export folder…")
        browse.clicked.connect(workflow.choose_export_folder)
        handoff_form.addRow(browse)
        guide = QLabel("Export a verified candidate from Versions. Copy the exported package to private "
                       "storage on your API's inference host, then review and import it there.")
        guide.setWordWrap(True)
        handoff_form.addRow(guide)
        tabs.addTab(handoff, "API handoff")

        backups = QWidget()
        backup_form = QFormLayout(backups)
        note = QLabel("Save your profiles, lessons, chats, and trained candidates to one ZIP. "
                      "Keep the file private. Import replaces this workspace after you reopen Aether.")
        note.setWordWrap(True)
        backup_form.addRow(note)
        create = QPushButton("Create Backup…")
        create.clicked.connect(workflow.create_workspace_backup)
        backup_form.addRow(create)
        restore = QPushButton("Import Backup…")
        restore.clicked.connect(workflow.import_workspace_backup)
        backup_form.addRow(restore)
        tabs.addTab(backups, "Backup & restore")

    def refresh_preset(self):
        steps,context,examples=PRESETS[self.preset.currentText()]
        self.resolved.setText(f"{steps} steps · {context} tokens · up to {examples} examples\nNF4 · rank 4 · alpha 8 · LR 0.0002 · batch 1")

    def limits(self):
        return RunLimits(self.ram.value(),self.gpu.value(),self.threads.value(),self.timeout.value())
