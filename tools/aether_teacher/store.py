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
            new = Profile(old.id if old else str(uuid.uuid4()), expected_revision + 1, job, personality, boundaries)
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
        return session

    def sessions(self):
        return [row[0] for row in self.connection.execute("SELECT id FROM sessions ORDER BY rowid")]

    def messages(self, session):
        return [dict(role=row[0], content=row[1]) for row in self.connection.execute(
            "SELECT role,content FROM messages WHERE session=? ORDER BY ordinal", (session,))]

    def append_message(self, session, role, content):
        require(role in ("user", "assistant"))
        text(content, 4000 if role == "user" else 16000, 1)
        with self.transaction():
            require(self.connection.execute("SELECT 1 FROM sessions WHERE id=?", (session,)).fetchone(), "SESSION_MISSING")
            count, size = self.connection.execute(
                "SELECT count(*),coalesce(sum(length(CAST(content AS BLOB))),0) FROM messages WHERE session=?", (session,)).fetchone()
            require(count < 200 and size + len(content.encode("utf-8")) <= 4 * 1024 * 1024, "TRANSCRIPT_LIMIT")
            self.connection.execute("INSERT INTO messages VALUES(?,?,?,?)", (session, count, role, content))

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
