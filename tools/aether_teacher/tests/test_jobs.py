from dataclasses import replace
from pathlib import Path
import tempfile
import unittest
import uuid
import threading
from types import SimpleNamespace
from tools.aether_teacher.domain import JobBinding, Rejected, snapshot
from tools.aether_teacher.store import Store


class JobTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.store=Store(Path(self.temp.name)/"workspace.sqlite3");self.addCleanup(self.store.close)
        self.approval=Path(self.temp.name)/"approval"
        self.approval.write_text('{"baseSha256":"'+"a"*64+'"}')
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

    def test_gpu_probe_uses_admitted_training_ram_instead_of_metadata_budget(self):
        from tools.aether_teacher.jobs import JobCoordinator
        observed=[]
        class Runner:
            cancelled=threading.Event()
            def run(self,operation,*args,**kwargs):
                if operation=="doctor":
                    observed.append(kwargs["memory_mib"])
                    return {"cudaKernelPassed":True,"freeGpuMiB":15000}
                raise Rejected("TEST_STOP_BEFORE_TRAINING")
        runtime=SimpleNamespace(stop=lambda:None,cancelled=threading.Event())
        settings=SimpleNamespace(workspace_root=Path(self.temp.name),approval_path=Path(self.temp.name)/"approval")
        readiness={"estimatedRunBytes":1,"engineSha256":"b"*64,"base":{"baseSha256":"a"*64}}
        coordinator=JobCoordinator(self.store,settings,readiness,Runner(),runtime)
        coordinator.execute(self.job.id)
        self.assertEqual(observed,[24576])

    def fake_coordinator(self, cancelled=False, changed_binding=False, diagnostic_failure=False):
        from tools.aether_teacher.jobs import JobCoordinator
        calls=[];stopped=[]
        class Runner:
            def __init__(self):self.cancelled=threading.Event()
            def run(self,operation,*args,**kwargs):
                calls.append(operation)
                failure=Rejected("EXPECTED_NATIVE_FAILURE")
                if diagnostic_failure:failure.details={"exitCode":1}
                raise failure
        runner=Runner()
        if cancelled:runner.cancelled.set()
        runtime=SimpleNamespace(stop=lambda:stopped.append(True),cancelled=runner.cancelled)
        settings=SimpleNamespace(workspace_root=Path(self.temp.name),approval_path=self.approval)
        readiness={"estimatedRunBytes":1,"engineSha256":"c"*64 if changed_binding else "b"*64,
                   "base":{"baseSha256":"d"*64 if changed_binding else "a"*64}}
        return JobCoordinator(self.store,settings,readiness,runner,runtime),calls,stopped

    def test_cancel_during_acceptance_is_durable_before_first_claim(self):
        coordinator,calls,_=self.fake_coordinator(cancelled=True)
        result=coordinator.execute(self.job.id)
        self.assertEqual(result.state,"cancelled")
        self.assertFalse(self.store.phase_claimed(self.job.id))
        self.assertEqual(calls,[])

    def test_changed_accepted_engine_or_base_never_launches_work(self):
        coordinator,calls,_=self.fake_coordinator(changed_binding=True)
        result=coordinator.execute(self.job.id)
        self.assertEqual(result.error_code,"ACCEPTED_BINDING_CHANGED")
        self.assertEqual(calls,[])
        self.assertFalse(self.store.phase_claimed(self.job.id))

    def test_approval_file_replacement_cannot_bypass_cached_setup_identity(self):
        coordinator,calls,_=self.fake_coordinator()
        self.approval.write_text('{"baseSha256":"'+"c"*64+'"}')
        result=coordinator.execute(self.job.id)
        self.assertEqual(result.error_code,"ACCEPTED_BINDING_CHANGED")
        self.assertEqual(calls,[])

    def test_optional_diagnostic_write_failure_does_not_skip_cleanup_or_failure_state(self):
        from unittest.mock import patch
        coordinator,calls,stopped=self.fake_coordinator(diagnostic_failure=True)
        original=Path.write_bytes
        def write(path,raw):
            if path.name=="failure.json":raise OSError("disk full")
            return original(path,raw)
        with patch.object(Path,"write_bytes",write):
            result=coordinator.execute(self.job.id)
        self.assertEqual(result.state,"failed")
        self.assertEqual(result.error_code,"EXPECTED_NATIVE_FAILURE")
        self.assertTrue(stopped)


if __name__ == "__main__":unittest.main()
