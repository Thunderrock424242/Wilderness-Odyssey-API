from dataclasses import replace
import json
import os
import unittest
from tools.aether_teacher.domain import Profile, Rejected, bounded_context


class ChatTest(unittest.TestCase):
    def test_chat_request_uses_saved_profile_and_bounded_local_generation(self):
        from tools.aether_teacher.chat import chat_payload
        profile = Profile("00000000-0000-4000-8000-000000000001", 1, "Guide", "Brief", "Ask")
        first = chat_payload("llama3.1:8b", profile, bounded_context(profile, (), "Hello"))
        changed = replace(profile, revision=2, job="Teach maps")
        second = chat_payload("llama3.1:8b", changed, bounded_context(changed, (), "Hello"))
        self.assertNotEqual(first["messages"][0], second["messages"][0])
        self.assertFalse(first["stream"])
        self.assertEqual(first["options"]["num_predict"], 1024)
        self.assertNotIn("tools", first)
        with self.assertRaises(Rejected):
            chat_payload("https://remote/model", profile, first["messages"])

    def test_malformed_oversize_or_nonassistant_replies_are_rejected(self):
        from tools.aether_teacher.chat import reply_text
        self.assertEqual(reply_text({"done":True,"message":{"role":"assistant","content":"Real answer"}}), "Real answer")
        for result in ({}, {"done":False,"message":{"role":"assistant","content":"partial"}},
                       {"done":True,"message":{"role":"user","content":"bad"}},
                       {"done":True,"message":{"role":"assistant","content":"x" * 16001}}):
            with self.assertRaises(Rejected): reply_text(result)

    def test_profile_proposals_are_revision_bound_and_never_executable_actions(self):
        from tools.aether_teacher.chat import profile_patch
        proposal = {"profileRevision":2,"job":"Help","personality":"Brief","boundaries":"Ask"}
        self.assertEqual(profile_patch(json.dumps(proposal), 2)["job"], "Help")
        for bad in ({**proposal,"profileRevision":1}, {**proposal,"command":"train"}, {**proposal,"job":"x" * 1001}):
            with self.assertRaises(Rejected): profile_patch(json.dumps(bad), 2)


if __name__ == "__main__": unittest.main()
