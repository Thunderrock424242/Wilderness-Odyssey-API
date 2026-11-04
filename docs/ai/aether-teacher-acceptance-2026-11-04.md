# Aether Teacher acceptance — 2026-11-04

## Scope and outcome

The native source-launched Windows application completed real local chat and the full reviewed-example training workflow on this laptop. Portable frozen packaging is **not complete**: PyInstaller can assemble the directory, but its actual executable launch fails importing QtWidgets with a DLL missing-procedure error. The working delivery uses an installed GUI interpreter/native source launcher and desktop shortcut. No browser is required.

All model examples in these checks were synthetic and explicitly reviewed for pipeline testing. No production conversations were collected, and no model was uploaded or activated on Minecraft, a gateway, Kinetic or another service. These checks establish a working pipeline, not improved gameplay knowledge or model quality.

The app was installed into the owner's local Programs folder and its desktop shortcut was created. An actual installed-source launch in the separate default private workspace reported `native:true`, `platform:windows`, five pages, `modelConnected:true` and a saved native screenshot. This new daily workspace starts with the original Llama model; it does not activate the synthetic test candidate or import the test conversations/lessons.

## Model and environment

| Item | Observed value |
| --- | --- |
| OS | Windows 11 x64 |
| GPU | NVIDIA GeForce RTX 4090 Laptop GPU; 16375 MiB reported total |
| System RAM | Approximately 32 GiB |
| Successful training interpreter | Official PSF CPython 3.12.10, project-local |
| Software stack | Torch 2.11.0+cu128; Transformers 5.16.1; PEFT 0.18.1; bitsandbytes 0.50.2 |
| GUI | PySide6 6.12.0; psutil 7.2.2; separate GUI environment |
| Source model | Installed Llama 3.1 8B Ollama Q4 source; local dequantized approved base |
| Approved base SHA256 | `be77d8e1b2846df686b316de25bd9bd33e9d06ee88d36b748fa91506751e45f7` |
| Original source GGUF SHA256 | `667b0c1932bc6ffc593ed1d03f895bf2dc8dc6df21db3042284a6f4416b06a29` |

Official diagnostic CPython package: `https://api.nuget.org/v3-flatcontainer/python/3.12.10/python.3.12.10.nupkg`, SHA256 `0eb85c2dfccccf1b17352de4c397f69194035b7d37149eacc16f1147d93de3b8`. Existing training libraries were reused through a private project-local `.pth`; the original environment was not modified.

## Real training and candidate

Job `0b1b002c-e000-49e8-bd8e-883c9eb80c3e` used eight synthetic reviewed lessons, `laptop-qlora-smoke`, eight actual optimization steps, 256-token context, NF4, rank 4, alpha 8, learning rate 0.0002, batch 1 and two CPU threads.

| Check | Actual result |
| --- | --- |
| Training duration | 37.515 seconds |
| Loss | 0.09506592899560928, finite |
| Changed weights | `true`; actual saved tensors were reloaded and checked finite |
| Peak GPU allocation | 8449.595703125 MiB |
| Peak observed training RSS | 15820.1484375 MiB |
| Training RAM/commit ceiling | 24576 MiB |
| Managed runtime memory ceiling | 32768 MiB; actual conversion/import completed under the Windows job limit |
| Complete acceptance duration | 551 seconds, including setup, export/import, inference, comparison, rollback and verified outer export |
| Adapter SHA256 | `964924f26c275fec4eaba10b7d530eb52745d5f20eef19587607be271c250fce` |
| Ordered dataset SHA256 | `ef89a585c9bbcf5bb4631acf007f94e949a3992709e03f35948692a09927929b` |
| Accepted profile SHA256 | `7b11b99ca27d0ed9e350f4060fcde3207940f959867039ba6777603a14dd6425` |
| Accepted engine SHA256 | `1949eb6b709e1dd828c09cf387144cb17df561a484ada0195af4e61d3f50ac82` |
| Standalone Q4_K_M GGUF | 4921247072 bytes |
| Standalone GGUF SHA256 | `af8d2595abfbf0161bf558f12945f83b86bfa55cc5273286d6ea2a3ca55e427c` |
| Merged manifest SHA256 | `f166985012dc3a25d187587f9b488adbfcda0f5a64c6e868160b660e0f781e8d` |
| Actual runtime template SHA256 | `89f217dbe5de4923c2b83bbe6f7af9ad29fad7b1a656b657eeccdd2fd48fd221` |

The imported candidate was `aether-candidate:teacher-0b1b002c-e000-49e8-bd8e-883c9eb80c3e`. A version-1.1 receipt binds its actual blob, merged manifest and source/runtime templates. The app performed a real bounded candidate inference probe, compared original/candidate answers with identical profile/questions, explicitly selected and rolled back the app model, and verified a complete outer model/profile handoff. No automatic quality score was assigned.

Private ignored evidence is under `.codex-build/aether-teacher/acceptance/`: `training-evidence.json`, the job's candidate/merged/receipt/probe/model-bundle, and `export-0b1b002c-e000-49e8-bd8e-883c9eb80c3e/`. Model and dataset files are not committed.

## Native chat, restart and lifecycle

The actual native window was shown/captured with Windows Qt, separately from the browser mockup. Saving two different profile instructions changed subsequent real Llama replies without starting an optimizer. This check completed in 26.5 seconds including setup.

After the independent review fixes, a separate app process explicitly selected the real candidate, saved an edited profile (revision 9), shut down, then reopened. Startup verified/restored the same candidate and preserved the edited profile before enabling chat. A real reply completed; the restart/inference acceptance took 30.594 seconds. Ignored evidence: `restart-evidence.json`, `native-final.png`.

Real Windows owned-process fixtures verified cancellation and forced supervisor death, including interpreter descendants; an unrelated listener survived. Durable state tests covered cancellation before claim, non-replayed optimizer claims, publication-before-acknowledgement recovery, quarantined ambiguous imports and base/engine identity drift. These are controlled process/state regressions; no claim is made that a full 8B optimizer was deliberately killed during this acceptance.

## Verification results

- Desktop suite: **50 tests passed**, including native entry from another working directory, spaces/non-ASCII paths, private state, session capacity/recovery, immutable snapshots, idempotency, early cancellation, restart selection, paired rollback, corrupt/overlapping exports and owned process lifecycle.
- Local engine/process regressions: **26 passed** using the successful CPython training interpreter.
- Hosted Python policy: **4 passed**; Linux/cgroup policy remains unchanged.
- Standalone server Gradle checks: **77 tests, 0 failures, 0 errors, 0 skipped**, plus `verifyStandalone`. The conditional real-artifact check used the existing real CPU optimizer artifact under `.codex-build/aether-laptop/hosted-core-smoke-final`; it did not substitute a fake GPU result or weaken artifact limits.
- `git diff --check`: no whitespace errors. No Java/Minecraft source behavior was changed for the desktop feature.

The independent reviewer identified seven Important lifecycle/recovery defects. Each was reproduced and fixed: exclusive app lease; cancellation through acceptance; restoring selected candidates; binding resumed work to accepted base/engine; explicit session capacity management; valid rollback pairs after profile edits; and cleanup even when diagnostic storage fails. Additional shutdown-during-DB-initialization and delayed-response cancellation regressions were fixed in the same pass.

## Limits and remaining work

- **Portable executable packaging remains unfinished.** Assembly success is not launch success. Native source/Python-GUI launch is the supported local delivery; do not publish the failing frozen output.
- The 100-step small preset has not been executed in this acceptance. Eight-step optimization and all subsequent artifact/runtime phases were real.
- Clean-machine training installation, other GPUs/operating systems and long teaching curricula are not established by these laptop checks. Owner evaluation with real reviewed examples is still needed.
- Qwen 3.5 4B is available for inference but is not yet supported by the reviewed converters. The owner's requested Llama fallback is retained; see [compatibility assessment](aether-teacher-model-compatibility.md).
- No remote server deployment or hosting-capacity acceptance was performed. Follow the [desktop/operator guide](aether-teacher-desktop.md) for the handoff boundary.
