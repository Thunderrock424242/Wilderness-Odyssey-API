# Full mod audit remediation

**Remediation date:** 2026-08-20

**Source audit:** [`FULL_MOD_AUDIT.md`](FULL_MOD_AUDIT.md)

**Scope:** Correctness, startup, compatibility, lifecycle, bounded-work, telemetry, resource, visual, and conservative cleanup findings from the 2026-08-18 repository-wide audit.

This document records the disposition of every finding without rewriting the original read-only audit. “Implemented” means the source/resource change and focused evidence are present; it does **not** mean Minecraft runtime behavior has been proven. “Mitigated” means the low-risk portion was implemented while a measurement-dependent redesign remains open.

## Finding disposition

| Finding | Disposition | Remediation |
| --- | --- | --- |
| `AUDIT-META-001` | Implemented | Release identity and NeoForge dependency ranges now come from `gradle.properties`; current required/optional dependency schema, Minecraft/NeoForge ranges, and a built-JAR contract test were added. |
| `AUDIT-MIX-001` | Implemented | The WorldEdit mixin is pseudo-targeted and gated by the exact optional target class in the existing mixin plugin. |
| `AUDIT-PERF-001` | Implemented | World-sized Riftfall AABB queries were replaced with server/dimension entity join/leave counters and lifecycle cleanup. Local per-player cap queries remain bounded. |
| `AUDIT-CONC-001` | Implemented | SPH settlements are filtered by their exact owning level and server-thread ownership before application; client ticks cannot drain server-level work. |
| `AUDIT-PERF-002` | Implemented | Meteor work uses a bounded per-level queue, one release per tick, fixed candidate/sample budgets, loaded-column heightmaps, no unloaded fallback, and bounded retries. |
| `AUDIT-COMPAT-001` | Implemented | Create's global directories are no longer replaced. Legacy files are staged and copied without deletion or overwrite into Create's normal directories, with conflict reporting and a completion marker. |
| `AUDIT-COMPAT-002` | Implemented | WorldEdit entity copying is enabled only after the selected region is proven to contain a `create:` entity; ordinary and failed scans leave the caller's option unchanged. |
| `AUDIT-PERF-003` | Implemented | Expanded structure operations now enforce volume, synchronous-scan, already-loaded-chunk, and NBT byte budgets; force-loading/unlimited accounting were removed and rewrites are staged atomically. |
| `AUDIT-MIG-001` | Implemented | World-upgrade pack versions remain pending until explicit successful completion; paused/failed queues cannot commit, legacy early commits reopen safely, failed tasks can be retried, and per-chunk versions continue to protect chunks discovered later. |
| `AUDIT-CONC-002` | Implemented | Async pools now use abort/rejection semantics and never run rejected I/O on the caller. Internal callers handle rejection explicitly. |
| `AUDIT-RES-001` | Implemented | The three recipes and three vanilla tags use Minecraft 1.21 singular directories, guarded by packaged-JAR assertions. |
| `AUDIT-LIFE-001` | Implemented | Riftfall state is server/dimension/session scoped and cleared at logout, level unload, and server stop. |
| `AUDIT-CONC-003` | Implemented | Live async reload swaps pools on the server thread, drains accepted work on retired pools, preserves callbacks, and rejects stale cross-server results by lifecycle generation. |
| `AUDIT-TEL-001` | Implemented | Telemetry queues are per server and close/persist on shutdown; UUID caches are periodically evicted and cleared when disabled or stopped. |
| `AUDIT-TEL-002` | Implemented | Queue persistence is dirty/coalesced, uses one atomic sibling snapshot, and retains dirty state when bounded I/O rejects the write. |
| `AUDIT-TEL-003` | Implemented | Player telemetry captures immutable server-thread snapshots; logout is non-blocking, Spark waits are worker-side and capped, and retry payloads preserve their actual endpoint. |
| `AUDIT-PERF-004` | Deferred to profiling | The global water reconciliation hook remains unchanged. Its cheap authority/side/build-height/loaded-chunk guards already precede the required old-state read; speculative caching risks stale canonical-water state and global instrumentation would add work to the same hot path. |
| `AUDIT-PERF-005` | Mitigated; profile pending | Ecosystem scans now publish tick, loaded-entity count, profiled-wildlife count, and scan duration through `/wilderness ecosystem status`. Enumeration redesign awaits representative measurements. |
| `AUDIT-PERF-006` | Mitigated; profile pending | Weather simulation now builds the frozen keyed view map directly, removing the intermediate all-cell list. Broader snapshot redesign awaits JFR/GC evidence. |
| `AUDIT-PERF-007` | Implemented | Bathymetry refresh is limited to one region per level tick; sentinel/clock-rollback handling also fixes an overflow that could prevent a new region's first scan. |
| `AUDIT-ARCH-001` | Mitigated | The unused secondary compute pool defaults off while explicit existing configs remain honored. Full executor/budget consolidation waits for a real production adopter and benchmarks. |
| `AUDIT-VIS-001` | Implemented | Rift Maw and Rift Listener now use separate final 128×128 humanoid atlases with the original UV geometry/alpha mask preserved. |
| `AUDIT-LIFE-002` | Implemented | Meteor cadence and pending work are scoped per logical server/level and cleared on unload/stop. |
| `AUDIT-CONF-001` | Implemented | Meteor count and custom crater min/max pairs are normalized consistently and warn once per invalid pair. |
| `AUDIT-DEAD-001` | Implemented conservatively | The disconnected wave mixin was removed; public legacy wave classes remain deprecated compatibility surfaces. |
| `AUDIT-DEAD-002` | Mitigated for compatibility | The no-op biome modifier remains as a deprecated binary/source marker. Published impact tags are retained as documented compatibility aliases rather than silently deleted. |
| `AUDIT-PERF-008` | Implemented | Only players awaiting cryo assignment remain in a pending set; retries run once per second and pending/session state is removed on success, logout, or server stop. |
| `AUDIT-LIFE-003` | Implemented | Version-notice and partner-ad session collections now clear at logout/server stop, with persistent player opt-out data left intact. |
| `AUDIT-PERF-009` | Mitigated; profile pending | Inactive GPU diagnostics return before the `ThreadLocal` lookup. The minimal mixin call pair remains so diagnostics can be enabled live and should be assessed with client frame captures. |

## Validation record

| Check | Result |
| --- | --- |
| `generateModMetadata -PcodexBuildDir=.codex-build` | Passed; processed metadata contains the expected current ranges/types and no unresolved release properties. |
| Resource JSON parsing | Passed for all 87 JSON files under source and generated resources. |
| Rift texture atlas validation | Passed: both assets are 128×128 and have zero alpha-mask mismatches against the existing humanoid atlas. |
| Focused regression tests | Added for metadata/resources, Create migration, async rejection/reload lifecycle, telemetry persistence/snapshots, meteor ranges/queue bounds, cryo retry policy, structure budgets, shoreline scheduling, weather snapshots, ecosystem metrics, and background defaults. Not executed because compilation is blocked as described below. |
| `compileJava -PcodexBuildDir=.codex-build` | Blocked before Java compilation by `AccessDeniedException` on `.codex-build/tmp/neoformruntime/20260820-190749_recompile/output.jar`. This is a generated NeoForm file lock, not a source pass/failure. |
| Full `test` / `build` / packaged-JAR assertions | Not run because they require the same blocked Minecraft artifact step. |
| Client, dedicated server, GameTest, mixin, rendering, and profiler checks | Not run; use the manual matrix below after compilation succeeds. |

## Release verification still required

After the generated-JAR lock is released, run the isolated build in this order:

```powershell
.\gradlew.bat test "-PcodexBuildDir=.codex-build" --no-daemon --console=plain --max-workers=1
.\gradlew.bat build "-PcodexBuildDir=.codex-build" --no-daemon --console=plain --max-workers=1
```

Then complete the runtime checks that source tests cannot prove:

1. Start a dedicated server without WorldEdit, then with the supported WorldEdit/Create versions.
2. Exercise WorldEdit selections containing no entity, an ordinary entity, and a Create entity.
3. Test legacy Create schematic migration on copied directories, including a destination conflict.
4. In an integrated client, exercise SPH settlement and close/reopen two worlds in one process.
5. Trigger natural and command meteor showers near loaded/unloaded chunk boundaries.
6. Test Riftfall counts through death, chunk unload, dimension transfer, logout, and restart.
7. Test normal and oversized structure Save/Load/Detect actions and verify unloaded chunks remain unloaded.
8. Interrupt and resume a copied-world migration, resolve failures, then explicitly complete it.
9. Inspect Rift Maw/Listener UVs and animation under bright, dark, and emissive-friendly lighting.
10. Capture Spark/JFR/client-frame evidence before redesigning the remaining profiling-dependent paths.

The original audit's release gate should remain closed until the isolated full build and the relevant runtime matrix pass.
