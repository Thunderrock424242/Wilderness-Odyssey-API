# Optimization Classes to Delete After Nova Migration

Once Wilderness Odyssey API depends on Nova API, delete these overlapping classes from this repo and use the Nova API equivalents instead. Update imports/mixins to the `com.thunder.NovaAPI` packages before removal.

| Current path (to delete) | Nova API equivalent | Notes |
| --- | --- | --- |
| `chunk/*` | `com/thunder/NovaAPI/chunk/*` | Replace all chunk streaming, throttling, and stats types with Nova versions. |
| `async/*` | `com/thunder/NovaAPI/async/*` | Point async initialization and metrics to Nova, then remove local async classes. |
| `io/*` | `com/thunder/NovaAPI/io/*` | Switch buffer pool/compression/IO executors to Nova; drop local IO helpers afterward. |
| `util/NbtDataCompactor.java` | `com/thunder/NovaAPI/util/NbtDataCompactor` | Use Nova utility for chunk payload compaction. |
| `util/NbtCompressionUtils.java` | `com/thunder/NovaAPI/util/NbtCompressionUtils` | Update worldgen mixins and chunk IO to Nova implementation, then remove local copy. |
| `AI/AI_perf/*` | `com/thunder/NovaAPI/AI/AI_perf/*` | Rewire analytics and AI story code to Nova perf advisors/queues, then delete local package. |
| `MemUtils/*` | `com/thunder/NovaAPI/MemUtils/MemoryUtils.java` | Adopt Nova MemoryUtils in bootstrap/analytics and drop local MemUtils. |
| `analytics/*` | `com/thunder/NovaAPI/analytics/*` | Shift analytics to Nova API and remove local analytics classes. |

Guardrails:
- Verify every caller (including mixins and commands) imports Nova packages before deleting.
- For worldgen mixins that touch `NbtCompressionUtils`, ensure the Nova implementation is wired in or provide a shim to avoid breaking worldgen.
- After deletions, rerun compilation to confirm no remaining references to the old package paths.
