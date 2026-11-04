# Aether Teacher desktop

A native Windows/PySide6 teaching workspace for the installed Llama 3.1 8B and the existing reviewed local Aether training engine. It uses a Windows application window, not a browser. Minecraft, website, gateway and bot contracts are unchanged.

The local installation is `%LOCALAPPDATA%/Programs/AetherTeacher`, with an **Aether Teacher** shortcut on the desktop. Its source copy is separate from the repository; update that owned installation explicitly when delivering later source changes. The shortcut currently uses the prepared `.codex-build/aether-teacher/.venv-cpython/Scripts/pythonw.exe` GUI environment. Keep that environment, the registered training interpreter/libraries and reviewed engine/base in place; deleting the repository's entire `.codex-build` directory would remove required local dependencies.

## Everyday use

1. Open the **Aether Teacher** desktop shortcut. The working laptop delivery uses the installed Python GUI environment and native source launcher; it opens a Windows application window without a browser or console. Its only base is Qwen 3.5 4B. Use **Local setup** to register the reviewed engine folder, training interpreter, installed Ollama, prepared Qwen source store, base approval and pinned converter manifest. Setup verifies source/engine hashes and copies the prepared source into a private runtime store; it downloads no model.
2. In **Job & personality**, describe the job, tone and boundaries, then **Save profile**. The next message uses those instructions immediately. Saving is not weight training.
3. In **Teach Aether**, try questions. A model can draft a profile change, but only your Save action applies it. Select an answer and **Correct selected answer** to prepare a lesson.
4. In **Lessons**, save/edit drafts, explicitly review selected lessons, select the reviewed rows, and choose **Train new version**. The confirmation shows the exact profile, examples, preset, budgets and hashes. Editing a lesson revokes its review.
5. A training job progresses through prepare, train, export, import, probe and bundle. A version is ready only after actual artifact, runtime-receipt and inference checks. Failure/cancellation is reported without a success placeholder.
6. In **Versions**, explicitly select a verified candidate, compare it with the original using identical questions/profile, roll back, or export a model/profile handoff. Saving a later profile edit remains supported. Rollback to a candidate restores that candidate's recorded training configuration; it does not erase later profile revisions.
7. **Advanced** shows the same workflow with preset settings, resource ceilings, job states and artifact hashes. It has no shell or arbitrary trainer editor.

New/select/export/delete chat actions manage the bounded local history. Twenty sessions, 200 messages per session, 4 MiB of text per session and a 256 MiB SQLite database are finite limits; capacity never silently deletes lessons or accepted snapshots. Chat export is an explicit private-data export, separate from model export.

## Private paths and runtime

The default workspace is `%LOCALAPPDATA%/WildernessOdyssey/AetherTeacher`. `settings.json`, `workspace.sqlite3`, the exclusive application lease, engine staging, jobs and `runtime/` belong there. Settings contain local paths, not service credentials. Do not commit or publish this directory. An explicit `--workspace` argument supports a separate local test workspace; linked/UNC paths are rejected.

The app owns an Ollama service at `127.0.0.1:11435`, with separate models and home directories. It refuses an occupied port and never takes over/stops another runtime at 11434. It holds a workspace lease before DB/runtime/recovery work, and Windows kill-on-close jobs own launched descendants. Closing during work offers Stay or Cancel and exit. Restart reconciles recorded artifacts; it never automatically repeats a claimed optimizer.

Private runtime environment (set by the application, not global Windows settings):

| Setting | Value |
| --- | --- |
| `OLLAMA_HOST` | `127.0.0.1:11435` |
| `OLLAMA_MODELS` | `<workspace>/runtime/qwen35-models` |
| `USERPROFILE`, `HOME` for the managed service | `<workspace>/runtime/home` |
| `OLLAMA_NO_CLOUD` | `1` |
| `OLLAMA_NUM_PARALLEL`, `OLLAMA_MAX_LOADED_MODELS` | `1` |
| Training `HF_HUB_OFFLINE`, `TRANSFORMERS_OFFLINE`, `HF_HUB_DISABLE_TELEMETRY` | `1` |

No Hugging Face, OpenAI, gateway monitoring or administration credential is required by this desktop workflow. The existing gateway's separate authentication remains unchanged. No production chat ingestion, model activation, upload or authoritative Minecraft mutation is performed.

## Verified laptop prerequisites

The acceptance machine is Windows 11 x64 with an RTX 4090 Laptop GPU (16 GiB), approximately 32 GiB RAM, an installed compatible NVIDIA/CUDA driver, and local Ollama 0.17.7. Keep the pinned official Qwen text safetensors base and approval, prepared Qwen source Ollama store, converter archives/extracted tools/manifest, and registered Python environments available. No original Llama model is required by the app. The reviewed model is text-only; image/video teaching is not provided.

Training defaults: RAM/commit ceiling 24576 MiB, GPU allocation budget 11000 MiB, two CPU threads; smoke 8 steps/256 tokens/up to 8 examples/600 seconds. Small is 100 steps/1024 tokens/up to 100 examples, deadline at most 3600 seconds. Rank 4, alpha 8, learning rate 0.0002 and batch 1 are preset-owned. A profile that exceeds the smoke context must use an admitted larger preset or be shortened explicitly.

Qwen export ceiling is 16384 MiB/1800 seconds; merge and converter processes run sequentially. The native converter's diagnostics are drained with a finite 16 MiB total/32 KiB tail, without forwarding raw private diagnostics to the UI. Runtime import has an independent 32768 MiB committed-memory ceiling and an 1800-second deadline. Full-run admission requires at least 60 GiB and the larger calculated peak estimate on each involved volume. These are ceilings/admission rules, not a promise that another machine fits. Keep model operations serialized.

The laptop launcher uses the separate GUI environment containing Qt/Python/psutil, without importing CUDA/Torch into the GUI. It needs the registered reviewed engine and training environment for training. There is no automatic clean-machine dependency installer.

## Build and validation

Source is under `tools/aether_teacher/`; use a separate GUI environment with `requirements-build.txt`. The working delivery was built with official PSF CPython 3.12.10 (NuGet `python/3.12.10`), PySide6 6.12.0, PyInstaller 6.22.3 and psutil 7.2.2. The checked-in `resources/engine-manifest.json` binds the reviewed engine files, normalizing only CRLF/LF checkout differences. The builder rejects changed engine source; update that manifest only as part of a reviewed engine change.

```powershell
& .codex-build/aether-teacher/.venv-cpython/Scripts/python.exe -B -m unittest discover -s tools/aether_teacher/tests
& .codex-build/aether-teacher/.venv-cpython/Scripts/python.exe -B -u -X faulthandler -m tools.aether_teacher.packaging.build
```

Experimental frozen output: `.codex-build/aether-teacher/dist/AetherTeacher/AetherTeacher.exe`. **This output is not a verified usable release**: actual launch fails with a QtWidgets DLL missing-procedure error, despite successful PyInstaller assembly. Do not distribute it as the working app. The laptop delivery instead launches `tools/aether_teacher/launch.py` with the installed GUI environment's `pythonw.exe` through a desktop shortcut. Keep that GUI environment and application source available.

For source-launch acceptance, `launch.py --smoke-check --workspace <new-private-directory>` opens the real Windows UI, checks its five pages, captures a native screenshot/result, and exits without installing/starting a model. This was exercised from a working directory outside the repository. The `--workspace` directory must be new/without saved setup to test the missing-setup state.

The project-local diagnostic training interpreter `.codex-build/aether-teacher/.venv-training-cpython` uses CPython 3.12.10 with a private `.pth` referencing the existing version-checked training libraries in `.codex-build/aether-laptop/.venv`. It leaves the original environment unchanged. The installed stack is Torch 2.11.0+cu128, Transformers 5.16.1, PEFT 0.18.1, bitsandbytes 0.50.2, psutil 7.2.2 and gguf 0.19.0. This linkage is local setup, not a self-contained training distribution; do not remove the referenced environment.

Native access violations were observed in the bundled CPython 3.12.14 training and packaging processes. Official CPython completed the verified training run. Packaging builds were intermittent even under official CPython; a bounded 16 MiB build-thread stack and a consistent bundled MSVC runtime family were tested, but the frozen GUI launch still failed. The portable executable remains unfinished. No global Python, PATH, registry, ACL, antivirus, pagefile or other security setting was changed. These observations do not establish a hardware diagnosis.

## Deployment boundary

Export creates a new outer directory with `model/` (the unchanged hashed portable bundle), `profile.json`, `teacher-export.json` and operator instructions. It excludes chats, lesson text, accepted input files, credentials and machine paths. Every inventory/hash is verified before atomic publication. Profile/model mismatches and corrupt/extra files fail closed.

Transfer only a reviewed export to a separately provisioned inference host. Verify both the outer inventory and inner `model/package.json`; import the inner Modelfile locally on that host. `profile.json` is an operator handoff: the existing gateway does not consume it automatically, so review/map it to gateway-owned instructions separately. This release does not establish Kinetic capacity, change remote environment settings, or deploy/activate a candidate. Hosting Qwen alongside Minecraft still needs its own measured CPU/RAM/runtime admission; the 2.71 GB file size is not the complete runtime budget.

See the [dated acceptance evidence](aether-teacher-acceptance-2026-11-04.md), [design](../superpowers/specs/2026-11-04-aether-teacher-desktop-design.md) and [implementation plan](../superpowers/plans/2026-11-04-aether-teacher-desktop.md).

The Qwen migration supersedes the model choice in the earlier Llama acceptance record. See [model compatibility](aether-teacher-model-compatibility.md) and the [Qwen acceptance record](aether-teacher-qwen-acceptance-2026-11-04.md). Changing the approved base preserves teaching/history records, resets the app selection to Qwen, clears rollback to a previous-base selection, and quarantines active jobs with a different base identity. Old-base/engine versions are omitted from current selectable versions; accepted inputs remain immutable.
