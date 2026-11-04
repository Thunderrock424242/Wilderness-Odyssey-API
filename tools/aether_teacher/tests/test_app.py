import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import os
from tools.aether.local_process import OwnedJob


class AppTest(unittest.TestCase):
    def test_native_entry_point_runs_from_other_directory_with_explicit_private_workspace(self):
        # Missing smoke support must fail; it must never leave an unowned GUI.
        root=Path(__file__).resolve().parents[3]
        with tempfile.TemporaryDirectory() as tmp:
            workspace=Path(tmp)/"private workspace 界"
            entry=root/"tools/aether_teacher/launch.py"
            self.assertTrue(entry.is_file(),"Native entry point with isolated smoke support is missing")
            with OwnedJob(2048) as owner:
                environment=dict(os.environ);environment.pop("QT_QPA_PLATFORM",None)
                child=owner.start([sys.executable,"-B",str(entry),"--workspace",str(workspace),"--smoke-check"],
                                  cwd=tmp,env=environment,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                try:
                    child.wait(timeout=30)
                    self.assertEqual(child.returncode,0)
                    result=json.loads((workspace/"native-smoke.json").read_text())
                    self.assertEqual(result["pages"],5)
                    self.assertTrue(result["native"])
                    self.assertFalse(result["modelConnected"])
                    self.assertTrue((workspace/"native-smoke.png").is_file())
                finally:owner.close();child.wait(timeout=10)
