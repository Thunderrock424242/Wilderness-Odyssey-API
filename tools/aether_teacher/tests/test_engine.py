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


if __name__ == "__main__": unittest.main()
