"""Windows-owned process tree with OS memory and kill-on-close limits.

The non-inheritable job handle lives only in the supervisor. Its closure, including
on forced supervisor termination, kills the attached interpreter and descendants.
This does not change any machine security settings or unrelated processes.
"""
import ctypes
from ctypes import wintypes
import os
import subprocess


class OwnedJob:
    def __init__(self, memory_mib):
        if os.name != "nt": raise ValueError("WINDOWS_LOCAL_RUNNER_REQUIRED")
        class Basic(ctypes.Structure):
            _fields_ = [("processTime",ctypes.c_longlong),("jobTime",ctypes.c_longlong),
                ("flags",wintypes.DWORD),("minWorking",ctypes.c_size_t),("maxWorking",ctypes.c_size_t),
                ("activeProcesses",wintypes.DWORD),("affinity",ctypes.c_size_t),
                ("priority",wintypes.DWORD),("scheduling",wintypes.DWORD)]
        class Io(ctypes.Structure):
            _fields_ = [(name,ctypes.c_ulonglong) for name in ("readOps","writeOps","otherOps","readBytes","writeBytes","otherBytes")]
        class Extended(ctypes.Structure):
            _fields_ = [("basic",Basic),("io",Io),("processMemory",ctypes.c_size_t),("jobMemory",ctypes.c_size_t),
                        ("peakProcess",ctypes.c_size_t),("peakJob",ctypes.c_size_t)]
        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        signatures = {
            "CreateJobObjectW":([ctypes.c_void_p,wintypes.LPCWSTR],wintypes.HANDLE),
            "SetInformationJobObject":([wintypes.HANDLE,ctypes.c_int,ctypes.c_void_p,wintypes.DWORD],wintypes.BOOL),
            "OpenProcess":([wintypes.DWORD,wintypes.BOOL,wintypes.DWORD],wintypes.HANDLE),
            "AssignProcessToJobObject":([wintypes.HANDLE,wintypes.HANDLE],wintypes.BOOL),
            "IsProcessInJob":([wintypes.HANDLE,wintypes.HANDLE,ctypes.POINTER(wintypes.BOOL)],wintypes.BOOL),
            "CloseHandle":([wintypes.HANDLE],wintypes.BOOL)}
        for name,(arguments,result) in signatures.items():
            method = getattr(self.api,name); method.argtypes=arguments; method.restype=result
        self.handle = self.api.CreateJobObjectW(None,None)
        if not self.handle: raise ValueError("WINDOWS_JOB_UNAVAILABLE")
        limits = Extended()
        limits.basic.flags = 0x2000 | 0x200 | 0x8  # kill on close, total committed memory, active-process cap
        limits.basic.activeProcesses = 8; limits.jobMemory = memory_mib * 1048576
        if not self.api.SetInformationJobObject(self.handle,9,ctypes.byref(limits),ctypes.sizeof(limits)):
            self.close(); raise ValueError("WINDOWS_JOB_LIMITS_UNAVAILABLE")

    def attach(self, process):
        self.attach_pid(process.pid)
        # A Windows venv launcher can create the real interpreter before the
        # supervisor attaches it. Include those existing children as well.
        import psutil
        for child in psutil.Process(process.pid).children(recursive=True):
            self.attach_pid(child.pid)

    def start(self, command, **kwargs):
        """Assign before the first instruction, including venv launcher children.

        Newer Windows venv redirectors create their own child job. Assigning a
        running redirector afterwards can conflict with that job's hierarchy.
        Starting suspended makes our ownership the ancestor of every child job.
        """
        flags = kwargs.pop("creationflags", 0)
        process = subprocess.Popen(command, creationflags=flags | 0x4, **kwargs)
        try:
            self.attach_pid(process.pid)
            import psutil
            threads = psutil.Process(process.pid).threads()
            if len(threads) != 1:
                raise ValueError("SUSPENDED_PROCESS_THREAD_REQUIRED")
            self.api.OpenThread.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
            self.api.OpenThread.restype = wintypes.HANDLE
            self.api.ResumeThread.argtypes = [wintypes.HANDLE]
            self.api.ResumeThread.restype = wintypes.DWORD
            thread = self.api.OpenThread(0x2, False, threads[0].id)
            try:
                if not thread or self.api.ResumeThread(thread) == 0xFFFFFFFF:
                    raise ValueError("SUSPENDED_PROCESS_RESUME_FAILED")
            finally:
                if thread:
                    self.api.CloseHandle(thread)
            return process
        except BaseException:
            process.kill()
            process.wait(timeout=10)
            raise

    def attach_pid(self, pid):
        handle = self.api.OpenProcess(0x100 | 0x1 | 0x1000,False,pid)  # SET_QUOTA | TERMINATE | QUERY_LIMITED_INFORMATION
        try:
            assigned = wintypes.BOOL()
            if handle and self.api.IsProcessInJob(handle,self.handle,ctypes.byref(assigned)) and assigned.value: return
            if not handle or not self.api.AssignProcessToJobObject(self.handle,handle):
                raise ValueError("WINDOWS_JOB_ASSIGNMENT_FAILED")
        finally:
            if handle: self.api.CloseHandle(handle)

    def close(self):
        if self.handle: self.api.CloseHandle(self.handle); self.handle=None

    def __enter__(self): return self
    def __exit__(self,*ignored): self.close()
