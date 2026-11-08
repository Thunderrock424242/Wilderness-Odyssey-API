"""One controller for simple and advanced views; storage never blocks Qt."""
from dataclasses import replace
from pathlib import Path
import threading
from PySide6.QtCore import QObject, Signal, Qt
from PySide6.QtWidgets import QMessageBox, QFileDialog
from tools.aether_teacher.chat import ChatClient, profile_patch
from tools.aether_teacher.domain import Rejected, bounded_context, local_path, require
from tools.aether_teacher.store import Store
from tools.aether_teacher.workers import Worker


class Controller(QObject):
    def __init__(self, workspace, manifest):
        super().__init__()
        self.workspace = local_path(workspace)
        self.manifest = manifest
        self.database = self.workspace / "workspace.sqlite3"
        self.storage = Worker(self)
        self.operations = Worker(self)
        self.callbacks = {}
        self.storage.done.connect(self._done, Qt.ConnectionType.QueuedConnection)
        self.operations.done.connect(self._done, Qt.ConnectionType.QueuedConnection)
        self.chat = ChatClient(self)
        self.chat.completed.connect(self._reply)
        self.chat.failed.connect(self._chat_failed)
        self.window = self.profile = self.session = None
        self.settings = self.readiness = self.runtime = self.runner = None
        self.model = "qwen3.5:4b"
        self.turns = []
        self.pending_question = None
        self.proposal_revision = None
        self.store = None
        self.closed = False
        self.stopping = threading.Event()
        self.workflow = None
        self.lease = None
        self.chat_pending = False
        self.workspace_transfer = False

    def _db(self, operation):
        def run():
            if self.store is None:
                from tools.aether_teacher.backup import open_workspace
                self.lease = open_workspace(self.workspace)
                try:self.store = Store(self.database)
                except BaseException:
                    self.lease.close();self.lease=None
                    raise
            return operation(self.store)
        return run

    def submit(self, operation, callback, storage=False, on_error=None):
        try:
            worker = self.storage if storage else self.operations
            key = worker.submit(self._db(operation) if storage else operation)
            self.callbacks[key] = (worker, callback, on_error)
        except Rejected as error:
            if on_error:on_error(str(error))
            self.status(str(error))

    def _done(self, key, value, error):
        entry = self.callbacks.pop(key, None)
        if entry is None:
            return
        worker, callback, on_error = entry
        worker.pending -= 1
        if self.closed or (self.window is not None and getattr(self.window, "exit_requested", False)):
            return
        if error:
            if worker is self.operations and self.runtime is None and self.window:
                self.window.runtime_status.setText("LOCAL WORKSPACE\nSetup needs attention")
            if on_error:on_error(error)
            self.status(error)
            return
        try:
            callback(value)
        except Rejected as failure:
            self.status(str(failure))

    def status(self, message):
        if self.window:
            message={"TRANSCRIPT_LIMIT":"This chat is full · Start a new session, or export/delete this chat",
                     "SESSION_LIMIT":"20 chat sessions are saved · Export/delete a session before creating another",
                     "WORKSPACE_ALREADY_OPEN":"Workspace is already open in another Aether Teacher",
                     "STORAGE_UNAVAILABLE_OR_FULL":"Private storage is unavailable or full · Export/remove saved material"}.get(message,message)
            self.window.statusBar().showMessage(message.replace("_", " "))

    def attach(self, window):
        self.window = window
        from tools.aether_teacher.ui.teaching import TeachingView
        from tools.aether_teacher.ui.profile import ProfileView
        from tools.aether_teacher.ui.lessons import LessonsView
        from tools.aether_teacher.ui.versions import VersionsView
        from tools.aether_teacher.ui.advanced import AdvancedView
        from tools.aether_teacher.workflow import Workflow
        self.workflow = Workflow(self)
        for index, name, view_type in ((0,"teaching_view",TeachingView), (1,"profile_view",ProfileView), (2,"lessons_view",LessonsView),
                                      (3,"versions_view",VersionsView), (4,"advanced_view",AdvancedView)):
            layout = window.pages.widget(index).layout()
            while layout.count() > 2:
                item = layout.takeAt(2)
                if item.widget(): item.widget().deleteLater()
            view = view_type(self if index < 3 else self.workflow)
            setattr(window, name, view)
            layout.addWidget(view, 1)
        window.findChild(__import__("PySide6.QtWidgets", fromlist=["QPushButton"]).QPushButton, "localSetup").clicked.connect(self.setup_dialog)
        window.theme_choice.currentIndexChanged.connect(self.save_theme)
        self.submit(self._boot, self._loaded, storage=True)

    def _boot(self, store):
        profile = store.active_profile() or store.save_profile("Help players.", "Friendly and clear.", "Ask when unsure.")
        sessions = store.sessions()
        session = store.setting("session")
        if session not in sessions:session=sessions[-1] if sessions else store.create_session()
        return profile, session, store.messages(session), store.lessons(), store.theme(), store.export_folder()

    def _loaded(self, data):
        self.profile, self.session, self.turns, lessons, theme, export_folder = data
        self._apply_theme(theme)
        self.window.advanced_view.export_folder.setText(export_folder)
        self.window.profile_view.show_profile(self.profile)
        self.window.teaching_view.show_turns(self.turns)
        self.window.lessons_view.show_lessons(lessons)
        self.refresh_sessions()
        self.status("Local workspace ready · Complete local setup to chat")
        self.workflow.refresh()
        if (self.workspace / "settings.json").is_file():
            from tools.aether_teacher.setup import read_settings
            try:
                self.settings = read_settings(self.workspace)
                self.connect_runtime(self.settings)
            except (OSError, ValueError):
                self.status("Saved setup needs attention · Open Local setup")

    def _apply_theme(self, theme):
        from tools.aether_teacher.ui.themes import apply_theme
        apply_theme(self.window, theme)
        self.window.theme_choice.setEnabled(True)

    def save_theme(self, index):
        previous = self.window.theme
        selected = self.window.theme_choice.currentData()
        self.window.theme_choice.setEnabled(False)
        def saved(theme):
            self._apply_theme(theme)
            self.status("Appearance saved · " + theme.capitalize() + " mode")
        self.submit(lambda store: store.save_theme(selected), saved, storage=True,
                    on_error=lambda error: self._apply_theme(previous))

    def save_profile(self):
        view = self.window.profile_view
        values = (view.job.toPlainText(), view.personality.toPlainText(), view.boundaries.toPlainText(), self.profile.revision)
        self.submit(lambda store: store.save_profile(*values), self._profile_saved, storage=True)

    def _profile_saved(self, profile):
        self.profile = profile
        self.window.profile_view.show_profile(profile)
        self.status(f"Profile revision {profile.revision} saved · Used on the next message")

    def save_lesson(self):
        view = self.window.lessons_view
        previous = view.editing
        values = (view.prompt.toPlainText(), view.target.toPlainText(), previous.id if previous else None, previous.revision if previous else 0)
        def save(store):
            store.save_lesson(*values)
            return store.lessons()
        self.submit(save, lambda rows: (view.new_lesson(), view.show_lessons(rows), self.status("Draft saved · Review explicitly before training")), storage=True)

    def review_lessons(self):
        refs = tuple((v.id, v.revision) for v in self.window.lessons_view.selected())
        def review(store):
            store.review_lessons(refs)
            return store.lessons()
        self.submit(review, self.window.lessons_view.show_lessons, storage=True)

    def delete_lessons(self):
        selected = self.window.lessons_view.selected()
        if selected and QMessageBox.question(self.window, "Delete lessons", "Delete the selected lessons? Accepted training snapshots will remain intact.") == QMessageBox.StandardButton.Yes:
            def remove(store):
                for value in selected: store.delete_lesson(value.id, value.revision)
                return store.lessons()
            self.submit(remove, self.window.lessons_view.show_lessons, storage=True)

    def correct_answer(self):
        index = self.window.teaching_view.history.currentRow()
        if index < 1 or self.turns[index]["role"] != "assistant":
            self.status("Select an Aether answer to correct")
            return
        view = self.window.lessons_view
        view.new_lesson()
        view.prompt.setPlainText(self.turns[index - 1]["content"])
        view.target.setPlainText(self.turns[index]["content"])
        self.window.pages.setCurrentIndex(2)
        self.window.navigation.button(2).setChecked(True)

    def send_message(self):
        try:
            require(self.runtime is not None and self.operations.pending == 0, "LOCAL_SETUP_REQUIRED_OR_BUSY")
            require(not self.chat_pending and self.chat.reply is None,"CHAT_BUSY")
            question = self.window.teaching_view.message.toPlainText()
            session=self.session
            self.chat_pending=True
            self.window.teaching_view.send.setEnabled(False)
            def admit(store):
                store.preflight_turn(session,question)
                return store.active_profile(),store.messages(session)
            def send(result):
                try:
                    require(session==self.session,"SESSION_CHANGED")
                    require(self.operations.pending==0 and not self.stopping.is_set(),"FINISH_CURRENT_OPERATION_FIRST")
                    profile,turns=result
                    context=bounded_context(profile,tuple(turns),question)
                    self.proposal_revision=None
                    self.pending_question=question
                    self.pending_session=session
                    self.chat.send(self.model,profile,context)
                    self.window.teaching_view.context_note.setText(f"{len(context)} messages in context · Older turns stay saved locally")
                    self.status("Aether is replying…")
                except Rejected as error:self._storage_chat_error(str(error));self.status(str(error))
            self.submit(admit,send,storage=True,on_error=self._storage_chat_error)
        except Rejected as error:
            self.status(str(error))

    def propose_profile(self):
        try:
            require(self.runtime is not None and self.operations.pending == 0, "LOCAL_SETUP_REQUIRED_OR_BUSY")
            require(not self.chat_pending and self.chat.reply is None,"CHAT_BUSY")
            prompt = ("Draft job/personality/boundaries from our conversation. Return ONLY JSON with keys "
                      f'profileRevision (the integer {self.profile.revision}), job, personality, boundaries. '
                      "Each text field is at most 1000 UTF-16 units. This is a draft for owner review.")
            context = bounded_context(self.profile, tuple(self.turns), prompt)
            self.chat.send(self.model, self.profile, context, json_format=True)
            self.chat_pending=True
            self.proposal_revision = self.profile.revision
            self.window.teaching_view.send.setEnabled(False)
            self.status("Drafting profile for your review…")
        except Rejected as error:
            self.status(str(error))

    def _reply(self, operation, answer):
        self.window.teaching_view.send.setEnabled(self.runtime is not None and self.proposal_revision is not None)
        if self.proposal_revision is not None:
            self.chat_pending=False
            try:
                require(self.profile.revision == self.proposal_revision, "STALE_PROFILE_PROPOSAL")
                patch = profile_patch(answer, self.profile.revision)
                view = self.window.profile_view
                for key in ("job", "personality", "boundaries"):
                    getattr(view, key).setPlainText(patch[key])
                view.revision.setText(f"Unsaved model draft against revision {self.profile.revision} · Review all fields, then Save profile")
                self.window.pages.setCurrentIndex(1)
                self.window.navigation.button(1).setChecked(True)
                self.status("Profile draft only · Your saved profile has not changed")
            except Rejected as error:
                self.status(str(error))
            self.proposal_revision = None
            return
        question = self.pending_question
        session = self.pending_session
        def save(store):
            store.append_turn(session, question, answer)
            return store.messages(session)
        self.submit(save, self._turn_saved, storage=True,on_error=self._storage_chat_error)

    def _turn_saved(self, turns):
        self.chat_pending=False
        self.turns = turns
        self.window.teaching_view.show_turns(turns)
        self.window.teaching_view.message.clear()
        self.window.teaching_view.send.setEnabled(self.runtime is not None)
        self.status("Reply saved locally · Select an answer to correct it")

    def _chat_failed(self, operation, error):
        self.chat_pending=False
        self.window.teaching_view.send.setEnabled(self.runtime is not None)
        self.status(error)

    def _storage_chat_error(self,error):
        self.chat_pending=False
        self.window.teaching_view.send.setEnabled(self.runtime is not None and self.operations.pending==0)

    def refresh_sessions(self):
        self.submit(lambda store:store.sessions(),lambda sessions:self.window.teaching_view.show_sessions(sessions,self.session),storage=True)

    def _session_changed(self,data):
        self.session,self.turns=data
        self.window.teaching_view.show_turns(self.turns)
        self.refresh_sessions()
        self.status("Chat session ready · Conversations are separate from reviewed lessons")

    def new_session(self):
        if self.chat_pending:self.status("Finish or cancel the current reply first");return
        def create(store):
            session=store.create_session()
            return session,[]
        self.submit(create,self._session_changed,storage=True)

    def select_session(self,index):
        if self.chat_pending:self.status("Finish or cancel the current reply first");self.refresh_sessions();return
        session=self.window.teaching_view.sessions.itemData(index)
        if session and session!=self.session:
            self.submit(lambda store:(session,store.select_session(session)),self._session_changed,storage=True)

    def delete_session(self):
        if self.chat_pending:self.status("Finish or cancel the current reply first");return
        session=self.session
        if QMessageBox.question(self.window,"Delete this chat","Delete this chat's saved messages? Reviewed lessons and accepted training snapshots remain intact.")!=QMessageBox.StandardButton.Yes:return
        def remove(store):
            store.delete_session(session)
            sessions=store.sessions()
            current=store.setting("session") if sessions else store.create_session()
            return current,store.messages(current)
        self.submit(remove,self._session_changed,storage=True)

    def export_session(self):
        destination,_=QFileDialog.getSaveFileName(self.window,"Export private chat",f"aether-chat-{self.session[:8]}.json","JSON (*.json)")
        if destination:
            session=self.session
            self.submit(lambda store:store.export_session(session,Path(destination)),lambda path:self.status("Private chat exported to "+str(path)),storage=True)

    def setup_dialog(self):
        if self.profile is None or self.lease is None:
            self.status("Workspace is unavailable · Close any other Aether Teacher using it")
            return
        from tools.aether_teacher.ui.setup import SetupDialog
        SetupDialog(self).exec()

    def connect_runtime(self, settings):
        if self.lease is None:
            self.status("Workspace ownership is required before local setup")
            return
        if self.operations.pending or self.chat.reply is not None or self.chat_pending:
            self.status("Finish or cancel the current operation first")
            return
        self.status("Verifying installed engine and copying the source into private storage…")
        self.window.runtime_status.setText("LOCAL WORKSPACE\nVerifying local setup…")
        self.window.teaching_view.send.setEnabled(False)
        self.stopping.clear()
        def connect():
            from tools.aether_teacher.setup import SetupProbe, save_settings
            from tools.aether_teacher.engine import EngineRunner, run_owned
            from tools.aether_teacher.runtime import ManagedRuntime
            readiness = SetupProbe(self.manifest).inspect(settings)
            require(not self.stopping.is_set(), "CANCELLED")
            # The manifest-verified engine checks every approved base file offline.
            script = ("import importlib.util,json,sys; "
                      "s=importlib.util.spec_from_file_location('teacher_engine',sys.argv[1]); "
                      "m=importlib.util.module_from_spec(s);s.loader.exec_module(m); "
                      "a,b=m.approval(sys.argv[2]);print(json.dumps(a))")
            checked = run_owned([str(settings.python_executable), "-I", "-B", "-c", script,
                                 str(settings.engine_root / "tools/aether/laptop.py"), str(settings.approval_path)], 1024, 180)
            require(checked == readiness["base"], "BASE_APPROVAL_CHANGED")
            if self.runtime:
                self.runtime.stop()
            runtime = ManagedRuntime(settings, readiness["origin"])
            runtime.cancelled = self.stopping
            try:
                runtime.import_source("qwen3.5:4b")
                runtime.ensure_started()
                save_settings(settings)
                runner = EngineRunner(settings, self.manifest)
                runner.cancelled = self.stopping
                from tools.aether_teacher.jobs import JobCoordinator
                with Store(self.database) as store:
                    store.bind_base(readiness["base"]["baseSha256"])
                    coordinator=JobCoordinator(store, settings, readiness, runner, runtime)
                    coordinator.recover()
                    from tools.aether_teacher.workflow import restore_selection
                    restored_profile,restored_model=restore_selection(store,coordinator)
                return settings, readiness, runtime, runner, restored_profile, restored_model
            except BaseException:
                runtime.stop()
                raise
        self.submit(connect, self._connected)

    def _connected(self, result):
        self.settings, self.readiness, self.runtime, self.runner, restored_profile, self.model = result
        if self.profile is None or restored_profile.revision>=self.profile.revision:
            self.profile=restored_profile
            self.window.profile_view.show_profile(self.profile)
        self.window.teaching_view.send.setEnabled(True)
        self.window.lessons_view.train.setEnabled(self.readiness["trainingDiskReady"])
        self.window.runtime_status.setText("LOCAL WORKSPACE\nLocal model ready")
        self.window.advanced_view.resources.setPlainText(
            f"Verified environment: {self.readiness['versions']}\n"
            f"Engine SHA256: {self.readiness['engineSha256']}\n"
            f"Original base: {self.readiness['base']['modelId']}\n"
            f"Base SHA256: {self.readiness['base']['baseSha256']}\n"
            f"Full-run disk estimate: {self.readiness['estimatedRunBytes'] / 1073741824:.1f} GiB\n"
            f"Disk admitted: {self.readiness['trainingDiskReady']}\n"
            "Managed runtime committed-memory ceiling: 32768 MiB\n"
            "Observed GPU/RAM: unavailable until a measured preparation phase\n"
            "Private runtime: 127.0.0.1:11435\n"
            "Training settings are ceilings, not observed usage.")
        self.workflow.refresh()
        self.status("Local Qwen 3.5 4B ready · Saved profile applies to your next message")

    def train_review(self):
        self.workflow.review()

    def close(self):
        if self.closed: return
        self.closed = True
        self.stopping.set()
        self.chat.cancel()
        if self.runner: self.runner.cancel()
        if self.runtime:
            self.runtime.cancelled.set()
            self.runtime.stop()
        self.operations.close()
        def close_storage():
            if self.store is not None:
                self.store.close();self.store=None
        self.storage.executor.submit(close_storage).result(timeout=30)
        self.storage.close()
        if self.lease:self.lease.close();self.lease=None
