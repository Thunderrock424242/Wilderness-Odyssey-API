import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from tools.aether_teacher.domain import Rejected


class EngineTest(unittest.TestCase):
    def test_output_is_bounded_and_wrong_json_type_rejected(self):
        from tools.aether_teacher.engine import run_owned
        result = run_owned([sys.executable, "-c", "print('{\"value\":3}')"], 1024, 10)
        self.assertEqual(result, {"value":3})
        for code in ("print('x'*70000)", "print('[]')", "raise RuntimeError('private teaching text')"):
            with self.assertRaises(Rejected) as error:
                run_owned([sys.executable, "-c", code], 1024, 10)
            self.assertNotIn("private teaching text", str(error.exception))

    def test_multiple_cli_error_records_keep_category_without_raw_diagnostics(self):
        from tools.aether_teacher.engine import run_owned
        code="import sys;print('private library text',file=sys.stderr);print('{\"childFailure\":{\"error\":\"WINDOWS_JOB_ASSIGNMENT_FAILED\"}}',file=sys.stderr);print('{\"error\":\"GPU_TRAINING_FAILED\"}',file=sys.stderr);sys.exit(1)"
        with self.assertRaisesRegex(Rejected,"GPU_TRAINING_FAILED"):
            run_owned([sys.executable,"-c",code],1024,10)

    def test_cancellation_stops_owned_child_and_preserves_other_listener(self):
        from tools.aether_teacher.engine import run_owned
        cancelled = threading.Event()
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0)); listener.listen()
        self.addCleanup(listener.close)
        timer = threading.Timer(.3, cancelled.set)
        timer.start()
        self.addCleanup(timer.join)
        started = time.monotonic()
        with self.assertRaisesRegex(Rejected, "CANCELLED"):
            run_owned([sys.executable, "-c", "import time; time.sleep(120)"], 1024, 10, cancelled)
        self.assertLess(time.monotonic() - started, 5)
        with socket.create_connection(listener.getsockname(), timeout=1):
            pass

    def test_forced_owner_death_terminates_suspended_start_descendants(self):
        from tools.aether.local_process import OwnedJob
        import psutil
        source = Path(__file__).resolve().parents[2] / "aether/local_process.py"
        with tempfile.TemporaryDirectory() as tmp:
            marker=Path(tmp)/"ready.json"
            code="\n".join(("import importlib.util,json,os,sys,time",
                f"s=importlib.util.spec_from_file_location('job',{str(source)!r})",
                "m=importlib.util.module_from_spec(s);s.loader.exec_module(m)",
                "owner=m.OwnedJob(1024)",
                "child=owner.start([sys.executable,'-c','import time;time.sleep(120)'])",
                f"open({str(marker)!r},'w').write(json.dumps({{'owner':os.getpid(),'child':child.pid}}))",
                "time.sleep(120)"))
            with OwnedJob(1024) as cleanup:
                parent=cleanup.start([sys.executable,"-c",code])
                try:
                    deadline=time.monotonic()+10
                    while not marker.exists() and parent.poll() is None and time.monotonic()<deadline:time.sleep(.02)
                    self.assertTrue(marker.exists())
                    ids=json.loads(marker.read_text());child=psutil.Process(ids["child"])
                    descendants=child.children(recursive=True)
                    psutil.Process(ids["owner"]).kill()
                    parent.wait(timeout=10)
                    _,alive=psutil.wait_procs([child,*descendants],timeout=10)
                    self.assertEqual(alive,[])
                finally:
                    cleanup.close();parent.wait(timeout=10)


if __name__ == "__main__": unittest.main()
