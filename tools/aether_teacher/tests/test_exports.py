import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from tools.aether_teacher.domain import Rejected, digest, encode


class ExportTest(unittest.TestCase):
    def test_export_binds_profile_and_inner_files_without_private_content(self):
        from tools.aether_teacher.exports import export_bundle, verify_export
        from tools.aether import laptop
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);inner=root/"inner";inner.mkdir()
            profile=encode({"schemaVersion":"1.0","profileId":"00000000-0000-4000-8000-000000000001","revision":1,"systemPrompt":"Guide"})
            (inner/"model.gguf").write_bytes(b"GGUF fixture")
            (inner/"deployment.json").write_bytes(encode({"profileSha256":digest(profile),"candidateName":"aether-candidate:teacher-test"}))
            laptop.write_manifest(inner,{})
            before={p.name:p.read_bytes() for p in inner.iterdir()}
            out=export_bundle(inner,profile,root/"export",laptop.verify_manifest)
            verify_export(out,laptop.verify_manifest)
            self.assertEqual({p.name:p.read_bytes() for p in inner.iterdir()},before)
            self.assertEqual(set(p.name for p in out.iterdir()),{"model","profile.json","teacher-export.json","README.txt"})
            (out/"profile.json").write_bytes(profile+b" ")
            with self.assertRaises(Rejected):verify_export(out,laptop.verify_manifest)
            with self.assertRaises(Rejected):export_bundle(inner,b'{}',root/"other",laptop.verify_manifest)

    def test_export_rejects_extra_inner_files_or_overlapping_destination(self):
        from tools.aether_teacher.exports import export_bundle
        from tools.aether import laptop
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);inner=root/"inner";inner.mkdir()
            profile=b'{}'
            (inner/"deployment.json").write_bytes(encode({"profileSha256":digest(profile)}))
            laptop.write_manifest(inner,{})
            (inner/"untracked-private-chat.txt").write_text("private")
            with self.assertRaises((Rejected,ValueError)):export_bundle(inner,profile,root/"out",laptop.verify_manifest)
            with self.assertRaises((Rejected,ValueError)):export_bundle(inner,profile,inner/"out",laptop.verify_manifest)


if __name__ == "__main__":unittest.main()
