"""Corrections become drafts until the owner explicitly reviews them."""
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTableWidget, QTableWidgetItem,
    QPlainTextEdit, QPushButton, QLabel, QAbstractItemView,
)


class LessonsView(QWidget):
    def __init__(self, controller):
        super().__init__()
        self.rows = []
        self.editing = None
        layout = QVBoxLayout(self)
        self.table = QTableWidget(0, 3)
        self.table.setHorizontalHeaderLabels(("Review", "Question", "Revision"))
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.itemDoubleClicked.connect(self.edit_selected)
        layout.addWidget(self.table, 1)
        actions = QHBoxLayout()
        self.review = QPushButton("Review selected lessons")
        self.review.clicked.connect(controller.review_lessons)
        actions.addWidget(self.review)
        self.train = QPushButton("Train new version…")
        self.train.setEnabled(False)
        self.train.setToolTip("Complete local model setup before training.")
        self.train.clicked.connect(controller.train_review)
        actions.addWidget(self.train)
        layout.addLayout(actions)
        layout.addWidget(QLabel("Question"))
        self.prompt = QPlainTextEdit(); self.prompt.setMaximumHeight(80)
        layout.addWidget(self.prompt)
        layout.addWidget(QLabel("The answer you want to teach"))
        self.target = QPlainTextEdit(); self.target.setMaximumHeight(110)
        layout.addWidget(self.target)
        buttons = QHBoxLayout()
        self.save = QPushButton("Save draft lesson")
        self.save.clicked.connect(controller.save_lesson)
        buttons.addWidget(self.save)
        fresh = QPushButton("New lesson")
        fresh.clicked.connect(self.new_lesson)
        buttons.addWidget(fresh)
        remove = QPushButton("Delete selected")
        remove.clicked.connect(controller.delete_lessons)
        buttons.addWidget(remove)
        layout.addLayout(buttons)

    def selected(self):
        return tuple(self.rows[index.row()] for index in self.table.selectionModel().selectedRows())

    def show_lessons(self, rows):
        self.rows = rows
        self.table.setRowCount(len(rows))
        for index, lesson in enumerate(rows):
            for col, value in enumerate(("Reviewed" if lesson.reviewed_revision == lesson.revision else "Draft",
                                         lesson.prompt[:90], str(lesson.revision))):
                self.table.setItem(index, col, QTableWidgetItem(value))
        self.table.resizeColumnsToContents()

    def new_lesson(self):
        self.editing = None
        self.prompt.clear(); self.target.clear()

    def edit_selected(self, item):
        lesson = self.rows[item.row()]
        self.editing = lesson
        self.prompt.setPlainText(lesson.prompt); self.target.setPlainText(lesson.target)
