"""Single-worker SQLite store. Acceptance/claims commit before side effects.

Connections belong to the creating worker thread. GUI views use controller
signals rather than opening this database on the event loop.
"""
from contextlib import contextmanager
from dataclasses import asdict
import json
from pathlib import Path
import sqlite3
import uuid
import os
from tools.aether_teacher.domain import (
    JobBinding, JobRecord, Lesson, Profile, Rejected, RunLimits, Snapshot,
    compile_profile, encode, local_path, require, text, validate_binding,
)

TERMINAL = ("completed", "failed", "cancelled", "interrupted", "needs-verification")
PHASES = ("prepare", "train", "export", "import", "probe", "bundle")


class Store:
    def __init__(self, path: Path):
        self.path = local_path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        # Recheck after creation; never follow linked owner storage.
        local_path(self.path)
        self.connection = sqlite3.connect(self.path, timeout=5)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA synchronous=FULL")
        self.connection.execute("PRAGMA journal_mode=DELETE")
        page_size = self.connection.execute("PRAGMA page_size").fetchone()[0]
        self.connection.execute(f"PRAGMA max_page_count={256 * 1024 * 1024 // page_size}")
        self.connection.executescript("""
            CREATE TABLE IF NOT EXISTS profiles(id TEXT, revision INTEGER, body BLOB NOT NULL,
                PRIMARY KEY(id,revision));
            CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS lessons(id TEXT PRIMARY KEY,revision INTEGER NOT NULL,
                prompt TEXT NOT NULL,target TEXT NOT NULL,reviewed INTEGER);
            CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY);
            CREATE TABLE IF NOT EXISTS messages(session TEXT,ordinal INTEGER,role TEXT,content TEXT,
                PRIMARY KEY(session,ordinal));
            CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY,request TEXT UNIQUE NOT NULL,binding BLOB NOT NULL,
                dataset BLOB NOT NULL,profile BLOB NOT NULL,state TEXT NOT NULL,phase TEXT NOT NULL,
                attempt TEXT NOT NULL,error TEXT,artifacts TEXT NOT NULL DEFAULT '{}',claimed INTEGER NOT NULL DEFAULT 0);
        """)

    def close(self):
        self.connection.close()

    def __enter__(self):
        return self

    def __exit__(self, *ignored):
        self.close()

    @contextmanager
    def transaction(self):
        try:
            self.connection.execute("BEGIN IMMEDIATE")
            yield
            self.connection.commit()
        except sqlite3.Error:
            self.connection.rollback()
            raise Rejected("STORAGE_UNAVAILABLE_OR_FULL") from None
        except BaseException:
            self.connection.rollback()
            raise

    def setting(self, key, default=None):
        row = self.connection.execute("SELECT value FROM settings WHERE key=?", (key,)).fetchone()
        return row[0] if row else default

    def _setting(self, key, value):
        self.connection.execute("INSERT OR REPLACE INTO settings VALUES(?,?)", (key, value))

    def active_profile(self):
        ref = self.setting("profile")
        if not ref:
            return None
        return self.profile(*json.loads(ref))

    def profile(self, profile_id, revision):
        row = self.connection.execute("SELECT body FROM profiles WHERE id=? AND revision=?", (profile_id, revision)).fetchone()
        require(row is not None, "PROFILE_MISSING")
        return Profile(**json.loads(row[0]))

    def save_profile(self, job, personality, boundaries, expected_revision=0):
        with self.transaction():
            old = self.active_profile()
            require(expected_revision == (old.revision if old else 0), "STALE_PROFILE")
            next_revision = self.connection.execute("SELECT coalesce(max(revision),0)+1 FROM profiles WHERE id=?", (old.id,)).fetchone()[0] if old else 1
            new = Profile(old.id if old else str(uuid.uuid4()), next_revision, job, personality, boundaries)
            compile_profile(new)
            self.connection.execute("INSERT INTO profiles VALUES(?,?,?)", (new.id, new.revision, encode(asdict(new))))
            self._setting("profile", json.dumps([new.id, new.revision]))
        return new

    def lessons(self):
        return [Lesson(row["id"], row["revision"], row["prompt"], row["target"], row["reviewed"])
                for row in self.connection.execute("SELECT * FROM lessons ORDER BY rowid")]

    def save_lesson(self, prompt, target, lesson_id=None, expected_revision=0):
        text(prompt, 4000, 1)
        text(target, 12000, 1)
        with self.transaction():
            lesson_id = lesson_id or str(uuid.uuid4())
            row = self.connection.execute("SELECT revision FROM lessons WHERE id=?", (lesson_id,)).fetchone()
            require(expected_revision == (row[0] if row else 0), "STALE_LESSON")
            new = Lesson(lesson_id, expected_revision + 1, prompt, target)
            self.connection.execute("INSERT OR REPLACE INTO lessons VALUES(?,?,?,?,NULL)",
                                    (new.id, new.revision, prompt, target))
        return new

    def review_lessons(self, refs):
        require(bool(refs), "NO_LESSONS")
        with self.transaction():
            for lesson_id, revision in refs:
                result = self.connection.execute("UPDATE lessons SET reviewed=revision WHERE id=? AND revision=?",
                                                 (lesson_id, revision))
                require(result.rowcount == 1, "STALE_LESSON")

    def delete_lesson(self, lesson_id, revision):
        with self.transaction():
            result = self.connection.execute("DELETE FROM lessons WHERE id=? AND revision=?", (lesson_id, revision))
            require(result.rowcount == 1, "STALE_LESSON")

    def create_session(self):
        with self.transaction():
            require(self.connection.execute("SELECT count(*) FROM sessions").fetchone()[0] < 20, "SESSION_LIMIT")
            session = str(uuid.uuid4())
            self.connection.execute("INSERT INTO sessions VALUES(?)", (session,))
            self._setting("session",session)
        return session

    def sessions(self):
        return [row[0] for row in self.connection.execute("SELECT id FROM sessions ORDER BY rowid")]

    def messages(self, session):
        return [dict(role=row[0], content=row[1]) for row in self.connection.execute(
            "SELECT role,content FROM messages WHERE session=? ORDER BY ordinal", (session,))]

    def select_session(self,session):
        with self.transaction():
            require(self.connection.execute("SELECT 1 FROM sessions WHERE id=?",(session,)).fetchone(),"SESSION_MISSING")
            self._setting("session",session)
        return self.messages(session)

    def delete_session(self,session):
        with self.transaction():
            self.connection.execute("DELETE FROM messages WHERE session=?",(session,))
            self.connection.execute("DELETE FROM sessions WHERE id=?",(session,))
            if self.setting("session")==session:
                sessions=self.sessions()
                self._setting("session",sessions[-1] if sessions else "")

    def preflight_turn(self,session,question):
        text(question,4000,1)
        require(self.connection.execute("SELECT 1 FROM sessions WHERE id=?",(session,)).fetchone(),"SESSION_MISSING")
        count,size=self.connection.execute("SELECT count(*),coalesce(sum(length(CAST(content AS BLOB))),0) FROM messages WHERE session=?",(session,)).fetchone()
        require(count<=198 and size+len(question.encode("utf-8"))+48000<=4*1024*1024,"TRANSCRIPT_LIMIT")
        page_size=self.connection.execute("PRAGMA page_size").fetchone()[0]
        used=self.connection.execute("PRAGMA page_count").fetchone()[0]-self.connection.execute("PRAGMA freelist_count").fetchone()[0]
        limit=self.connection.execute("PRAGMA max_page_count").fetchone()[0]
        require((limit-used)*page_size>=65536,"STORAGE_UNAVAILABLE_OR_FULL")

    def export_session(self,session,destination):
        require(session in self.sessions(),"SESSION_MISSING")
        destination=local_path(destination)
        require(not destination.exists(),"DESTINATION_EXISTS")
        raw=encode({"schemaVersion":"1.0","sessionId":session,"messages":self.messages(session)})
        require(len(raw)<=8*1024*1024,"TRANSCRIPT_LIMIT")
        temporary=destination.with_name(".aether-session-"+uuid.uuid4().hex)
        try:
            with temporary.open("xb") as stream:
                stream.write(raw);stream.flush();os.fsync(stream.fileno())
            require(not destination.exists(),"DESTINATION_EXISTS")
            temporary.rename(destination)
        finally:temporary.unlink(missing_ok=True)
        return destination

    def append_message(self, session, role, content):
        require(role in ("user", "assistant"))
        text(content, 4000 if role == "user" else 16000, 1)
        with self.transaction():
            require(self.connection.execute("SELECT 1 FROM sessions WHERE id=?", (session,)).fetchone(), "SESSION_MISSING")
            count, size = self.connection.execute(
                "SELECT count(*),coalesce(sum(length(CAST(content AS BLOB))),0) FROM messages WHERE session=?", (session,)).fetchone()
            require(count < 200 and size + len(content.encode("utf-8")) <= 4 * 1024 * 1024, "TRANSCRIPT_LIMIT")
            self.connection.execute("INSERT INTO messages VALUES(?,?,?,?)", (session, count, role, content))

    def append_turn(self, session, question, answer):
        text(question, 4000, 1); text(answer, 16000, 1)
        with self.transaction():
            require(self.connection.execute("SELECT 1 FROM sessions WHERE id=?", (session,)).fetchone(), "SESSION_MISSING")
            count, size = self.connection.execute(
                "SELECT count(*),coalesce(sum(length(CAST(content AS BLOB))),0) FROM messages WHERE session=?", (session,)).fetchone()
            require(count <= 198 and size + len(question.encode("utf-8")) + len(answer.encode("utf-8")) <= 4 * 1024 * 1024, "TRANSCRIPT_LIMIT")
            self.connection.executemany("INSERT INTO messages VALUES(?,?,?,?)",
                                       ((session, count, "user", question), (session, count + 1, "assistant", answer)))

    def accept_job(self, binding: JobBinding, data: Snapshot):
        validate_binding(binding, data)
        raw = encode(asdict(binding))
        with self.transaction():
            old = self.connection.execute("SELECT * FROM jobs WHERE request=?", (binding.request_id,)).fetchone()
            if old:
                require(bytes(old["binding"]) == raw and bytes(old["dataset"]) == data.dataset_bytes
                        and bytes(old["profile"]) == data.profile_bytes, "IDEMPOTENCY_CONFLICT")
                return self._job(old)
            active = self.connection.execute("SELECT 1 FROM jobs WHERE state IN ('queued','running','cancel-requested')").fetchone()
            require(not active, "WORKFLOW_BUSY")
            job_id = str(uuid.uuid4())
            self.connection.execute("INSERT INTO jobs(id,request,binding,dataset,profile,state,phase,attempt) VALUES(?,?,?,?,?,'queued','prepare',?)",
                                    (job_id, binding.request_id, raw, data.dataset_bytes, data.profile_bytes, str(uuid.uuid4())))
        return self.get_job(job_id)

    def _job(self, row):
        binding = json.loads(row["binding"])
        binding["limits"] = RunLimits(**binding["limits"])
        return JobRecord(row["id"], JobBinding(**binding), row["state"], row["phase"], row["attempt"], row["error"],
                         tuple(json.loads(row["artifacts"]).items()))

    def get_job(self, job_id):
        row = self.connection.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
        return self._job(row) if row else None

    def jobs(self):
        return [self._job(row) for row in self.connection.execute("SELECT * FROM jobs ORDER BY rowid DESC")]

    def job_snapshot(self, job_id):
        row = self.connection.execute("SELECT dataset,profile FROM jobs WHERE id=?", (job_id,)).fetchone()
        job = self.get_job(job_id)
        require(row is not None, "JOB_MISSING")
        return Snapshot(bytes(row[0]), job.binding.dataset_sha256, bytes(row[1]), job.binding.profile_sha256)

    def claim_phase(self, job_id, expected_phase):
        require(expected_phase in PHASES)
        with self.transaction():
            result = self.connection.execute("UPDATE jobs SET state='running',claimed=1 WHERE id=? AND phase=? AND claimed=0 AND state IN ('queued','running')",
                                             (job_id, expected_phase))
        return result.rowcount == 1

    def cancel_job(self, job_id):
        with self.transaction():
            self.connection.execute("UPDATE jobs SET state=CASE WHEN claimed=0 THEN 'cancelled' ELSE 'cancel-requested' END WHERE id=? AND state IN ('queued','running')", (job_id,))
        return self.get_job(job_id)

    def complete_phase(self, job_id, phase, evidence):
        require(isinstance(evidence, dict) and evidence.get("verified") is True, "PHASE_NOT_VERIFIED")
        with self.transaction():
            row = self.connection.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
            require(row is not None and row["phase"] == phase and row["claimed"] == 1, "PHASE_NOT_CLAIMED")
            require(row["state"] in ("running", "cancel-requested"), "INVALID_JOB_STATE")
            artifacts = json.loads(row["artifacts"]); artifacts[phase] = evidence
            if row["state"] == "cancel-requested":
                state, next_phase = "cancelled", phase
            elif phase == PHASES[-1]:
                state, next_phase = "completed", phase
            else:
                state, next_phase = "running", PHASES[PHASES.index(phase) + 1]
            self.connection.execute("UPDATE jobs SET state=?,phase=?,claimed=0,artifacts=? WHERE id=?",
                                    (state, next_phase, json.dumps(artifacts), job_id))
        return self.get_job(job_id)

    def stop_job(self, job_id, state, error):
        require(state in ("failed", "cancelled", "interrupted", "needs-verification"))
        import re
        require(isinstance(error, str) and re.fullmatch("[A-Z0-9_]{1,96}", error), "INVALID_ERROR")
        with self.transaction():
            self.connection.execute("UPDATE jobs SET state=?,error=? WHERE id=? AND state NOT IN ('completed','cancelled')", (state, error, job_id))
        return self.get_job(job_id)

    def phase_claimed(self, job_id):
        row = self.connection.execute("SELECT claimed FROM jobs WHERE id=?", (job_id,)).fetchone()
        return bool(row and row[0])

    def retry_job(self, job_id):
        with self.transaction():
            row = self.connection.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
            require(row is not None and row["state"] in ("interrupted", "failed"), "RETRY_NOT_AVAILABLE")
            require(not (row["phase"] == "train" and row["claimed"]), "NEW_TRAINING_ACTION_REQUIRED")
            require(not self.connection.execute("SELECT 1 FROM jobs WHERE state IN ('queued','running','cancel-requested')").fetchone(), "WORKFLOW_BUSY")
            self.connection.execute("UPDATE jobs SET state='queued',claimed=0,error=NULL,attempt=? WHERE id=?", (str(uuid.uuid4()), job_id))
        return self.get_job(job_id)

    def selection(self):
        return json.loads(self.setting("selection", '{"revision":0,"version":"original"}'))

    def select_version(self, version, profile_id, profile_revision, expected_selection_revision):
        with self.transaction():
            current = self.selection()
            require(current["revision"] == expected_selection_revision, "STALE_SELECTION")
            self.profile(profile_id, profile_revision)
            if version != "original":
                job = self.get_job(version)
                require(job is not None and job.state == "completed", "VERSION_NOT_VERIFIED")
                accepted = json.loads(self.job_snapshot(version).profile_bytes)
                require(accepted["profileId"] == profile_id and accepted["revision"] == profile_revision, "MODEL_PROFILE_MISMATCH")
            # Candidate rollback restores its recorded training configuration.
            # Later profile edits remain immutable revisions, but must not turn
            # the previous candidate/configuration pair into an invalid pair.
            if current["version"] == "original":
                current["profileId"], current["profileRevision"] = json.loads(self.setting("profile"))
            self._setting("previousSelection", json.dumps(current))
            selection = {"revision":expected_selection_revision+1,"version":version,"profileId":profile_id,"profileRevision":profile_revision}
            self._setting("selection", json.dumps(selection))
            self._setting("profile", json.dumps([profile_id, profile_revision]))
        return selection
