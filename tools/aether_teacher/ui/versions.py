"""Explicit candidate selection, paired rollback, comparison and export."""
from PySide6.QtWidgets import QWidget,QVBoxLayout,QHBoxLayout,QListWidget,QPushButton,QPlainTextEdit,QLabel,QComboBox


class VersionsView(QWidget):
    def __init__(self,workflow):
        super().__init__();self.rows=[]
        layout=QVBoxLayout(self)
        self.selection=QLabel("App selection: original Qwen 3.5 4B")
        layout.addWidget(self.selection)
        self.list=QListWidget();layout.addWidget(self.list,1)
        buttons=QHBoxLayout()
        for label,action in (("Use selected version",workflow.select),("Roll back selection",workflow.rollback),("Export selected…",workflow.export)):
            button=QPushButton(label);button.clicked.connect(action);buttons.addWidget(button)
        layout.addLayout(buttons)
        layout.addWidget(QLabel("Compare the original base and selected candidate using the same saved profile and questions (one question per line)."))
        self.questions=QPlainTextEdit();self.questions.setMaximumHeight(90);self.questions.setPlainText("What is your job?")
        layout.addWidget(self.questions)
        compare=QPushButton("Compare answers");compare.clicked.connect(workflow.compare);layout.addWidget(compare)
        self.answers=QPlainTextEdit();self.answers.setReadOnly(True);layout.addWidget(self.answers,1)

    def selected(self):
        index=self.list.currentRow()
        return self.rows[index] if 0<=index<len(self.rows) else None

    def show_jobs(self,jobs):
        selected=self.selected()
        self.rows=list(jobs);self.list.clear()
        for job in jobs:
            self.list.addItem(f"{job.id[:8]} · {job.state} · {job.phase} · {job.binding.preset_id}")
        if selected:
            for index,job in enumerate(jobs):
                if selected.id==job.id:self.list.setCurrentRow(index)
