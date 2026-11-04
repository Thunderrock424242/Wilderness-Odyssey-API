"""Finite worker queues; callbacks cross to the GUI using Qt signals."""
from concurrent.futures import ThreadPoolExecutor
import threading
import uuid
from PySide6.QtCore import QObject, Signal
from tools.aether_teacher.domain import Rejected


class Worker(QObject):
    done = Signal(str, object, str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="aether-teacher")
        self.capacity = threading.BoundedSemaphore(16)
        self.pending = 0

    def submit(self, operation):
        if not self.capacity.acquire(blocking=False):
            raise Rejected("WORK_QUEUE_FULL")
        key = str(uuid.uuid4())
        self.pending += 1
        future = self.executor.submit(operation)
        def complete(result):
            error = ""
            value = None
            try:
                value = result.result()
            except Rejected as failure:
                error = str(failure)
            except Exception:
                error = "LOCAL_OPERATION_FAILED"
            self.capacity.release()
            self.done.emit(key, value, error)
        future.add_done_callback(complete)
        return key

    def close(self):
        self.executor.shutdown(wait=True, cancel_futures=True)
