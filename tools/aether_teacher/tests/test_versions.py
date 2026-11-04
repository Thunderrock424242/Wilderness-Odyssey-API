from dataclasses import replace
import json
from pathlib import Path
import tempfile
import unittest
import uuid
from tools.aether_teacher.domain import JobBinding,snapshot
from tools.aether_teacher.store import Store


class VersionsTest(unittest.TestCase):
    def test_restart_uses_persisted_candidate_instead_of_silently_using_base(self):
        from tools.aether_teacher.workflow import restore_selection
        with tempfile.TemporaryDirectory() as tmp, Store(Path(tmp)/"workspace.sqlite3") as store:
            profile=store.save_profile("Guide","Brief","Ask")
            lesson=store.save_lesson("Q","A");store.review_lessons(((lesson.id,1),))
            data=snapshot(profile,tuple(store.lessons()),"laptop-qlora-smoke")
            binding=JobBinding(str(uuid.uuid4()),"a"*64,"b"*64,"laptop-qlora-smoke",data.dataset_sha256,data.profile_sha256)
            job=store.accept_job(binding,data)
            for phase in ("prepare","train","export","import","probe","bundle"):
                store.claim_phase(job.id,phase);store.complete_phase(job.id,phase,{"verified":True})
            store.select_version(job.id,profile.id,1,0)
            edited=store.save_profile("Saved edit","Brief","Ask",expected_revision=1)
            validated=[]
            class ArtifactBoundary:
                def model_for_job(self,job):
                    validated.append(job.id)
                    return "aether-candidate:teacher-"+job.id
            restored_profile,model=restore_selection(store,ArtifactBoundary())
            self.assertEqual(model,"aether-candidate:teacher-"+job.id)
            self.assertEqual(validated,[job.id])
            self.assertEqual(restored_profile.job,"Saved edit")
