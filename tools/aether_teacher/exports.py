"""Atomic outer handoff; the already hashed inner model bundle stays unchanged."""
import json
import os
from pathlib import Path
import shutil
import uuid
from tools.aether_teacher.chat import strict_json
from tools.aether_teacher.domain import Rejected, digest, encode, local_path, require, text, identifier
from tools.aether_teacher.setup import admit_disk, sha_file


def profile_document(raw):
    require(len(raw) <= 32768, "PROFILE_LIMIT")
    profile = strict_json(raw)
    require(isinstance(profile, dict) and set(profile) == {"schemaVersion", "profileId", "revision", "systemPrompt"}
            and profile["schemaVersion"] == "1.0", "INVALID_PROFILE")
    identifier(profile["profileId"])
    require(type(profile["revision"]) is int and profile["revision"] > 0)
    text(profile["systemPrompt"], 4000, 1)
    return profile


def inventory(root):
    entries = []
    for path in sorted(root.rglob("*")):
        local_path(path)
        if path.is_file() and path != root / "teacher-export.json":
            entries.append({"path":path.relative_to(root).as_posix(), "bytes":path.stat().st_size, "sha256":sha_file(path)})
    return entries


def verify_export(directory, verify_model):
    root = local_path(directory)
    manifest_path = root / "teacher-export.json"
    require(manifest_path.is_file() and manifest_path.stat().st_size <= 1048576, "EXPORT_MANIFEST_MISSING")
    manifest = strict_json(manifest_path.read_bytes())
    require(isinstance(manifest, dict) and manifest.get("schemaVersion") == "1.0", "INVALID_EXPORT")
    require(set(p.name for p in root.iterdir()) == {"model", "profile.json", "teacher-export.json", "README.txt"}, "EXPORT_FILE_SET")
    require(manifest.get("files") == inventory(root), "EXPORT_HASH_MISMATCH")
    verify_model(root / "model")
    raw = (root / "profile.json").read_bytes()
    profile_document(raw)
    deployment = strict_json((root / "model/deployment.json").read_bytes())
    require(deployment.get("profileSha256") == manifest.get("profileSha256") == digest(raw), "MODEL_PROFILE_MISMATCH")
    require(manifest.get("modelManifestSha256") == sha_file(root / "model/package.json"), "MODEL_MANIFEST_MISMATCH")
    return manifest


def export_bundle(inner, profile_bytes, destination, verify_model, cancelled=None):
    inner, destination = local_path(inner), local_path(destination)
    require(not destination.exists(), "DESTINATION_EXISTS")
    require(inner != destination and inner not in destination.parents and destination not in inner.parents, "OVERLAPPING_EXPORT")
    verify_model(inner)
    profile_document(profile_bytes)
    deployment = strict_json((inner / "deployment.json").read_bytes())
    require(deployment.get("profileSha256") == digest(profile_bytes), "MODEL_PROFILE_MISMATCH")
    require(set(path.name for path in inner.iterdir()) <= {"model.gguf", "Modelfile", "LICENSE", "LICENSE.txt", "README.md", "deployment.json", "package.json"}, "NONPORTABLE_MODEL_BUNDLE")
    original_manifest = sha_file(inner / "package.json")
    size = sum(p.stat().st_size for p in inner.rglob("*") if p.is_file())
    admit_disk(((destination.parent, size + 256 * 1048576),))
    destination.parent.mkdir(parents=True, exist_ok=True)
    staged = destination.parent / (".aether-export-" + uuid.uuid4().hex)
    staged.mkdir()
    try:
        (staged / "model").mkdir()
        for source in inner.iterdir():
            require(source.is_file(), "NONPORTABLE_MODEL_BUNDLE")
            with local_path(source).open("rb") as src, (staged / "model" / source.name).open("xb") as dst:
                while chunk := src.read(1048576):
                    require(not cancelled or not cancelled.is_set(), "CANCELLED")
                    dst.write(chunk)
                dst.flush(); os.fsync(dst.fileno())
        (staged / "profile.json").write_bytes(profile_bytes)
        (staged / "README.txt").write_text(
            "Aether Teacher reviewed candidate handoff\n\n"
            "Verify teacher-export.json and every inner model/package.json hash before transfer.\n"
            "Import model/Modelfile only on an independently provisioned inference host.\n"
            "profile.json is an operator handoff; the current gateway does not automatically consume it.\n"
            "Review and map its instructions to gateway-owned configuration separately.\n"
            "No deployment or activation was performed. Do not replace project canon automatically.\n", encoding="utf-8")
        manifest = {"schemaVersion":"1.0", "profileSha256":digest(profile_bytes), "modelManifestSha256":original_manifest,
                    "files":inventory(staged), "activation":"not-performed"}
        (staged / "teacher-export.json").write_bytes(encode(manifest))
        verify_export(staged, verify_model)
        require(sha_file(inner / "package.json") == original_manifest, "SOURCE_BUNDLE_CHANGED")
        require(not destination.exists() and (not cancelled or not cancelled.is_set()), "EXPORT_CANCELLED_OR_DESTINATION_EXISTS")
        staged.rename(destination)
        return destination
    finally:
        # Only this call's random staging directory can be removed.
        if staged.exists():
            require(staged.parent == destination.parent and staged.name.startswith(".aether-export-"))
            shutil.rmtree(staged)
