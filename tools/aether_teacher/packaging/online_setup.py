"""Provision the Windows download edition using public, pinned artifacts."""

import argparse
import json
from pathlib import Path, PureWindowsPath
import re
import shutil
import stat
import subprocess
import sys
from urllib.parse import urlsplit
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
if __name__ == "__main__":
    sys.path.insert(0, str(ROOT))

from tools.aether import laptop, qwen35
from tools.aether.local_process import OwnedJob
from tools.aether_teacher.domain import local_path, require
from tools.aether_teacher.engine import private_environment, run_owned
from tools.aether_teacher.lease import WorkspaceLease
from tools.aether_teacher.setup import admit_disk, default_workspace, trusted_manifest, verify_engine

READY = b"aether-online-runtime-v1\n"
WHEEL_HOSTS = {"files.pythonhosted.org", "download-r2.pytorch.org"}
OLLAMA = (
    ("ollama-windows-amd64.zip", 2059474803, "67710550b4b77d86dc307b52d84cb3b5780847d5468428325470e37e1a394a72"),
    ("ollama-windows-amd64-rocm.zip", 381238159, "8f2acc65734ef360b5a7522325cecfd46301245ad970146d8380f542ab7bbbfc"),
)


def validate_download(spec, hosts):
    url = urlsplit(spec["url"])
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.+-]{0,180}", spec["file"])
            and not PureWindowsPath(spec["file"]).is_reserved()
            and url.scheme == "https" and url.netloc in hosts and not url.query and not url.fragment
            and type(spec["size"]) is int and 0 < spec["size"] <= 6 * 1024**3
            and re.fullmatch(r"[0-9a-f]{64}", spec["sha256"]), "INVALID_DOWNLOAD_PIN")


def fetch(spec, directory, hosts):
    validate_download(spec, hosts)
    target = local_path(directory / spec["file"])
    # The provisioner holds the install's maintenance lock. An interrupted
    # file can be retried while already verified downloads remain cached.
    local_path(target.with_name(target.name + ".download")).unlink(missing_ok=True)
    print(f"Verifying or downloading {target.name} ({spec['size'] / 1e6:.1f} MB)", flush=True)
    qwen35.download(spec["url"], target, spec)
    return target


def extract_ollama(archive, destination):
    destination = local_path(destination)
    with ZipFile(archive) as bundle:
        items = bundle.infolist()
        require(len(items) <= 5000 and sum(item.file_size for item in items) <= 8 * 1024**3,
                "OLLAMA_ARCHIVE_TOO_LARGE")
        seen = set()
        for item in items:
            parts = item.filename.rstrip("/").split("/")
            require(all(part and part not in (".", "..") and not part.endswith((".", " "))
                        and not PureWindowsPath(part).is_reserved()
                        and not re.search(r'[<>:"\\|?*\x00-\x1f]', part) for part in parts)
                    and item.filename.casefold() not in seen
                    and not stat.S_ISLNK(item.external_attr >> 16), "INVALID_OLLAMA_ARCHIVE_PATH")
            seen.add(item.filename.casefold())
            if item.is_dir() or (item.filename != "ollama.exe" and parts[0] != "lib"):
                continue
            target = local_path(destination.joinpath(*parts))
            target.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(item) as source, target.open("xb") as output:
                shutil.copyfileobj(source, output, 1024**2)


def install_python(root, recipe, cache, stage):
    pins = json.loads((recipe / "wheels.json").read_bytes())
    require(isinstance(pins, list) and 1 <= len(pins) <= 80
            and len({item["file"].lower() for item in pins}) == len(pins), "INVALID_WHEEL_LOCK")
    wheels = [fetch(spec, cache / "wheels", WHEEL_HOSTS) for spec in pins]
    python = stage / "python"
    shutil.copytree(root / "python", python, ignore=shutil.ignore_patterns("site-packages", "__pycache__"))
    print("Installing verified training wheels…", flush=True)
    command = [str(root / "python/python.exe"), "-I", "-B", "-m", "pip", "--isolated", "--disable-pip-version-check",
               "install", "--no-index", "--no-deps", "--no-compile", "--no-warn-script-location",
               "--target", str(python / "Lib/site-packages"), *map(str, wheels)]
    with OwnedJob(4096) as owner:
        process = owner.start(command, env=private_environment(), stdin=subprocess.DEVNULL)
        try:
            require(process.wait(timeout=900) == 0, "TRAINING_WHEEL_INSTALL_FAILED")
        finally:
            owner.close()
            process.wait(timeout=10)
    return python / "python.exe"


def acquire_model_inputs(cache):
    inputs = cache / "qwen"
    inputs.mkdir(parents=True, exist_ok=True)
    unpacked = local_path(inputs / "toolchain")
    if unpacked.exists():
        shutil.rmtree(unpacked)
    return qwen35.acquire(inputs)


def prepare_models(root, recipe, stage, python, acquired):
    origin = json.loads((recipe / "base-source.json").read_bytes())
    converter = Path(acquired["toolchainManifest"])
    require(acquired["toolchainSha256"] == origin["toolchainManifestSha256"], "CONVERTER_IDENTITY_CHANGED")
    assets = stage / "models"
    assets.mkdir()
    base = assets / "base"
    command = [str(python), "-I", "-B", str(root / "tools/aether/qwen35.py"),
               "--state-directory", str(stage / "operations")]
    print("Rebuilding the reviewed Qwen training base…", flush=True)
    run_owned([*command, "prepare", "--source", acquired["source"], "--output", str(base),
               "--toolchain-manifest", str(converter)], 28672, 1850)
    shutil.copyfile(recipe / "base-source.json", base / "source.json")
    identity = json.loads((recipe / "base-identity.json").read_bytes())
    require(laptop.core().model_digest(base) == identity["baseSha256"], "APPROVED_BASE_HASH_MISMATCH")
    print("Rebuilding the approved Qwen chat model…", flush=True)
    gguf = stage / "converted"
    result = run_owned([*command, "convert", "--model", str(base), "--output", str(gguf),
                        "--toolchain-manifest", str(converter)], 20480, 1850)
    digest = origin["sourceGgufSha256"]
    require(result["ggufSha256"] == digest, "APPROVED_CHAT_MODEL_HASH_MISMATCH")
    shutil.copytree(recipe / "source-store", assets / "source-store")
    (gguf / "model.gguf").rename(assets / "source-store/blobs" / f"sha256-{digest}")
    shutil.rmtree(gguf)
    shutil.rmtree(stage / "operations")
    for name in ("toolchain", "downloads"):
        shutil.copytree(converter.parent.parent / name, assets / name)
    shutil.copyfile(recipe / "base-identity.json", assets / "base-identity.json")


def provision(root):
    root = local_path(root)
    recipe = local_path(root / "online")
    runtime = local_path(root / "online-runtime")
    cache = local_path(root / "online-download-cache")
    verify_engine(root, trusted_manifest())
    with WorkspaceLease(runtime, maintenance=True):
        if runtime.exists():
            require((runtime / "ready").read_bytes() == READY, "ONLINE_RUNTIME_NOT_READY")
            return
        stage = local_path(root / "online-installing")
        if stage.exists():
            shutil.rmtree(stage)
        for partial in cache.rglob("*.download"):
            local_path(partial).unlink()
        # Allow for inputs already on this volume when a previous run stopped.
        cached = sum(local_path(path).stat().st_size for path in cache.rglob("*") if path.is_file())
        admit_disk(((root, 60 * 1024**3 - min(cached, 15 * 1024**3)),))
        stage.mkdir()
        try:
            acquired = acquire_model_inputs(cache)
            python = install_python(root, recipe, cache, stage)
            for name, size, digest in OLLAMA:
                spec = {"file": name, "size": size, "sha256": digest,
                        "url": f"https://github.com/ollama/ollama/releases/download/v0.17.7/{name}"}
                extract_ollama(fetch(spec, cache / "ollama", {"github.com"}), stage / "ollama")
            prepare_models(root, recipe, stage, python, acquired)
            (stage / "ready").write_bytes(READY)
            stage.rename(runtime)
        finally:
            if stage.exists():
                shutil.rmtree(stage)
        shutil.rmtree(cache)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path)
    parser.add_argument("--wait-on-error", action="store_true")
    options = parser.parse_args()
    try:
        provision(ROOT)
        from tools.aether_teacher.packaging.windows_entry import configure
        configure(options.workspace or default_workspace(), replace=True)
        print("Verified setup is complete. Open Aether Playground Online from Start.", flush=True)
        return 0
    except (Exception, KeyboardInterrupt) as error:
        print(f"Setup stopped: {error}\nOpen Download Aether Runtime from Start to retry.", file=sys.stderr, flush=True)
        if options.wait_on_error:
            input("Press Enter to close setup.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
