"""Immutable contracts shared by the simple and technical workspaces."""
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True, repr=False)
class Profile:
    id: str
    revision: int
    job: str
    personality: str
    boundaries: str


@dataclass(frozen=True, repr=False)
class Lesson:
    id: str
    revision: int
    prompt: str
    target: str
    reviewed_revision: int | None = None


@dataclass(frozen=True, repr=False)
class Snapshot:
    dataset_bytes: bytes
    dataset_sha256: str
    profile_bytes: bytes
    profile_sha256: str


@dataclass(frozen=True, repr=False)
class RuntimeSettings:
    engine_root: Path
    python_executable: Path
    ollama_executable: Path
    source_store: Path
    approval_path: Path
    workspace_root: Path
    port: int = 11435


@dataclass(frozen=True)
class RunLimits:
    ram_mib: int = 24576
    gpu_mib: int = 11000
    cpu_threads: int = 2
    timeout_seconds: int = 600


@dataclass(frozen=True)
class JobBinding:
    request_id: str
    base_sha256: str
    engine_sha256: str
    preset_id: str
    dataset_sha256: str
    profile_sha256: str
    limits: RunLimits = field(default_factory=RunLimits)


@dataclass(frozen=True)
class JobRecord:
    id: str
    binding: JobBinding
    state: str
    phase: str
    attempt_id: str
    error_code: str | None = None
    artifact_refs: tuple = ()
