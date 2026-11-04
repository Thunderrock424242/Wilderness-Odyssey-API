import hashlib
import json
import unittest
from dataclasses import replace
from tools.aether_teacher import domain as d


class DomainTest(unittest.TestCase):
    def setUp(self):
        self.profile = d.Profile("00000000-0000-4000-8000-000000000001", 1, "Help", "Brief", "Ask when unsure")

    def test_profile_utf16_boundary_and_invalid_unicode(self):
        self.assertIn("😀" * 500, d.compile_profile(replace(self.profile, job="😀" * 500)))
        for value in ("a" * 1001, "😀" * 501, "\ud800"):
            with self.subTest(length=len(value)), self.assertRaises(d.Rejected):
                d.compile_profile(replace(self.profile, job=value))

    def test_snapshot_binds_exact_reviewed_revision_and_profile_separately(self):
        lesson = d.Lesson("lesson_1", 2, "Hello?", "Hello.", 2)
        result = d.snapshot(self.profile, (lesson,), "laptop-qlora-smoke")
        expected = b'[{"id":"lesson_1","revision":"2","prompt":"Hello?","target":"Hello."}]'
        self.assertEqual(result.dataset_bytes, expected)
        self.assertEqual(result.dataset_sha256, hashlib.sha256(expected).hexdigest())
        changed = d.snapshot(replace(self.profile, revision=2, job="Guide"), (lesson,), "laptop-qlora-smoke")
        self.assertEqual(result.dataset_bytes, changed.dataset_bytes)
        self.assertNotEqual(result.profile_sha256, changed.profile_sha256)
        self.assertEqual(json.loads(result.profile_bytes)["systemPrompt"], d.compile_profile(self.profile))
        for bad in (replace(lesson, revision=3), replace(lesson, reviewed_revision=None)):
            with self.assertRaises(d.Rejected):
                d.snapshot(self.profile, (bad,), "laptop-qlora-smoke")

    def test_snapshot_rejects_empty_duplicate_oversized_or_unsupported_batch(self):
        lesson = d.Lesson("x", 1, "Q", "A", 1)
        for lessons, preset in (((), "laptop-qlora-smoke"), ((lesson, lesson), "laptop-qlora-smoke"),
                                ((lesson,), "arbitrary"), ((replace(lesson, target="x" * 12001),), "laptop-qlora-smoke")):
            with self.assertRaises(d.Rejected):
                d.snapshot(self.profile, lessons, preset)

    def test_context_omits_whole_old_pairs_without_mutating_saved_turns(self):
        turns = tuple({"role": role, "content": str(i)} for i in range(8) for role in ("user", "assistant"))
        context = d.bounded_context(self.profile, turns, "Now")
        self.assertEqual(len(context), 12)
        self.assertEqual(context[1], {"role": "user", "content": "3"})
        self.assertEqual(context[-1], {"role": "user", "content": "Now"})
        self.assertEqual(len(turns), 16)
        huge = ({"role": "user", "content": "a" * 4000}, {"role": "assistant", "content": "b" * 16000})
        self.assertEqual(len(d.bounded_context(self.profile, huge, "Now")), 2)

    def test_private_text_is_not_in_repr(self):
        self.assertNotIn("Ask when unsure", repr(self.profile))


if __name__ == "__main__":
    unittest.main()
