"""Immutable contracts shared by the simple and technical workspaces."""
from dataclasses import dataclass, field
from pathlib import Path
import hashlib
import json
import re
import uuid


class Rejected(ValueError):
    """A categorical error safe for operational diagnostics."""


def require(condition, code="INVALID_INPUT"):
    if not condition:
        raise Rejected(code)


def text(value, maximum, minimum=0):
    require(isinstance(value, str))
    try:
        length = len(value.encode("utf-16-le", errors="strict")) // 2
    except UnicodeError:
        raise Rejected("INVALID_UNICODE") from None
    require(minimum <= length <= maximum, "TEXT_LIMIT")
    return value


def identifier(value):
    try:
        require(str(uuid.UUID(value)) == value)
    except (ValueError, TypeError, AttributeError):
        raise Rejected("INVALID_ID") from None
    return value


def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")


def digest(value):
    return hashlib.sha256(value).hexdigest()


PRESETS = {
    "laptop-qlora-smoke": (8, 256, 8),
    "laptop-qlora-small": (100, 1024, 100),
}


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


def compile_profile(profile: Profile) -> str:
    identifier(profile.id)
    require(type(profile.revision) is int and profile.revision > 0)
    for value in (profile.job, profile.personality, profile.boundaries):
        text(value, 1000)
    result = f"Job: {profile.job}\nPersonality: {profile.personality}\nBoundaries: {profile.boundaries}"
    return text(result, 4000)


def snapshot(profile: Profile, lessons: tuple[Lesson, ...], preset_id: str) -> Snapshot:
    require(preset_id in PRESETS, "UNSUPPORTED_PRESET")
    require(1 <= len(lessons) <= PRESETS[preset_id][2], "EXAMPLE_LIMIT")
    ids = set()
    items = []
    for lesson in lessons:
        require(type(lesson.revision) is int and lesson.revision > 0)
        require(lesson.reviewed_revision == lesson.revision, "LESSON_NOT_REVIEWED")
        require(isinstance(lesson.id, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,96}", lesson.id))
        require(lesson.id not in ids, "DUPLICATE_LESSON")
        ids.add(lesson.id)
        items.append(dict(id=lesson.id, revision=str(lesson.revision),
                          prompt=text(lesson.prompt, 4000, 1), target=text(lesson.target, 12000, 1)))
    dataset = encode(items)
    require(len(dataset) <= 262144, "DATASET_BYTE_LIMIT")
    profile_bytes = encode(dict(schemaVersion="1.0", profileId=profile.id, revision=profile.revision,
                                systemPrompt=compile_profile(profile)))
    return Snapshot(dataset, digest(dataset), profile_bytes, digest(profile_bytes))


def bounded_context(profile: Profile, turns: tuple[dict, ...], message: str) -> tuple[dict, ...]:
    system = {"role": "system", "content": compile_profile(profile)}
    current = {"role": "user", "content": text(message, 4000, 1)}
    require(len(turns) % 2 == 0, "INCOMPLETE_TURN")
    pairs = []
    for index in range(0, len(turns), 2):
        user, assistant = turns[index:index + 2]
        require(user.get("role") == "user" and assistant.get("role") == "assistant", "INVALID_TURN")
        pairs.append((dict(role="user", content=text(user.get("content"), 4000, 1)),
                      dict(role="assistant", content=text(assistant.get("content"), 16000, 1))))
    selected = []
    total = len(system["content"]) + len(current["content"])
    for user, assistant in reversed(pairs):
        size = len(user["content"]) + len(assistant["content"])
        if len(selected) + 4 > 12 or total + size > 16000:
            break
        selected[0:0] = [user, assistant]
        total += size
    return (system, *selected, current)


def local_path(path: Path) -> Path:
    path = Path(path).absolute()
    require(not str(path).startswith(("\\\\", "//")), "NETWORK_PATH")
    for part in (path, *path.parents):
        require(not part.is_symlink() and not (hasattr(part, "is_junction") and part.is_junction()), "LINKED_PATH")
    return path.resolve()


def validate_binding(binding: JobBinding, data: Snapshot):
    identifier(binding.request_id)
    for value in (binding.base_sha256, binding.engine_sha256, binding.dataset_sha256, binding.profile_sha256):
        require(isinstance(value, str) and re.fullmatch("[0-9a-f]{64}", value))
    require(binding.dataset_sha256 == digest(data.dataset_bytes) == data.dataset_sha256, "DATASET_HASH_MISMATCH")
    require(binding.profile_sha256 == digest(data.profile_bytes) == data.profile_sha256, "PROFILE_HASH_MISMATCH")
    require(binding.preset_id in PRESETS, "UNSUPPORTED_PRESET")
    limits = binding.limits
    require(all(type(v) is int for v in (limits.ram_mib, limits.gpu_mib, limits.cpu_threads, limits.timeout_seconds)))
    require(8192 <= limits.ram_mib <= 32768 and 4096 <= limits.gpu_mib <= 15000
            and 1 <= limits.cpu_threads <= 8 and 1 <= limits.timeout_seconds <= 3600, "RESOURCE_LIMIT")
