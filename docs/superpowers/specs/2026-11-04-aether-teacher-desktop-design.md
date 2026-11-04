# Aether Teacher desktop design

Status: desktop direction and simple/Advanced split accepted for preparation. The user explicitly deferred implementation until they return in the morning. Technical proposals below are recorded for review with the implementation plan; do not begin building overnight.

## Intent and agreed direction

Make teaching Aether practical on the owner's existing Windows laptop: talk about its job, refine its personality, correct answers, review lessons, train a candidate, and compare versions without operating PowerShell or editing training JSON by hand.

The user selected a standalone desktop application rather than a local browser application. They approved the simple, conversational mockup and asked for an Advanced tab that feels like a technical desktop tool. The browser companion is an illustrative design preview only; the shipped UI will be a native application window and shortcut.

The following are design proposals, not additional user decisions: Python/PySide6, the storage and ownership model below, Aether-only first-release scope, bounded presets instead of arbitrary hyperparameters, a managed private Ollama instance, and a portable Windows application folder.

## Approaches considered

1. Native Windows desktop UI wrapping the existing local Python pipeline. Own window, native dialogs, background processes, and a shortcut. Requires Windows packaging and a separate training environment. **Selected desktop direction; proposed implementation is PySide6 Widgets.**
2. Local browser dashboard wrapping the same engine. Could reuse more playground UI ideas and would be easier to update, but the user chose a desktop application.

An embedded browser/Electron shell is unnecessary for the first release. PySide6 provides Python bindings for Qt and supports desktop deployment; use its supported process/network facilities rather than freezing the UI during model work. See [Qt for Python](https://doc.qt.io/qtforpython-6/), [deployment guidance](https://doc.qt.io/qtforpython-6/deployment/index.html), and [QProcess](https://doc.qt.io/qtforpython-6/PySide6/QtCore/QProcess.html).

## Existing foundation and constraints

- `tools/aether/laptop.py`, `gguf_import.py`, `merge_export.py`, and `local_process.py` implement the verified offline import, QLoRA, safe merge, runtime import, receipt binding, and portable GGUF bundle workflow. Reuse this engine; do not introduce a competing trainer.
- Llama 3.1 8B is the selected base. This laptop has a 16 GiB RTX 4090 Laptop GPU and about 32 GiB RAM. Training, export and runtime conversion must be serialized; Ollama conversion was observed near 20 GiB server RSS.
- Training consumes an explicitly reviewed, literal UTF-8 snapshot with hashes. An ordinary conversation is never automatically an approved dataset.
- The native gateway owns deployed canon/prompts. Its main adapter supports `ai.pause`; this app must not invent profile/model activation endpoints or edit production configuration.
- Preserve the frozen peer contracts at `60a962005e1684610da1b6588e63b6fc195f0fd5`, native health/admission behavior, and hosted Linux/cgroup admission. Desktop features are a separate local operator workflow.
- The current CLI assumes a repository engine layout and a runtime at loopback port 11434. Desktop packaging must explicitly address engine discovery and a private runtime endpoint; an executable alone does not remove these dependencies.

## First-release scope

One Windows owner and one local Aether workspace, with versioned profiles, teaching sessions, reviewed lessons, local candidate jobs, comparisons, and export. Multiple profile revisions are supported; a suite of independently managed specialist personalities is deferred.

### Everyday workspace

**Teach Aether** is the opening page. Chat with the chosen local model, explain the desired job/tone, try questions, and request changes. Responses are real local inference. A separate draft action can propose changes to bounded profile fields. Proposed text is shown as a diff; only the owner's Save profile action commits it. Model-generated proposals cannot choose paths, run commands, approve lessons, start jobs, or select models.

**Job & personality** edits job, personality/tone, and response boundaries. Each field is at most 1000 UTF-16 units. The deterministic compiled system instruction is at most 4000 UTF-16 units. Store immutable profile revisions and one active-profile pointer. Saving changes affects subsequent app conversations immediately and creates no training job. It does not mutate the underlying model, its Ollama Modelfile, project lore, or the deployed gateway.

**Lessons** turns a selected question and corrected answer into a draft. Nothing else from that conversation enters the lesson. The owner can edit, delete, and explicitly review lessons; editing a reviewed lesson removes its approval. Approve selected lessons in one batch, inspect the exact proposed training batch, and press Train new version. Retain the existing item fields and byte/character/context limits. No JSON editing is required.

**Versions** records the trained artifact, base/dataset/profile/engine hashes, runtime import receipt, and evaluation results. Compare two model versions on the same questions and same chosen profile revision. The owner explicitly chooses which candidate the app uses. Keep the prior selection for rollback. Restoring a version restores its recorded app configuration; it does not alter Minecraft or a remote server.

**Training review/progress** is a shared job screen reached from Lessons and Advanced. Show the selected lessons, profile, base, preset, and resource limits before acceptance. Show measured phase, elapsed time, cancellation state, and real errors. Use phase-only progress where the engine has no measured percentage; never invent optimizer percentages, quality scores or success. A completed training phase requires the real verified adapter. A ready-to-test version additionally requires a standalone export, actual runtime import receipt and a successful bounded inference probe.

### Advanced tab

The Advanced tab exposes the same underlying workspace and job state, with a denser technical presentation:

- Model/runtime inventory: exact installed model ID, base fingerprint, approved engine/interpreter versions, source quantization, managed runtime address and readiness. Paths remain local to the owner.
- Training setup: choose approved `laptop-qlora-smoke` or `laptop-qlora-small`, inspect resolved steps/context/example limit/LR/rank/alpha/precision, and choose bounded resource/deadline settings. Rank, alpha, LR and sequence length are preset-owned in the first release, not unrestricted editors. `laptop-lora-smoke` is shown only when its actual base/budget admission supports it.
- Resources: actual NVIDIA device/free VRAM, available RAM, Windows committed-memory/job limits, disk estimate and live owned-process RSS when measured. Clearly distinguish configured ceilings from observed values and unavailable readings.
- Jobs and diagnostics: current phase, accepted immutable configuration, cancellation, sanitized error categories/stack locations and bounded operational logs. There is no command shell or arbitrary trainer-script field.
- Artifacts: adapter/base/dataset/profile hashes, manifest checks, runtime receipt, candidate lineage, storage locations and export validation.
- Comparisons: the exact shared test prompts, profile revision and both model answers. No fabricated automatic quality grade.

Simple and Advanced views must use the same validated actions, not two implementations with different limits or authorization.

## Local runtime and process ownership

**Proposed first-release isolation:** the app manages its own Ollama instance at `127.0.0.1:11435` with a private model store under the workspace. It never takes over port 11434 or stops an existing user/Minecraft runtime. If port 11435 is occupied, report the conflict and leave that process alone.

Setup explicitly imports the already installed source model into the private store by copying and verifying the required manifest and content-addressed model/license/template/parameter blobs. Preserve the source hashes; no model download is needed. Do not share a writable Ollama store between independent servers, and do not depend on another server's pruning or lifecycle. An unchanged source blob may be reused within the app's own verified store.

The existing CLI gains a local-only validated runtime-port option restricted to 11434/11435, defaulting to 11434 for compatibility. It also gains an optional private state-directory override, preserving the existing repository-local default when absent; GUI-owned staging and locks must not leak back into the engine checkout. The GUI selects 11435 and supplies its private store/state root. No arbitrary host, URL or remote proxy option is added. The app talks directly to the private loopback service; it does not expose a browser HTTP frontend.

The app owns its service and all launched CLI descendants through Windows kill-on-close Job Objects. Keep the UI out of the training process tree. Serialize model preparation, training, export and runtime conversion under one workspace lease and the engine lock. Before training, finish/cancel chat and stop the app-owned runtime to release its memory. Restart it for import/testing afterward. The user can still edit profile/lesson drafts while a job runs; those edits cannot change the accepted snapshot.

Use finite, separately recorded budgets: default training 24576 MiB committed-memory/RSS ceiling and 11000 MiB GPU allocation budget, export 8192 MiB, two training CPU threads; smoke training deadline 600 seconds, small preset up to 3600 seconds, export/runtime import up to 1800 seconds. The managed Ollama tree has a separately configurable bounded committed-memory ceiling, proposed 32768 MiB, because its conversion footprint differs from the CLI client. Verify this ceiling on the real laptop before claiming GUI import works. Disk admission accounts for all used volumes and stages, with a conservative 60 GiB free-space floor for a new complete run and a larger model-specific estimate when needed; the floor alone is not proof a run fits.

Closing the app during work offers Stay or Cancel and exit. The first release does not hide training in an unowned background service. Forced termination kills owned CLI/runtime trees; startup reconciles their recorded artifacts. Never change ACLs, antivirus, system pagefile/security settings, or unrelated processes to recover a run.

## Profile-aware training

The current local trainer applies the model chat template to user/assistant pairs but does not include the app's editable role. Extend the **local-only** CLI with an optional immutable profile-snapshot path and SHA-256. The snapshot contains the compiled system instruction and profile revision. Local training and local preview use the same instruction. The default CLI behavior without this option and the hosted worker's behavior remain unchanged.

Pin the accepted profile hash separately from the unchanged ordered dataset hash. Add it to local artifact/export provenance. Use completion-only loss; validate the exact rendered token count including the system instruction before training. A long profile may exceed the smoke preset's 256-token limit; show this and offer the supported larger preset instead of truncating or silently weakening limits. Model/proposed profile output is never itself an approval.

## Persistence and recovery

Use one private workspace under `%LOCALAPPDATA%/WildernessOdyssey/AetherTeacher`, with SQLite for profiles/revisions, teaching sessions, lessons/reviews, immutable accepted job bindings, phase claims, attempts, app model selections, and artifact/receipt references. Use transactional durable acceptance and unique request IDs. Repeated clicks or a lost UI acknowledgement return the existing job; conflicting payloads fail.

Keep bounded local transcripts independently of training: proposed first-release cap is 20 sessions, each at most 200 messages and 4 MiB of UTF-8 text, plus a 256 MiB database cap. Refuse additional persistence at capacity and offer explicit export/removal; do not silently delete reviewed material. Chat input is at most 4000 UTF-16 units, output at most 1024 tokens and 16000 characters. Send at most 12 messages including the system/current user message and at most 16000 input characters, trimming oldest complete conversation turns from inference context while retaining the saved transcript. Show when older context is omitted.

Commit a phase claim before launching it. On restart, never automatically rerun a claimed optimizer job. Validate complete artifacts against the recorded base, dataset, profile and engine binding; reconcile a successful published phase or mark the job Interrupted/Needs verification. Partial output remains private and is cleaned only within owned staging paths after validation. An explicit retry uses a recorded new attempt, reuses verified completed phases and cannot change its original accepted inputs. If training was interrupted without a valid candidate, starting optimization again requires a new explicit training action/identity. App-owned runtime import can be stopped and reconciled without touching other services; ambiguous unreceipted candidates are quarantined rather than called successful.

Cancellation is durable, including before launch. Cancelling during preparation/training/export stops owned work and never activates a candidate. Cancelling conversion also stops the app-owned runtime. Keep verified completed artifacts until explicit cleanup. Data and logs are private local files, never repository content or telemetry. Operational logs have finite rotation and omit prompts, targets, complete system instructions, credentials and raw library diagnostics. The chat/lesson UI is intentionally private content, distinct from diagnostics.

## Packaging and setup

Ship a native PySide6 Widgets application in a portable Windows folder with `AetherTeacher.exe`, required GUI libraries/resources and third-party notices. No browser window is required for daily use. The UI package does not bundle the multi-gigabyte model or CUDA PyTorch environment.

The initial target is this already prepared laptop. A first-run setup flow discovers or lets the owner select the approved existing training interpreter, engine location, original source Ollama store and base approval, verifies the known engine-file manifest and installed versions, and imports the source into its private store. The GUI remains usable for setup when CUDA/trainer is unavailable; training is honestly disabled with actionable details. There are no silent dependency/model downloads. A clean-machine dependency installer is a later scope, not a promise that the portable GUI contains the full training stack.

Proposed new source owner: `tools/aether_teacher/` for the GUI package, domain/store, local runtime/engine adapters, job coordinator, setup and packaging definitions, with mirrored tests. Extend feature-owned `tools/aether/` and the fixed trainer only for the narrow runtime-port/profile-snapshot integration. Do not place desktop imports in Java/Minecraft code. Precise module signatures belong in the implementation plan after design review.

## Export and deployment boundary

An explicit Export action produces a new staged outer package containing the existing verified model bundle, a versioned `profile.json`, and a teacher-export manifest binding both inventories to the selected model/profile. Do not append files to an already hashed inner model bundle. Exclude chat transcripts, lesson text, job snapshots, credentials and machine-specific paths.

First-release profile JSON is an operator handoff, not a new gateway API. The server does not already consume it. Include instructions for separately reviewing/mapping the profile to the gateway's authoritative configuration; never replace canon or unrelated specialists automatically. No automatic upload, production model activation, Minecraft state mutation, or production conversation ingestion is added. Website/bot integration and remote training control remain separate deployments under their pinned contracts.

## Acceptance and review focus

- Native Windows UI launches from its packaged shortcut, keeps input responsive during model/network/file work, and has no required browser window.
- Save profile creates a revision and affects the next real local reply without any optimizer launch. Proposed changes never apply themselves. Plain-text/safely rendered model output cannot execute code or open arbitrary resources.
- Draft lessons require explicit review; edits revoke it. The accepted dataset/profile/base/engine binding stays immutable through later edits, replay, cancellation and restart.
- Simple and Advanced views enforce identical preset/resource policy. GPU failure, context overflow, native crashes, disk exhaustion and missing dependencies show truthful failure/unavailable states.
- Profile-aware completion masking and token accounting have regressions. A real small CUDA run creates finite changed weights and records the exact profile hash; hosted policy/native health tests stay unchanged.
- GUI death/cancellation terminates its own processes and leaves an unrelated Ollama instance on 11434 untouched. Test the real Windows job lifecycle and interrupted-phase reconciliation; no duplicate optimizer run after recovery.
- Verify a real model import inside the managed runtime budget, actual chat with the saved profile, comparison/rollback, and a complete model+profile export with every hash checked. Mock progress is not acceptance evidence.
- After transfer, only an independently provisioned inference host can establish production readiness. The existing Kinetic allocation is not silently treated as adequate.

## Explicit first-release exclusions

Automatic learning from all chats; continual weight updates after each message; voice/microphone; multi-user accounts; cloud services; arbitrary scripts/URLs; unrestricted hyperparameter editors; background daemon training after app exit; automatic server deployment/activation; automatic rewriting of canon; and managing all Aether specialists at once.

These can be considered later without making the initial teaching program harder to use or weakening the already verified contracts.
