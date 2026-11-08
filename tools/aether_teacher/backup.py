"""Private, portable workspace backups and restart-time replacement."""

from contextlib import closing
import os
from pathlib import Path, PureWindowsPath
import re
import shutil
import sqlite3
import stat
import tempfile
import uuid
from zipfile import BadZipFile, ZIP_STORED, ZipFile

from tools.aether_teacher.domain import Rejected, identifier, local_path, require
from tools.aether_teacher.lease import WorkspaceLease
from tools.aether_teacher.setup import admit_disk

FORMAT = b'{"format":"aether-workspace","version":1}\n'
MARKER = "aether-backup.json"
DATABASE = "workspace.sqlite3"
MODEL_STORE = "runtime/qwen35-models"
STOCK_BLOB = "sha256-c74846553aea7923b4b2ade3394ed2ddd7a6bbdf56058247f727db6a3b13d38c"
MAX_DATABASE = 256 * 1024**2
MAX_SIZE = 256 * 1024**3
MAX_FILES = 50000
TABLES = {
    "profiles": "id revision body",
    "settings": "key value",
    "lessons": "id revision prompt target reviewed",
    "sessions": "id",
    "messages": "session ordinal role content",
    "jobs": "id request binding dataset profile state phase attempt error artifacts claimed",
}


def pending_path(workspace):
    return local_path(workspace.with_name(f".{workspace.name}.pending-import"))


def open_workspace(workspace):
    """Finish a verified import before opening SQLite or starting Ollama."""
    workspace = local_path(workspace)
    with WorkspaceLease(workspace, maintenance=True):
        pending = pending_path(workspace)
        if pending.is_dir():
            previous = None
            if workspace.exists():
                # Check for a running copy, including older launchers that use
                # the workspace lock but do not know the maintenance lock.
                with WorkspaceLease(workspace):
                    pass
                previous = workspace.with_name(f"{workspace.name}-before-import-{uuid.uuid4().hex[:12]}")
                workspace.rename(previous)
            try:
                pending.rename(workspace)
            except OSError:
                if previous is not None and not workspace.exists():
                    previous.rename(workspace)
                raise
        return WorkspaceLease(workspace)


def archive_name(name):
    parts = name.split("/")
    require(len(name) <= 512 and all(part and part not in (".", "..") and len(part) <= 255
                                    and not part.endswith((".", " ")) and not PureWindowsPath(part).is_reserved()
                                    and not re.search(r'[<>:"\\|?*\x00-\x1f]', part) for part in parts),
            "INVALID_BACKUP_PATH")
    require(name in (DATABASE, MARKER) or (len(parts) >= 3 and parts[0] == "jobs")
            or (len(parts) == 4 and parts[:3] == ["runtime", "qwen35-models", "blobs"]
                and re.fullmatch(r"sha256-[0-9a-f]{64}", parts[-1]))
            or (len(parts) >= 4 and parts[:3] == ["runtime", "qwen35-models", "manifests"]),
            "UNEXPECTED_BACKUP_FILE")
    if parts[0] == "jobs":
        identifier(parts[1])
    return parts


def create_backup(workspace, connection, destination):
    workspace, destination = local_path(workspace), local_path(destination)
    require(workspace not in destination.parents and not destination.exists(), "CHOOSE_NEW_BACKUP_FILE")
    files = []
    for directory in ("jobs", MODEL_STORE):
        base = local_path(workspace / directory)
        for path in base.rglob("*"):
            local_path(path)
            if not path.is_file():
                continue
            if path.parent == workspace / MODEL_STORE / "blobs" and (
                    path.name == STOCK_BLOB or re.fullmatch(r"copy-[0-9a-f]{32}|sha256-[0-9a-f]{64}-partial(?:-\d+)?",
                                                            path.name)):
                continue
            name = path.relative_to(workspace).as_posix()
            archive_name(name)
            files.append((path, name))
    total = sum(path.stat().st_size for path, _ in files) + MAX_DATABASE
    require(total <= MAX_SIZE and len(files) <= MAX_FILES - 2, "BACKUP_TOO_LARGE")
    admit_disk(((destination.parent, total + 16 * 1024**2),))
    temporary = destination.with_name(f".aether-backup-{uuid.uuid4().hex}.tmp")
    try:
        with tempfile.TemporaryDirectory(prefix=".aether-db-", dir=destination.parent) as directory:
            snapshot = Path(directory) / DATABASE
            with closing(sqlite3.connect(snapshot)) as copy:
                connection.backup(copy)
            with ZipFile(temporary, "x", compression=ZIP_STORED) as archive:
                archive.writestr(MARKER, FORMAT)
                archive.write(snapshot, DATABASE)
                for path, name in files:
                    archive.write(path, name)
        with temporary.open("r+b") as output:
            os.fsync(output.fileno())
        require(not destination.exists(), "CHOOSE_NEW_BACKUP_FILE")
        temporary.rename(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return destination


def verify_database(path):
    with closing(sqlite3.connect(path.as_uri() + "?mode=ro&immutable=1", uri=True)) as database:
        database.execute("PRAGMA trusted_schema=OFF")
        entries = database.execute("SELECT name, type, sql FROM sqlite_schema").fetchall()
        require({name for name, kind, _ in entries if kind == "table"} == TABLES.keys()
                and all(kind == "index" or kind == "table" and sql.upper().startswith("CREATE TABLE ")
                        for _, kind, sql in entries)
                and database.execute("PRAGMA quick_check(1)").fetchone() == ("ok",), "INVALID_BACKUP_DATABASE")
        for table, columns in TABLES.items():
            require([row[1] for row in database.execute(f"PRAGMA table_info({table})")] == columns.split(),
                    "INVALID_BACKUP_DATABASE")
        for (job_id,) in database.execute("SELECT id FROM jobs"):
            identifier(job_id)


def queue_import(workspace, source):
    workspace, source = local_path(workspace), local_path(source)
    pending = pending_path(workspace)
    require(not pending.exists(), "BACKUP_IMPORT_ALREADY_PENDING")
    require(source.is_file() and source.stat().st_size <= MAX_SIZE, "INVALID_BACKUP_FILE")
    staged = local_path(workspace.with_name(f".{workspace.name}.import-{uuid.uuid4().hex}"))
    try:
        with ZipFile(source) as archive:
            entries = archive.infolist()
            require(2 <= len(entries) <= MAX_FILES, "INVALID_BACKUP_SIZE")
            seen = set()
            for entry in entries:
                require(entry.orig_filename == entry.filename and not entry.is_dir()
                        and not stat.S_ISLNK(entry.external_attr >> 16)
                        and entry.compress_type == ZIP_STORED and entry.compress_size == entry.file_size
                        and entry.filename.casefold() not in seen, "INVALID_BACKUP_ENTRY")
                archive_name(entry.filename)
                seen.add(entry.filename.casefold())
            marker = archive.getinfo(MARKER)
            database = archive.getinfo(DATABASE)
            total = sum(entry.file_size for entry in entries)
            require(marker.file_size == len(FORMAT) and archive.read(marker) == FORMAT, "UNSUPPORTED_BACKUP_FORMAT")
            require(0 < database.file_size <= MAX_DATABASE and total <= MAX_SIZE, "INVALID_BACKUP_SIZE")
            admit_disk(((workspace.parent, total + 16 * 1024**2),))
            staged.mkdir()
            for entry in entries:
                if entry == marker:
                    continue
                target = staged.joinpath(*archive_name(entry.filename))
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(entry) as stream, target.open("xb") as output:
                    shutil.copyfileobj(stream, output, length=1024**2)
        verify_database(staged / DATABASE)
        # Runtime paths belong to this installation. Only copy trusted local
        # setup files after validating the portable archive.
        for name in ("settings.json", "installed-base-approval.json"):
            current = local_path(workspace / name)
            if current.is_file():
                shutil.copyfile(current, staged / name)
        staged.rename(pending)
    except (BadZipFile, KeyError, NotImplementedError, RuntimeError, sqlite3.Error):
        raise Rejected("INVALID_AETHER_BACKUP") from None
    finally:
        if staged.exists():
            shutil.rmtree(staged)
