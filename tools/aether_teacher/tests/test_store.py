from dataclasses import replace
from pathlib import Path
import tempfile
import unittest
import uuid
from tools.aether_teacher import domain as d
from tools.aether_teacher.store import Store


class StoreTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "workspace.sqlite3"
        self.store = Store(self.path)
        self.addCleanup(self.store.close)

    def accepted(self):
        profile = self.store.save_profile("Guide", "Brief", "Ask when unsure")
        lesson = self.store.save_lesson("Hello?", "Hello.")
        self.store.review_lessons(((lesson.id, lesson.revision),))
        data = d.snapshot(profile, tuple(self.store.lessons()), "laptop-qlora-smoke")
        binding = d.JobBinding(str(uuid.uuid4()), "a" * 64, "b" * 64, "laptop-qlora-smoke",
                               data.dataset_sha256, data.profile_sha256)
        return binding, data, self.store.accept_job(binding, data)

    def test_profile_revision_and_review_are_explicit_and_stale_edits_rejected(self):
        first = self.store.save_profile("Help", "Brief", "Careful")
        second = self.store.save_profile("Guide", "Brief", "Careful", expected_revision=1)
        self.assertEqual(second.revision, 2)
        self.assertEqual(self.store.profile(first.id, 1).job, "Help")
        with self.assertRaises(d.Rejected):
            self.store.save_profile("Stale", "", "", expected_revision=1)
        lesson = self.store.save_lesson("Q", "A")
        self.store.review_lessons(((lesson.id, 1),))
        edited = self.store.save_lesson("Q", "Correct", lesson.id, 1)
        self.assertIsNone(edited.reviewed_revision)
        with self.assertRaises(d.Rejected):
            self.store.review_lessons(((lesson.id, 1),))

    def test_acceptance_survives_restart_replays_and_freezes_bytes(self):
        binding, data, job = self.accepted()
        self.store.save_profile("Changed", "", "", expected_revision=1)
        self.store.close()
        with Store(self.path) as again:
            self.assertEqual(again.accept_job(binding, data).id, job.id)
            self.assertEqual(again.job_snapshot(job.id), data)
            with self.assertRaises(d.Rejected):
                again.accept_job(replace(binding, base_sha256="c" * 64), data)
            with self.assertRaises(d.Rejected):
                again.accept_job(replace(binding, request_id=str(uuid.uuid4())), data)

    def test_cancel_before_claim_never_launches_and_claim_is_once(self):
        _, _, job = self.accepted()
        self.assertEqual(self.store.cancel_job(job.id).state, "cancelled")
        self.assertFalse(self.store.claim_phase(job.id, "prepare"))

    def test_durable_claim_is_not_replayed_after_restart(self):
        _, _, job = self.accepted()
        self.assertTrue(self.store.claim_phase(job.id, "prepare"))
        self.assertFalse(self.store.claim_phase(job.id, "prepare"))
        self.store.close()
        with Store(self.path) as again:
            self.assertFalse(again.claim_phase(job.id, "prepare"))

    def test_batch_review_rolls_back_if_any_revision_is_stale(self):
        a = self.store.save_lesson("Q1", "A1")
        b = self.store.save_lesson("Q2", "A2")
        with self.assertRaises(d.Rejected):
            self.store.review_lessons(((a.id, 1), (b.id, 3)))
        self.assertTrue(all(item.reviewed_revision is None for item in self.store.lessons()))

    def test_transcript_caps_do_not_silently_remove_history(self):
        for _ in range(20):
            self.store.create_session()
        with self.assertRaises(d.Rejected):
            self.store.create_session()
        session = self.store.sessions()[0]
        for i in range(200):
            self.store.append_message(session, "user" if i % 2 == 0 else "assistant", "x")
        with self.assertRaises(d.Rejected):
            self.store.append_message(session, "user", "x")
        self.assertEqual(len(self.store.messages(session)), 200)
        self.assertEqual(self.store.connection.execute("PRAGMA synchronous").fetchone()[0], 2)
        pages = self.store.connection.execute("PRAGMA max_page_count").fetchone()[0]
        size = self.store.connection.execute("PRAGMA page_size").fetchone()[0]
        self.assertLessEqual(pages * size, 256 * 1024 * 1024)

    def test_session_byte_limit_and_database_limit_roll_back_atomically(self):
        session = self.store.create_session()
        # Lower the configured test DB budget, then exercise real SQLite FULL.
        self.store.connection.execute("PRAGMA max_page_count=40")
        with self.assertRaises(d.Rejected):
            for _ in range(200):
                self.store.append_message(session, "assistant", "x" * 16000)
        self.assertLess(len(self.store.messages(session)), 200)

    def test_utf8_transcript_capacity_rejects_without_deleting_messages(self):
        session = self.store.create_session()
        for _ in range(87):
            self.store.append_message(session, "assistant", "界" * 16000)
        with self.assertRaises(d.Rejected):
            self.store.append_message(session, "assistant", "界" * 16000)
        self.assertEqual(len(self.store.messages(session)), 87)

    def test_unc_store_is_rejected(self):
        with self.assertRaises(d.Rejected):
            Store(Path("//server/share/workspace.sqlite3"))


if __name__ == "__main__":
    unittest.main()
