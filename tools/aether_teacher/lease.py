"""Exclusive application ownership, released by Windows on process death."""
import msvcrt
import os
from tools.aether_teacher.domain import Rejected, local_path


class WorkspaceLease:
    def __init__(self, root):
        root=local_path(root);root.mkdir(parents=True,exist_ok=True)
        self.stream=local_path(root/"workspace.lock").open("a+b")
        if self.stream.tell()==0:
            self.stream.write(b"0");self.stream.flush();os.fsync(self.stream.fileno())
        self.stream.seek(0)
        try:msvcrt.locking(self.stream.fileno(),msvcrt.LK_NBLCK,1)
        except OSError:
            self.stream.close();self.stream=None
            raise Rejected("WORKSPACE_ALREADY_OPEN") from None

    def close(self):
        if self.stream is not None:
            try:
                self.stream.seek(0);msvcrt.locking(self.stream.fileno(),msvcrt.LK_UNLCK,1)
            finally:self.stream.close();self.stream=None

    def __enter__(self):return self
    def __exit__(self,*ignored):self.close()
