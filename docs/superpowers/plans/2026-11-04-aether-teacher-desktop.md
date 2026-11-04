# Aether Teacher Desktop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:executing-plans` for main-agent, inline implementation, following the repository's normal workflow. Use test-driven development for meaningful behavior changes and request a final independent code review. No parallel Gradle/model processes.

**Goal:** Deliver a native Windows teaching program with real local chat, saved job/personality profiles, reviewed lessons, durable candidate training, comparison/export, and an Advanced technical workspace.

**Architecture:** A PySide6 Widgets UI calls feature-owned controllers backed by a private SQLite store. Bounded workers and supervised processes reuse the verified `tools/aether` engine, with a separate app-owned Ollama instance/store. The same controllers enforce policy from both simple and Advanced views.

**Tech Stack:** Windows x64; Python 3.12; proposed GUI/build pins `PySide6==6.12.0` and `PyInstaller==6.22.3`; stdlib SQLite/dataclasses/unittest; Qt Network/Core/Test. Keep CUDA PyTorch 2.11.0+cu128 and the existing pinned training environment separate from GUI dependencies. Release availability and Python/Windows wheels were checked against [PySide6 on PyPI](https://pypi.org/project/PySide6/6.12.0/) and [PyInstaller on PyPI](https://pypi.org/project/pyinstaller/6.22.3/); installation/GUI compatibility is not yet verified.

**Spec:** [Desktop design](../specs/2026-11-04-aether-teacher-desktop-design.md). Read both documents before implementing.

## Start condition and current readiness

The user asked for preparation only and said implementation should start when they return in the morning. **No application scaffolding, dependencies, native build, training, model conversion, or deployment is to run as part of this preparation.** Resume when the user says to start; first surface the storage item below before any large model operation. No wake-up reminder or automatic overnight task was requested.

- Base implementation: `46df011821afaff5529605678aea7ada60678066`; design commit `9f1323b16bbee046b5c69cd6ae8a3303be214fb3`. Preserve subsequent user commits/work; do not reset to these commits.
- Interpreter/training environment present: Python 3.12.14, torch 2.11.0+cu128, Transformers 5.16.1, PEFT 0.18.1, bitsandbytes 0.50.2. This check read package metadata only; it did not rerun CUDA.
- Existing approval: `.codex-build/aether-laptop/llama31-ollama-base.json`; verified bundle: `.codex-build/aether-laptop/deployment/llama31-ollama-qlora-002/`. Reverify hashes before reuse.
- Proposed GUI environment `.codex-build/aether-teacher/.venv` does not exist. PySide6/PyInstaller are not installed in the inspected training environment; do not install them there.
- Observed free disk at preparation: **21.27 GiB**. GUI/core work can start; full 8B preparation/training/export must fail preflight until sufficient capacity is available. Minimum floor is 60 GiB plus the actual multi-volume peak estimate. Do not automatically delete existing artifacts or user Ollama blobs to make room.

## Global constraints

- Preserve peer contract commit `60a962005e1684610da1b6588e63b6fc195f0fd5`, native health/admission and hosted Linux/cgroup admission. Do not add server/bot/website routes.
- Native Windows window/shortcut; no browser/WebView dependency for daily use. The existing HTML files are throwaway design previews, not application source.
- Aether/Llama 3.1 8B first; no cloud requests, automatic downloads, production chat ingestion, deployment or model activation.
- App workspace: `%LOCALAPPDATA%/WildernessOdyssey/AetherTeacher`. Runtime: fixed loopback `127.0.0.1:11435`, private model store. Never stop/take over another process or modify its store at 11434.
- Profile fields job/personality/boundaries: each <=1000 UTF-16 units; compiled system prompt <=4000. Draft proposals require explicit Save. Edited lessons lose review approval.
- Chat: input <=4000 UTF-16 units; <=12 messages including system/current user; <=16000 total input characters; <=1024 output tokens and <=16000 output characters. Preserve omitted turns in bounded local history.
- SQLite cap 256 MiB; transcript cap 20 sessions, 200 messages/session, 4 MiB/session. Refuse at capacity without silently deleting material. Operational logs: proposed three rotating 256 KiB files containing no private dialogue, lesson targets, system prompts or secrets.
- One unfinished training workflow per workspace; repeated identical request IDs return the existing job. No automatic optimizer replay after a claim/crash. Keep all accepted bindings immutable.
- Defaults: training RAM 24576 MiB, VRAM 11000 MiB, CPU threads 2, smoke timeout 600s, small timeout <=3600s; export RAM 8192 MiB/time <=1800s; runtime import <=1800s and managed runtime committed-memory ceiling 32768 MiB. Verify that runtime ceiling with actual conversion; do not infer fitness from a flag.
- Presets remain `laptop-qlora-smoke` (8 steps/256 tokens/8 examples), `laptop-qlora-small` (100/1024/100), and unquantized smoke only when admitted. LR 0.0002, rank 4, alpha 8, batch 1 remain preset-owned.
- No security/ACL/pagefile changes, unrelated process termination, arbitrary commands/URLs, or automatic cleanup of user model libraries.

## File ownership and interfaces

Create `tools/aether_teacher/` with `__init__.py`, `__main__.py`, `app.py`, and cohesive modules:

| Module | Responsibility |
| --- | --- |
| `domain.py` | Frozen records, validation, deterministic profile/snapshot serialization and context bounds |
| `store.py` | SQLite revisions, sessions, reviews, durable jobs, phase claims and selections |
| `setup.py`, `engine.py` | Registered engine/environment checks, safe command construction, supervised CLI execution |
| `runtime.py`, `chat.py` | Owned private Ollama, source import, bounded requests/proposals/comparisons |
| `jobs.py` | Accepted workflow, phase transitions, cancellation and reconciliation |
| `exports.py` | Verified model/profile outer package, without mutating the hashed inner bundle |
| `ui/window.py`, `ui/teaching.py`, `ui/profile.py`, `ui/lessons.py`, `ui/versions.py`, `ui/advanced.py`, `ui/setup.py` | Native views and presentation only |
| `packaging/build.py`, `packaging/AetherTeacher.spec`, `requirements.txt`, `requirements-build.txt` | Separate GUI environment, trusted engine manifest, onedir Windows package |

Tests go under `tools/aether_teacher/tests/`, mirroring these responsibilities. Resources belong to this feature. Extend `tools/aether/laptop.py`, its tests, and the bundled trainer only for local runtime/state/profile integration. Do not import desktop modules from Java/Minecraft or move unrelated packages.

Pin these shared domain names before dependent tasks: `Profile(id, revision, job, personality, boundaries)`, `Lesson(id, revision, prompt, target, reviewed_revision)`, `Snapshot(dataset_bytes, dataset_sha256, profile_bytes, profile_sha256)`, `RuntimeSettings(engine_root, python_executable, ollama_executable, source_store, approval_path, workspace_root, port=11435)`, `RunLimits(ram_mib, gpu_mib, cpu_threads, timeout_seconds)`, `JobBinding(request_id, base_sha256, engine_sha256, preset_id, dataset_sha256, profile_sha256, limits)`, and `JobRecord(id, binding, state, phase, attempt_id, error_code, artifact_refs)`. Private record fields must not appear in diagnostic repr/log output. Use domain-specific categorical exceptions.

Proposed job states: queued, running, cancel-requested, completed, failed, cancelled, interrupted, needs-verification. Phases: prepare, train, export, import, probe, bundle. A completed workflow requires a real candidate, verified export/receipt, and successful inference probe; progress may be phase-only.

## Review focus

1. Stale profile/lesson edits while training is accepted: accepted bytes/hashes remain unchanged and editing revokes review. Covered by Tasks 2, 3 and 6.
2. GUI crash between artifact publication and DB acknowledgement: reconcile verified phases without a second optimizer launch; ambiguous imports are not success. Covered by Tasks 4 and 6.
3. An unrelated runtime owns a port or model store: leave it untouched, including during cancellation and shutdown. Covered by Task 4.
4. Long/non-ASCII profile text or hostile model output: enforce UTF-16/token limits, mask system/prompt loss, reject extra proposal fields, and never execute/render active content. Covered by Tasks 2, 3 and 5.
5. Packaging/path/memory failures: spaces and non-ASCII paths, missing CUDA, commit pressure, disk exhaustion, and unavailable readings stay truthful and do not trigger downloads or broad cleanup. Covered by Tasks 1, 4, 7 and 8.

## Task 1: Native window, isolated GUI environment and application contracts

**Files:** Create package entry points, `domain.py` record definitions, `ui/window.py`, GUI/build requirements, and `tests/test_window.py`.

**Interfaces:** `create_window(controller: object) -> QMainWindow` accepts a controller interface; `main(argv: list[str] | None = None) -> int` owns startup/shutdown. Define records above without importing torch/transformers. The shell exposes Teach, Job & personality, Lessons, Versions, Advanced and setup-required states.

- [ ] Write `test_window.py` using Qt Test: application opens a `QMainWindow`; Teach is initially selected; Advanced is reachable; unavailable actions are disabled with an explanation; no browser, subprocess or network action occurs merely by creating the window. Importing the package must not import torch.
- [ ] Create the separate GUI venv with the existing Python 3.12 installation, then install the pinned official GUI/build requirements there. Run `pip check`. This is an implementation step for tomorrow, not tonight; preserve the training/voice environments.
- [ ] Run `QT_QPA_PLATFORM=offscreen` with the GUI interpreter and `-B -m unittest tools.aether_teacher.tests.test_window`; observe the missing implementation failure before building the shell.
- [ ] Implement the native shell/records and run that command to completion. Open the native window once on Windows to verify layout, keyboard focus, close behavior and scaling; do not describe the HTML preview as this evidence.
- [ ] Commit only this task's files.

## Task 2: Private state, profiles, sessions and reviewed snapshots

**Files:** Implement `domain.py`, `store.py`; create `tests/test_domain.py` and `tests/test_store.py`.

**Interfaces:** `compile_profile(profile: Profile) -> str`; `snapshot(profile: Profile, lessons: tuple[Lesson, ...], preset_id: str) -> Snapshot`; `bounded_context(profile: Profile, turns: tuple[dict, ...], message: str) -> tuple[dict, ...]`. `Store(path: Path)` provides revision-checked profile/lesson edits, review, bounded session persistence, `accept_job(binding, snapshot) -> JobRecord`, `claim_phase(job_id, expected_phase) -> bool`, `cancel_job(job_id) -> JobRecord`, and `get_job(job_id) -> JobRecord | None`.

- [ ] Write tests with exact boundaries: a 1000-unit field passes and 1001 fails, including astral characters; lone surrogates fail. A reviewed lesson with a newer revision is rejected. Editing clears `reviewed_revision`. Snapshots use only selected reviewed pairs, the original ordered item keys `{id,revision,prompt,target}`, and exact UTF-8 SHA-256. A changed profile hash differs even when dataset bytes do not.
- [ ] Test SQLite restart/replay: identical UUID/binding returns the same ID; conflicting binding rejects; cancelled-before-claim never launches. Simulate profile/lesson changes after acceptance and assert accepted bytes/hashes are identical. Test each transcript/database cap, dirty transaction rollback and omitted context retaining stored turns.
- [ ] Run both test modules, confirm failures, implement the pure domain/store and migrations with FULL durability/finite page count, then rerun. Store calls and file hashing will execute off the GUI thread through a bounded serialized worker.
- [ ] Add setup-required display for unreadable/private path failures without changing ACLs. Reject linked/UNC workspace roots and path traversal; keep generated data outside source control.
- [ ] Commit the domain/store slice.

## Task 3: Narrow CLI integration for private runtime/state and profile-aware training

**Files:** Modify `tools/aether/laptop.py`, `tools/aether/test_laptop.py`, `aether-server/src/main/resources/training/trainer.py`; add profile regressions to its Python tests.

**Interfaces:** Add global local-CLI `--state-directory` and `--runtime-port` (choices 11434/11435, default 11434). State override defaults to the existing `STATE`; explicit GUI state owns lock/staging. Add train `--profile-snapshot` paired with `--profile-sha256`, both absent by default. Strict snapshot keys: `{schemaVersion:"1.0",profileId:UUID,revision:positive integer,systemPrompt:string}`. Extend `chat_sample(tokenizer, prompt, target, system_prompt: str | None = None)` and local provenance with optional `profileSha256`. No peer-schema change.

- [ ] Write regressions that reject non-allowlisted ports, linked/overlapping state, mismatched profile bytes, duplicate/extra snapshot members and a partially supplied flag pair. Existing CLI invocations must retain current defaults; state override must also reach lock/staging/recovery, not just final outputs.
- [ ] Test system+user+assistant rendering and prompt/system masking using a deterministic tokenizer fixture; test context overflow including the system instruction and the context-dependent BPE suffix case. Assert omitted profile flags produce the original samples/metadata and hosted mode still enforces Linux/cgroups and cannot request local QLoRA.
- [ ] Observe failing regressions, implement the optional local extensions, then run the targeted laptop/host policy tests. Include profile hash in candidate validation, export provenance and receipts without weakening existing base/dataset/Modelfile binding.
- [ ] Run `:aether-server:test :aether-server:verifyStandalone '-PcodexBuildDir=.codex-build' --no-parallel` after the trainer changes; only the main agent owns this Gradle invocation. Record the real-artifact environment/skip status honestly.
- [ ] Commit this backward-compatible engine extension.

## Task 4: Verified setup, private Ollama and owned process supervision

**Files:** Create `setup.py`, `engine.py`, `runtime.py`, engine-manifest build support and `tests/test_setup.py`, `tests/test_engine.py`, `tests/test_runtime.py`.

**Interfaces:** `SetupProbe.inspect(settings) -> dict`; `EngineRunner.start(operation: str, binding: JobBinding, paths: dict) -> str` returns an owned operation ID and emits bounded phase/result/error signals; `EngineRunner.cancel(operation_id) -> None`. `ManagedRuntime.ensure_started()`, `stop()`, `import_source(source_tag: str)` own only their recorded service/store. Expected engine files are laptop.py, local_process.py, gguf_import.py, merge_export.py, laptop-requirements.txt and trainer.py, bound by a build-generated manifest.

- [ ] Write fixtures for modified engine files, absent interpreter/packages, engine path with spaces/non-ASCII characters, port 11435 already occupied, wrong source digest/license/template, symlink/UNC paths and insufficient space on either source/output volume. Setup must remain usable when training is unavailable and must perform no automatic installation/download.
- [ ] Test environment filtering/fixed loopback, operation allowlist, unknown operation rejection, bounded JSON/output and a wrong response type. Use tiny owned subprocesses to test real Windows job closure and forced GUI-owner death; assert an unrelated listener at 11434 survives. Never run Gradle/model processes from these tests.
- [ ] Observe failures; implement QProcess/worker supervision with OS Job Objects, readiness deadlines, private source copy/hash verification, no shared writable store and no arbitrary shell construction. Keep heavy probes/copies off the UI thread. Record actual owned PIDs/create-times; cancellation cannot target an unrelated/reused PID.
- [ ] Run these modules and a lightweight native setup test. Build the known engine manifest into generated local output; bind a trusted manifest resource in the GUI package. Store registered paths locally, not in exported artifacts.
- [ ] Commit setup/runtime ownership.

## Task 5: Real teaching chat, saved profiles and lesson correction UI

**Files:** Create `chat.py`, `ui/teaching.py`, `ui/profile.py`, `ui/lessons.py`; extend window/controllers; add corresponding unit/Qt tests.

**Interfaces:** `ChatClient.send(model_id: str, profile: Profile, context: tuple[dict, ...]) -> str` is asynchronous/cancellable; one request at a time, fixed private runtime, `stream:false`, <=90s deadline and <=128 KiB response. `propose_profile(profile, context) -> dict` returns only a revision-bound patch of job/personality/boundaries. `correct_answer(question_id: str, target: str) -> Lesson` creates a draft through Store. UI views never build trainer commands.

- [ ] Write tests for the next chat using the newly saved profile without optimizer activity; stale proposals, unknown patch fields, overlong/invalid Unicode proposals and active HTML/links must not apply or execute. A draft action cannot trigger Save, review, training or model selection by model text alone.
- [ ] Test a selected correction containing only its chosen question/answer; no other messages become lessons. An edited reviewed example becomes draft. Include malformed/truncated/oversize/timeout chat responses and unavailable runtime states; stored prior messages remain intact and no placeholder reply is called a model success.
- [ ] Observe failures; implement real bounded Qt networking, context selection, plain-text output, draft diff/review, direct profile editing and correction/review actions. A dedicated bounded storage worker marshals DB operations back to UI signals; do not block the event loop.
- [ ] Run pure/Qt tests and, once the private setup is ready, one real local chat showing that Save profile changes subsequent instructions. This is inference only; no training is necessary to validate the profile flow.
- [ ] Commit the real teaching slice.

## Task 6: Durable training workflow, cancellation and recovery

**Files:** Create `jobs.py`, progress/review UI, `tests/test_jobs.py`; extend Store/EngineRunner only where this task's interfaces require it.

**Interfaces:** `JobCoordinator.submit(request_id, profile_id, lesson_refs, preset_id, limits) -> JobRecord`, `cancel(job_id) -> JobRecord`, `recover() -> tuple[JobRecord,...]`. Use the fixed phase sequence and immutable Store acceptance/claim APIs. `EngineRunner` maps phases to existing commands plus the optional profile/state/runtime flags. Reject a second distinct unfinished workflow as busy; replay of the same accepted request is not a second job.

- [ ] Write state-machine tests for double-click/lost acknowledgement, conflicting payload, cancellation before acceptance/claim, cancellation versus verified publication, profile changes after acceptance, and native failure. Assert no ready-to-test status without actual artifact verification, runtime receipt and inference probe.
- [ ] Inject crashes before launch, after claim, after candidate publication and after import before receipt. Recovery must reuse proven completed phases, never auto-replay a claimed optimizer, and quarantine ambiguous imports. Explicit optimizer retry after a missing candidate gets a new training identity; retrying later phases does not retrain.
- [ ] Observe failures; implement the durable coordinator, worker lease, truthful phase/elapsed UI, bounded diagnostic display, safe staging maintenance and Stay/Cancel-and-exit behavior. Stop the app-owned runtime before training; app-owned conversion cancellation terminates its service tree. Preserve draft editing while a job runs.
- [ ] Run state/lifecycle tests. Gate real model work on disk/hardware admission; lack of space is an environment prerequisite, not evidence of broken source. Validate the managed 32768 MiB runtime ceiling in Task 8 before claiming successful GUI conversion.
- [ ] Commit durable training orchestration.

## Task 7: Advanced workspace, comparison, rollback and verified export

**Files:** Create `ui/advanced.py`, `ui/versions.py`, `exports.py` and corresponding tests.

**Interfaces:** Advanced invokes the existing controller methods and reads `RunLimits`/JobRecord/engine metadata. `compare(left_version_id, right_version_id, profile_revision, prompts) -> tuple[dict,...]` runs sequential requests with identical profile/questions. `select_version(version_id, expected_selection_revision) -> None` updates only the app selection after verification. `export_version(version_id, profile_revision, destination: Path) -> Path` produces a new outer teacher-export package.

- [ ] Write tests that Simple and Advanced reject the same unsupported preset/budget; distinguish observed resource values from ceilings and unknowns; ensure no command shell or unrestricted LR/rank editor appears. Preserve exact configuration inspection without logging lesson/profile bodies.
- [ ] Test comparison using the identical profile hash/prompts for both models, verified explicit selection, paired configuration rollback and missing-artifact refusal. No automatic quality score or production action is emitted.
- [ ] Test export corruption, extra files, profile/model mismatch, path overlap/UNC/reparse points and destination exhaustion. Outer export contains the unchanged verified inner model bundle, `profile.json` and a full manifest; it excludes transcripts, lesson text, snapshots, credentials and machine-specific paths. Stage on the destination volume and atomically publish a new directory only after verifying every file.
- [ ] Observe failures; implement the Advanced tabs (Training setup, Resources, Diagnostics, Artifacts), same-state controls, Versions and export worker. Include clear operator instructions that profile JSON does not automatically configure the current gateway or rewrite canon.
- [ ] Run these tests plus the existing engine receipt/manifest regressions, then commit.

## Task 8: Windows package and real acceptance evidence

**Files:** Complete packaging scripts/spec, third-party notices, `tests/test_package.py`, `docs/ai/aether-teacher-desktop.md` and a dated evidence record.

**Interfaces:** Build `AetherTeacher.exe` as a Windows onedir/no-console application in `.codex-build/aether-teacher/dist/AetherTeacher/`, with trusted engine manifest/resources and GUI dependencies only. The CUDA environment/weights stay external and private. `python -m tools.aether_teacher.packaging.build` is the proposed reproducible build entry point.

- [ ] Write packaging assertions that required Qt platform plugins/resources/manifest exist, no torch/transformers/models/private workspace/secrets are bundled, and an altered engine is rejected. Include clean setup-required startup when the configured interpreter or source base is missing.
- [ ] Run all desktop unit/Qt tests with the GUI interpreter, training policy regressions with the CUDA interpreter, and existing local process/import/merge regressions appropriate to actual engine changes. Broaden checks only for new changes or unresolved concerns; do not run Minecraft or a full mod build for desktop code.
- [ ] Once storage admission passes, verify an actual small profile-aware CUDA optimization, finite changed/reloaded adapter and exact profile hash. Verify actual import/conversion in the managed runtime budget, the saved-profile chat, candidate comparison/rollback and a complete outer export. Use synthetic reviewed examples and record measurements; never use production conversations.
- [ ] Build and launch the actual executable from its output folder/shortcut, outside the source working directory. Verify startup/setup/navigation, native UI responsiveness, display scaling, background chat/job behavior and close/cancel/restart. Capture native desktop screenshots and results; browser mockups do not satisfy this acceptance.
- [ ] Request final independent code review, resolve important findings, review the complete focused diff for secrets/unrelated changes, then commit the final delivery/docs. Report the actual commit, executable path, evidence, remaining runtime/host limitations and manual deployment boundary. Do not push/deploy/activate automatically.

## Plan self-review and morning handoff

Spec coverage: everyday teaching/profile/review is Tasks 1/2/5; optional profile training is 3; engine discovery, private runtime and ownership is 4; durability/cancellation/recovery is 2/6; Advanced/comparison/export is 7; real native packaging/acceptance is 8. Every Review Focus item has an explicit regression above. No model/GUI checks were claimed as run during plan preparation.

Implementation is intentionally staged so the first usable native window and real chat can be built before disk-heavy training. Morning sequence: confirm current checkout/storage, create only the isolated GUI environment, implement Tasks 1–2, then the engine/runtime/chat slices. Address storage with the owner before Task 8's large run; inspection and a proposed cleanup list are safe, deleting existing artifacts is not part of this plan's preparation authorization.

The preview remains an illustrative asset under `.codex-build/aether-teacher-design/`. Its local web server can expire or be stopped overnight; recreate a preview only if needed. The real application work begins after the user returns and asks to start. Use this plan and the design as the handoff, rather than relying on chat context or leaving training running overnight.
