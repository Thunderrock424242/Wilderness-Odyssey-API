# Aether Teacher Qwen acceptance — 2026-11-04

The owner requested Qwen 3.5 4B as the app's only base and authorized removal of app-owned Llama weights/test artifacts after successful replacement. This record supersedes the model choice in the [historical Llama acceptance](aether-teacher-acceptance-2026-11-04.md). It does not supersede the gateway's pinned peer contracts or authorize production model activation.

## Source and runtime

| Item | Verified identity/result |
| --- | --- |
| Official source | `Qwen/Qwen3.5-4B`, revision `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` |
| License | Official Apache 2.0 license retained; raw official and Ollama LF-normalized license hashes recorded separately |
| Prepared text base | 426 tensors, 8,411,510,272 weight bytes; every saved tensor exactly equals its official source tensor |
| Explicit exclusions | 297 vision tensors, 15 MTP tensors; no image/video teaching |
| Approved base SHA256 | `bb535686a6ca9ca79f1c6190ec9964932a36ded761880ecbe3ba527f1b6d5649` |
| Converter | llama.cpp `b12110`, commit `cd959bae99ff4bed22bef2604f45241c09bbb8c8`; source and Windows CPU binary archives SHA-pinned |
| Converter manifest SHA256 | `0ca6d32267d2524a2b822a12953d4b47a096ae022a63dca02cbd6a86aea0fd51` |
| Baseline GGUF | Q4_K_M, 2,708,804,096 bytes; SHA256 `c74846553aea7923b4b2ade3394ed2ddd7a6bbdf56058247f727db6a3b13d38c` |
| Actual runtime | Existing Ollama 0.17.7; no global runtime upgrade |
| Baseline inference | Real response `QWEN_READY` through the private managed runtime |

Source/tool pins are checked in `tools/aether/qwen35-source.json`. Acquisition is an explicit operator action. Offline `prepare` and `convert` run inside owned Windows jobs with finite RAM, disk, process and time limits. Each extracted converter file is checked against the inventories derived from the pinned archives before execution. Runtime jobs never download weights or execute model-provided code.

The preparation worker peaked at 5,446.5 MiB observed RSS. Baseline conversion took 180.563 seconds and peaked at 11,039.6 MiB observed RSS. An initial 8 GiB conversion budget correctly stopped the operation; the admitted Qwen export budget is 16 GiB. Verbose converter diagnostics are consumed under a 16 MiB total/32 KiB retained-tail cap instead of flooding the UI's 64 KiB response channel.

## Actual native training workflow

Job `9e2b8d04-025b-4105-b607-73fdbb1e5c14` used eight synthetic, explicitly reviewed lessons and a saved short job/personality/boundaries profile. The same immutable dataset/profile hashes were checked through training, merged export, runtime receipt, probe, bundle and handoff. No production conversation was used.

| Check | Observed result |
| --- | --- |
| Preset | `laptop-qlora-smoke`: 8 actual steps, 256-token context, rank 4, alpha 8, LR 0.0002, batch 1, NF4, two CPU threads |
| Training time | 31.328 seconds |
| Final loss | `0.00014705327339470387`, finite; synthetic pipeline evidence only |
| Candidate | Changed weights; saved adapter reloaded and all tensors finite |
| Adapter SHA256 | `69256477117a2264b1a3719ed1b281859ceae740fefea818da7a6cafcd31f329` |
| Observed training RSS | 8,853.4 MiB |
| Peak allocated GPU memory | 5,401.6 MiB |
| Thinking | Explicitly disabled in the real training template and runtime requests |
| Merge | 426 standalone tensors; 496 adapter tensors consumed and verified |
| Candidate GGUF | 2,708,804,096 bytes, Q4_K_M; SHA256 `9a15537415a008c3e756b712bec5bb440e32d6ada95b6bf12841972e9474a57d` |
| Runtime import | Actual GGUF hash, source template, rendered runtime Modelfile and merged package bound by receipt 1.1 |
| Native workflow | Prepare, train, merge/convert, import, real inference probe, bundle and explicit version selection passed |
| Comparison | Same saved profile/question: base answered `My job is to help players.`; candidate answered `I help players.` |
| Rollback | Returned explicitly to `qwen3.5:4b` |
| Handoff | Outer inventory, inner portable GGUF package and matching profile verified |
| End-to-end duration | 461.156 seconds |

The laptop is Windows 11 x64, RTX 4090 Laptop GPU (16,375 MiB reported), approximately 32 GiB RAM. GUI and training use separate official CPython 3.12.10 environments. Training reuses the existing pinned Torch 2.11.0+cu128, Transformers 5.16.1, PEFT 0.18.1, bitsandbytes 0.50.2, psutil 7.2.2 and gguf 0.19.0 libraries. Do not remove the shared `.codex-build/aether-laptop/.venv`.

## Numerical verification

The real 4B candidate was checked in sequential bounded processes against PEFT 0.18.1's full-model CPU `merge_and_unload(safe_merge=True)`. All **426 standalone tensors exactly matched** that reference, including the tied embedding policy. The streamed model and the full PEFT merged model then produced **exactly equal GPU logits** across 993,280 elements (maximum absolute difference 0).

The initial unmerged-adapter versus FP16-merged comparison did not pass a provisional `rtol=0.01, atol=0.03`: maximum logit difference 0.0390625, mean 0.00356806. This observation is retained rather than silently raising the tolerance. A GPU merge reference also showed four elements in one examined tensor differing from the CPU result by one FP16 step (7.62939453125e-06). The stronger full CPU reference check resolved the exporter question with exact tensor/output equality; no model or merge implementation was changed to make this check pass. Forward passes used four fixed valid token IDs and eager attention; this establishes numerical merge correctness for the check, not broad behavioral quality.

The actual official tokenizer's generation text exactly matched the runtime template for both a system/user exchange and multi-turn user/assistant history. The real completion-only training split retained the closed empty thinking prefix and began its target at the answer text. No-thinking behavior was explicitly supplied rather than relying on a model default.

A fresh native process restored the persisted candidate `aether-candidate:teacher-9e2b8d04-025b-4105-b607-73fdbb1e5c14` and the subsequently edited profile revision 3. It produced the real reply `I am here to help you clearly and briefly.` in a 21.203-second startup/chat check. No optimizer was replayed.

## Installed delivery and authorized cleanup

The owned source installation under `%LOCALAPPDATA%/Programs/AetherTeacher` and its existing desktop shortcut were updated to Qwen. Before changing settings/source, the daily SQLite database, private settings and prior installed source were backed up under the ignored `.codex-build/aether-qwen/daily-backup`. The app's exclusive workspace lease and absence of active daily training jobs were checked before installation.

An actual installed-source Windows UI process connected the separate daily Qwen store and produced `I am here to help you with any questions or tasks you need!`. The check took 18.688 seconds. All existing profile/lesson/message/job/session counts were preserved; only its explicitly created temporary verification session was removed afterward. The synthetic training workspace/candidate was not copied into the daily workspace or activated there.

Private daily setup (all paths relative to the repository unless stated):

| Setting | Required local value |
| --- | --- |
| Engine root | This reviewed repository checkout |
| GUI interpreter | `.codex-build/aether-teacher/.venv-cpython/Scripts/pythonw.exe` |
| Training interpreter | `.codex-build/aether-teacher/.venv-training-cpython/Scripts/python.exe` |
| Shared training libraries | `.codex-build/aether-laptop/.venv/Lib/site-packages` |
| Ollama executable | Existing `%LOCALAPPDATA%/Programs/Ollama/ollama.exe` |
| Prepared source store | `.codex-build/aether-qwen/source-workspace/runtime/qwen35-models` |
| Training base / approval | `.codex-build/aether-qwen/base` / `.codex-build/aether-qwen/base-approval.json` |
| Converter manifest | `.codex-build/aether-qwen/toolchain/toolchain.json`; preserve its adjacent source/bin and sibling downloads directory |
| Daily private workspace | `%LOCALAPPDATA%/WildernessOdyssey/AetherTeacher` |
| Managed runtime | Loopback 11435; `<workspace>/runtime/qwen35-models`, private home; environment listed in the desktop guide |

After full pipeline, exact numerical merge, restart and daily-chat gates passed, the following resolved allowlisted paths were checked for links, overlap and active process/runtime references, then removed:

- Daily `<workspace>/runtime/models` (the old Llama store).
- `.codex-build/aether-teacher/acceptance` (synthetic Llama verification workspace/exports).
- `.codex-build/aether-laptop/models/llama31-8b-ollama-667b0c1932bc`.
- `.codex-build/aether-laptop/candidates/llama31-ollama-qlora-001`.
- `.codex-build/aether-laptop/exports/llama31-ollama-qlora-002`.
- `.codex-build/aether-laptop/deployment/llama31-ollama-qlora-002`.
- The old `llama31-ollama-base.json` approval and ten explicitly inventoried `llama31-*` result JSON files in that local directory.

Actual deletion total: **77,844,295,384 bytes (about 72.5 GiB)**. The ignored cleanup receipt records each absolute path, file count and byte count. The original user's `~/.ollama/models` was excluded; its Llama manifest hash was unchanged after cleanup. Shared training libraries and unrelated small/hosted test assets were retained. Historical checked-in evidence remains historical; it is not an active model selection.

The installed app also passed a fresh real Qwen chat after cleanup (14.531 seconds), with existing record counts still preserved. Final diff review then tightened the export disk counter to include the whole operation directory, including temporary converter files outside the publication directory. A small owned-worker regression reproduced the omission before the fix and verified refusal to publish afterward, without allocating large files. The reviewed engine manifest was updated in both repository and installation. This changes the engine fingerprint: historical acceptance jobs retain their original immutable bindings and are not relabelled or automatically resumed under the final fingerprint. Model tensors and training/conversion algorithms were unchanged by this final guard.

## Checks and boundaries

- Desktop regression suite: 52 tests passed, including Qwen-only chat/store admission, preserved teaching records, old-job quarantine, cancellation, durable idempotency, restoration and native window checks.
- Local Qwen/laptop suite: 32 tests passed, including a real tiny hybrid-attention Qwen PEFT/streaming-merge numerical regression and staged receipt finalization.
- Final expanded local engine/lifecycle/compatibility run after cleanup and the final disk guard: 39 tests run, 37 passed, 2 legacy explicit-weight comparisons skipped because their external verification inputs were not supplied. The real Qwen numerical and actual full 4B reference checks above did run; they were not skipped.
- Hosted trainer Python policy: 4 tests passed. Standalone Aether server Gradle test task: 77 tests passed. Native health/admission and peer contracts were preserved.
- Independent read-only review found one Important staged receipt overwrite issue; it was corrected and covered by a regression. No remaining Critical/Important findings were reported.

An observed Qt test timeout was isolated to the native test wait retaining Python's GIL; a 1 ms yield in the test harness lets the storage worker run without changing production timing or persistence. Tests that use loopback 11435 must run while the model service is stopped.

The native source launcher and existing desktop shortcut remain the working delivery. The earlier frozen `.exe` Qt missing-procedure issue remains unresolved; this migration does not claim a portable executable or clean-machine dependency installer.

## Hosting and deployment

No remote deployment or model activation was performed. The 2.71 GB artifact is smaller than the earlier 8B artifact; that file size does not establish that Qwen and the Minecraft modpack fit an 8 GB hosting allocation. CPU-only inference capacity, concurrent Minecraft load, native service readiness and gateway admission still require measurement on the intended host.

Transfer only the reviewed outer handoff. Verify `teacher-export.json` and inner `model/package.json`, import `model/Modelfile` into a compatible private Ollama service, review the gateway's finite model allowlist/native commissioning, and map `profile.json` to gateway-owned instructions explicitly. Training remains on the laptop. Never automatically activate the synthetic acceptance candidate.

Private inference-host starting settings, subject to actual admission, are:

```dotenv
OLLAMA_HOST=127.0.0.1:11434
OLLAMA_MODELS=/srv/aether/ollama-models
OLLAMA_CONTEXT_LENGTH=2048
OLLAMA_NUM_PARALLEL=1
OLLAMA_MAX_LOADED_MODELS=1
OLLAMA_MAX_QUEUE=8
OLLAMA_NO_CLOUD=1
```

Keep the model directory persistent/private and align the gateway's reviewed `ollama.context_tokens` with the native inference limit. The host origin, server ID and separate monitoring/admin credentials must come from the operator's existing private deployment; no credentials are needed for this laptop app. See the existing [operator deployment workflow](aether-laptop-training.md) for the authoritative gateway boundary. Website/gateway/bot contracts were not redesigned for this migration.
