"""Stage the offline x64 installer from the verified local Aether runtime inputs."""

import argparse
import csv
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

from tools.aether import laptop, qwen35
from tools.aether_teacher.domain import local_path, require
from tools.aether_teacher.setup import ENGINE_FILES, sha_file, trusted_manifest, verify_engine

ROOT = Path(__file__).resolve().parents[3]
PACKAGE = Path(__file__).resolve().parent
BASE = "bb535686a6ca9ca79f1c6190ec9964932a36ded761880ecbe3ba527f1b6d5649"
PYTHON = "0eb85c2dfccccf1b17352de4c397f69194035b7d37149eacc16f1147d93de3b8"
VC_REDIST = "843068991daaa1f73ad9f6239bce4d0f6a07a51f18c37ea2a867e9beca71295c"
OLLAMA_LICENSE = "5934ed2ce0d15154bcdb9c85203210abac0da4314af34081e36df4599f90b226"
GUI = ("pyside6_essentials-6.12.0", "shiboken6-6.12.0")
DEVELOPER_TOOLS = {
    "python-3.12.10-amd64.exe": "67b5635e80ea51072b87941312d00ec8927c4db9ba18938f7ad2d27b328b95fb",
    "node-v24.22.0-x64.msi": "97ebad47ad35da4b59182a5fbe9fad2b1a1836f1042b4833ff5b4b9571175088",
}


def copy(source, target):
    source = local_path(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)


def copy_tree(source, target):
    require(source.is_dir(), f"Missing input {source.name}")
    for path in source.rglob("*"):
        local_path(path)
        if path.is_file() and path.suffix != ".pyc" and "__pycache__" not in path.parts:
            copy(path, target / path.relative_to(source))


def stage_app(stage):
    verify_engine(ROOT, trusted_manifest())
    for name in ENGINE_FILES:
        copy(ROOT / name, stage / name)
    teacher = PACKAGE.parent
    for directory in (teacher, teacher / "ui"):
        for path in directory.glob("*.py"):
            copy(path, stage / path.relative_to(ROOT))
    resources = teacher / "resources"
    copy(resources / "engine-manifest.json", stage / resources.relative_to(ROOT) / "engine-manifest.json")
    copy_tree(resources / "licenses", stage / "licenses/Qt")
    copy(PACKAGE / "windows_entry.py", stage / PACKAGE.relative_to(ROOT) / "windows_entry.py")
    copy(ROOT / "LICENSE.md", stage / "licenses/Wilderness-Odyssey.txt")
    copy(ROOT / "docs/ai/aether-playground-windows.md", stage / "INSTALL-AND-RESTORE.md")


def stage_distribution(site, name, target):
    record = site / f"{name}.dist-info/RECORD"
    with record.open(encoding="utf-8", newline="") as stream:
        for value, _, _ in csv.reader(stream):
            relative = Path(value)
            require(not relative.anchor, "ABSOLUTE_WHEEL_PATH")
            if ".." in relative.parts or relative.suffix == ".pyc" or relative.name in (
                    "direct_url.json", "RECORD"):
                continue
            # The desktop UI uses Qt Widgets. QML ships unused object files
            # whose source paths can exceed Inno Setup 6's path limit.
            if relative.parts[:2] == ("PySide6", "qml"):
                continue
            copy(site / relative, target / relative)


def stage_python(stage, teacher, training):
    archive = teacher / "python-3.12.10.nupkg"
    require(sha_file(archive) == PYTHON, "PYTHON_ARCHIVE_CHANGED")
    target = stage / "python"
    with zipfile.ZipFile(archive) as package:
        for name in package.namelist():
            if not name.startswith("tools/") or name.endswith("/"):
                continue
            relative = Path(name.removeprefix("tools/"))
            require(not relative.anchor and ".." not in relative.parts, "INVALID_PYTHON_ARCHIVE_PATH")
            if relative.parts[0] in ("include", "libs") or "site-packages" in relative.parts:
                continue
            destination = target / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            with package.open(name) as source, destination.open("xb") as stream:
                shutil.copyfileobj(source, stream)
    site = target / "Lib/site-packages"
    for name in GUI:
        stage_distribution(teacher / ".venv-cpython/Lib/site-packages", name, site)
    for record in sorted(training.glob("*.dist-info/RECORD")):
        name = record.parent.name.removesuffix(".dist-info")
        if not name.startswith("pip-"):
            stage_distribution(training, name, site)
    require((site / "PySide6/plugins/platforms/qwindows.dll").is_file(), "WINDOWS_QT_PLUGIN_MISSING")
    require((site / "torch/lib/torch_cuda.dll").is_file(), "PYTORCH_CUDA_MISSING")


def stage_models(stage, qwen):
    assets = stage / "models"
    approved, base = laptop.approval(qwen / "base-approval.json")
    require(base == (qwen / "base").resolve() and approved["baseSha256"] == BASE
            and approved["modelId"] == "qwen3.5:4b", "UNEXPECTED_APPROVED_BASE")
    origin = json.loads((base / "source.json").read_bytes())
    qwen35.validate_tools(qwen / "toolchain/toolchain.json", origin["toolchainManifestSha256"])
    copy_tree(base, assets / "base")
    copy_tree(qwen / "toolchain", assets / "toolchain")
    copy_tree(qwen / "downloads", assets / "downloads")
    approved.pop("modelDirectory")
    (assets / "base-identity.json").write_text(json.dumps(approved), encoding="utf-8")
    source = qwen / "source-workspace/runtime/qwen35-models"
    manifest_path = Path("manifests/registry.ollama.ai/library/qwen3.5/4b")
    manifest = json.loads((source / manifest_path).read_bytes())
    require(any(v["digest"] == "sha256:" + origin["sourceGgufSha256"] for v in manifest["layers"]),
            "SOURCE_MODEL_CHANGED")
    for layer in [manifest["config"], *manifest["layers"]]:
        digest = layer["digest"]
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", digest), "INVALID_SOURCE_BLOB")
        path = Path("blobs") / digest.replace(":", "-")
        require((source / path).stat().st_size == layer["size"] and sha_file(source / path) == digest[7:],
                "SOURCE_BLOB_CHANGED")
        copy(source / path, assets / "source-store" / path)
    copy(source / manifest_path, assets / "source-store" / manifest_path)


def verify_payload(stage):
    for path in stage.rglob("*"):
        relative = path.relative_to(stage)
        require(len(str(path.absolute())) < 260, f"Installer staging path too long: {relative}")
        require(not any(part.lower() in {".git", ".env", "workspace.sqlite3", "settings.json", "workspace.lock",
                                        "credentials", "id_ed25519", "id_rsa", "direct_url.json", "__pycache__"}
                        for part in relative.parts), f"Private or host-specific payload: {relative}")
        require(path.suffix.lower() not in {".sqlite", ".sqlite3", ".pem", ".key", ".pfx", ".p12"}
                or relative.as_posix() == "python/Lib/site-packages/certifi/cacert.pem",
                f"Unexpected credential or database file: {relative}")
        if path.suffix in (".safetensors", ".gguf"):
            require(relative.parts[:2] == ("models", "base"), "UNAPPROVED_MODEL_PAYLOAD")


def compile_installer(work, stage, output):
    prerequisite = work / "vc_redist.x64.exe"
    require(sha_file(prerequisite) == VC_REDIST, "VC_REDIST_CHANGED")
    for name, expected in DEVELOPER_TOOLS.items():
        require(sha_file(work / "developer-tools" / name) == expected, "DEVELOPER_INSTALLER_CHANGED")
    verify_payload(stage)
    print(f"Verified staged application in {stage}", flush=True)
    output.mkdir(parents=True, exist_ok=True)
    require(not any(output.iterdir()), "RELEASE_FOLDER_NOT_EMPTY")
    subprocess.run([str(work / "inno/ISCC.exe"), "/Qp", f"/O{output}", f"/DPayload={stage}",
                    f"/DPrerequisite={prerequisite}", str(PACKAGE / "windows.iss")], check=True)
    copy(stage / "INSTALL-AND-RESTORE.md", output / "INSTALL-AND-RESTORE.md")
    for name in DEVELOPER_TOOLS:
        copy(work / "developer-tools" / name, output / "developer-tools" / name)
    files = sorted(path for path in output.rglob("*") if path.is_file())
    checksums = "".join(f"{sha_file(path)}  {path.relative_to(output).as_posix()}\n" for path in files)
    (output / "SHA256SUMS.txt").write_text(checksums, encoding="ascii")
    print(f"Installer, payload files, and checksums are in {output}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--ollama", type=Path, required=True, help="Verified Ollama 0.17.7 Windows x64 portable tree")
    parser.add_argument("--rocm", type=Path, help="Verified optional AMD libraries from the same Ollama release")
    parser.add_argument("--stage", type=Path, required=True, help="New build staging directory")
    parser.add_argument("--output", type=Path, required=True, help="New or empty release folder")
    args = parser.parse_args()
    work, stage, output = map(local_path, (args.work, args.stage, args.output))
    require(not stage.exists() and (not output.exists() or not any(output.iterdir())), "BUILD_OUTPUT_NOT_EMPTY")
    license_file = work / "Ollama-LICENSE.txt"
    require(sha_file(license_file) == OLLAMA_LICENSE, "OLLAMA_LICENSE_CHANGED")
    stage.mkdir(parents=True)
    stage_app(stage)
    teacher = ROOT / ".codex-build/aether-teacher"
    training = ROOT / ".codex-build/aether-laptop/.venv/Lib/site-packages"
    stage_python(stage, teacher, training)
    copy(args.ollama / "ollama.exe", stage / "ollama/ollama.exe")
    copy_tree(args.ollama / "lib", stage / "ollama/lib")
    if args.rocm:
        copy_tree(args.rocm / "lib", stage / "ollama/lib")
    copy(license_file, stage / "licenses/Ollama.txt")
    stage_models(stage, ROOT / ".codex-build/aether-qwen")
    compile_installer(work, stage, output)


if __name__ == "__main__":
    main()
