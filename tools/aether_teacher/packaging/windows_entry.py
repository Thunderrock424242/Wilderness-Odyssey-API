"""Launch or rebind the installed desktop application to its bundled assets."""

import argparse
import json
from pathlib import Path
import runpy
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))

from tools.aether_teacher.domain import RuntimeSettings, local_path, require
from tools.aether_teacher.backup import open_workspace
from tools.aether_teacher.setup import default_workspace, save_settings, trusted_manifest, verify_engine


def runtime_home():
    if not (ROOT / "online/wheels.json").is_file():
        return ROOT
    from tools.aether_teacher.packaging.online_setup import READY
    runtime = local_path(ROOT / "online-runtime")
    marker = local_path(runtime / "ready")
    require(marker.is_file() and marker.read_bytes() == READY, "ONLINE_RUNTIME_NOT_READY")
    return runtime


def configure(workspace, replace=False):
    workspace = local_path(workspace)
    with open_workspace(workspace):
        if (workspace / "settings.json").exists() and not replace:
            return
        verify_engine(ROOT, trusted_manifest())
        runtime = runtime_home()
        assets = runtime / "models"
        base = local_path(assets / "base")
        source_store = local_path(assets / "source-store")
        converter = local_path(assets / "toolchain/toolchain.json")
        python = runtime / "python/python.exe"
        ollama = runtime / "ollama/ollama.exe"
        require(all(path.is_file() for path in (base / "config.json", converter, python, ollama,
                    source_store / "manifests/registry.ollama.ai/library/qwen3.5/4b")),
                "INSTALLED_ASSETS_MISSING")
        template = json.loads((assets / "base-identity.json").read_bytes())
        require(set(template) == {"schemaVersion", "modelId", "baseSha256"}, "INVALID_BASE_APPROVAL")
        approval = local_path(workspace / "installed-base-approval.json")
        temporary = local_path(approval.with_suffix(".new"))
        temporary.write_text(json.dumps({**template, "modelDirectory": str(base)}), encoding="utf-8")
        temporary.replace(approval)
        save_settings(RuntimeSettings(ROOT, python, ollama, source_store, approval, workspace,
                                      converter_manifest=converter))


def main():
    parser = argparse.ArgumentParser(description="Aether Playground Windows launcher")
    parser.add_argument("--workspace", type=Path)
    parser.add_argument("--configure", action="store_true", help="Rebind a restored workspace to this installation")
    parser.add_argument("--smoke-check", action="store_true")
    options = parser.parse_args()
    workspace = options.workspace or default_workspace()
    try:
        runtime_home()
        configure(workspace, replace=options.configure)
    except Exception as error:
        if sys.stderr is not None:
            print(str(error), file=sys.stderr)
        else:
            from ctypes import windll
            message = ("Open Download Aether Runtime from Start and finish the one-time setup."
                       if str(error) == "ONLINE_RUNTIME_NOT_READY" else
                       "Close other Aether windows and check the installation.\n" + str(error))
            windll.user32.MessageBoxW(None, message,
                                      "Aether Playground", 0x10)
        return 1
    if options.configure:
        return 0
    launch = ROOT / "tools/aether_teacher/launch.py"
    sys.argv = [str(launch), "--workspace", str(workspace)]
    if options.smoke_check:
        sys.argv.append("--smoke-check")
    runpy.run_path(str(launch), run_name="__main__")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
