from dataclasses import replace
from pathlib import Path
import tempfile
import unittest
import uuid
from tools.aether_teacher.domain import JobBinding, Rejected, snapshot
from tools.aether_teacher.store import Store


class JobTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.store=Store(Path(self.temp.name)/"workspace.sqlite3");self.addCleanup(self.store.close)
        profile=self.store.save_profile("Guide","Brief","Ask")
        lesson=self.store.save_lesson("Q","A")
        self.store.review_lessons(((lesson.id,1),))
        data=snapshot(profile,tuple(self.store.lessons()),"laptop-qlora-smoke")
        binding=JobBinding(str(uuid.uuid4()),"a"*64,"b"*64,"laptop-qlora-smoke",data.dataset_sha256,data.profile_sha256)
        self.job=self.store.accept_job(binding,data)

    def test_completion_requires_claim_and_cancel_wins_before_publication(self):
        with self.assertRaises(Rejected):
            self.store.complete_phase(self.job.id,"prepare",{"verified":True})
        self.assertTrue(self.store.claim_phase(self.job.id,"prepare"))
        self.store.cancel_job(self.job.id)
        result=self.store.complete_phase(self.job.id,"prepare",{"verified":True})
        self.assertEqual(result.state,"cancelled")
        self.assertFalse(self.store.claim_phase(self.job.id,"train"))

    def test_unverified_phase_cannot_advance_to_ready(self):
        self.store.claim_phase(self.job.id,"prepare")
        with self.assertRaises(Rejected):self.store.complete_phase(self.job.id,"prepare",{})
        result=self.store.complete_phase(self.job.id,"prepare",{"verified":True})
        self.assertEqual(result.phase,"train")
        self.assertEqual(result.state,"running")
        self.assertFalse(self.store.claim_phase(self.job.id,"prepare"))

    def test_recovery_never_replays_a_claimed_optimizer(self):
        from tools.aether_teacher.jobs import recover_records
        self.store.claim_phase(self.job.id,"prepare")
        self.store.complete_phase(self.job.id,"prepare",{"verified":True})
        self.store.claim_phase(self.job.id,"train")
        result=recover_records(self.store,lambda job:None)
        self.assertEqual(result[0].state,"interrupted")
        self.assertFalse(self.store.claim_phase(self.job.id,"train"))
        with self.assertRaisesRegex(Rejected,"NEW_TRAINING_ACTION_REQUIRED"):
            self.store.retry_job(self.job.id)

    def test_published_candidate_is_reconciled_without_optimizer_relaunch(self):
        from tools.aether_teacher.jobs import recover_records
        self.store.claim_phase(self.job.id,"prepare")
        self.store.complete_phase(self.job.id,"prepare",{"verified":True})
        self.store.claim_phase(self.job.id,"train")
        records=recover_records(self.store,lambda job:{"verified":True,"sha256":"c"*64})
        self.assertEqual(records[0].state,"interrupted")
        self.assertEqual(records[0].phase,"export")
        old_attempt=records[0].attempt_id
        retried=self.store.retry_job(self.job.id)
        self.assertNotEqual(retried.attempt_id,old_attempt)
        self.assertTrue(self.store.claim_phase(self.job.id,"export"))

    def test_ambiguous_import_is_quarantined(self):
        from tools.aether_teacher.jobs import recover_records
        for phase in ("prepare","train","export"):
            self.store.claim_phase(self.job.id,phase)
            self.store.complete_phase(self.job.id,phase,{"verified":True})
        self.store.claim_phase(self.job.id,"import")
        result=recover_records(self.store,lambda job:None)
        self.assertEqual(result[0].state,"needs-verification")


if __name__ == "__main__":unittest.main()
