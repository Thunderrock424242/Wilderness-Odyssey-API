"""Real Windows job-object lifecycle regression; only owns its sleeping child."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import time
import unittest
import tempfile
import json


@unittest.skipUnless(os.name == "nt", "Windows job object")
class LifecycleTest(unittest.TestCase):
    def test_closing_job_terminates_owned_child(self):
        spec = importlib.util.spec_from_file_location("local_process", Path(__file__).with_name("local_process.py"))
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        child = None
        try:
            with module.OwnedJob(1024) as job:
                child = subprocess.Popen([sys.executable, "-I", "-c", "import time; time.sleep(120)"])
                job.attach(child)
                self.assertIsNone(child.poll())
            child.wait(timeout=10)
            self.assertIsNotNone(child.returncode)
        finally:
            if child is not None and child.poll() is None: child.kill(); child.wait()

    def test_forced_supervisor_death_terminates_interpreter_descendants(self):
        import psutil
        root = Path(__file__).resolve().parents[2] / ".codex-build/aether-laptop"
        module_path = str(Path(__file__).with_name("local_process.py"))
        spec = importlib.util.spec_from_file_location("supervisor_job",module_path)
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory(dir=root) as temporary:
            marker = Path(temporary) / "ready.json"
            code = "\n".join([
                "import importlib.util,subprocess,sys,os,json,time",
                f"s=importlib.util.spec_from_file_location('local_process',{module_path!r})",
                "m=importlib.util.module_from_spec(s);s.loader.exec_module(m)",
                "job=m.OwnedJob(1024)",
                "child=subprocess.Popen([sys.executable,'-I','-c','import time; time.sleep(120)'])",
                "job.attach(child)",
                f"temporary={str(marker.with_suffix('.tmp'))!r}",
                "with open(temporary,'w') as stream: stream.write(json.dumps({'parent':os.getpid(),'child':child.pid}))",
                f"os.replace(temporary,{str(marker)!r})",
                "time.sleep(120)"])
            supervisor_job = module.OwnedJob(1024)
            parent = subprocess.Popen([sys.executable,"-I","-c",code]); supervisor_job.attach(parent)
            ids = None
            try:
                deadline = time.monotonic()+10
                while not marker.exists() and parent.poll() is None and time.monotonic()<deadline: time.sleep(.05)
                self.assertTrue(marker.exists())
                ids = json.loads(marker.read_text()); child = psutil.Process(ids["child"])
                descendants = child.children(recursive=True)
                psutil.Process(ids["parent"]).kill(); parent.wait(timeout=10)
                _, alive = psutil.wait_procs([child,*descendants],timeout=10)
                self.assertEqual(alive,[])
            finally:
                if ids:
                    for pid in ids.values():
                        try: psutil.Process(pid).kill()
                        except psutil.NoSuchProcess: pass
                if parent.poll() is None: parent.kill(); parent.wait()
                supervisor_job.close()


if __name__ == "__main__": unittest.main()
