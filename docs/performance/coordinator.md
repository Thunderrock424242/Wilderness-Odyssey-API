# Opt-in pack performance coordinator

The whole coordinator defaults to disabled. Its observer, WO optional-work governor, disposable-job cancellation and coordinate-only save tracking are behind one server configuration switch. The existing Tick Engine, Background Efficiency Manager, Data Engine and required persistence retain their own configuration and ownership.

This implements the Phase 1 governor and the Phase 2 native save adapter following the [Phase 0 audit](coordinator-phase0-audit.md). Phases 3 and 4 were investigated and retain their original evidence gates: no detached persistence replacement or chunk-priority modification is installed. There is no actual-pack benchmark establishing a performance gain or explaining the reported thirty-second Save & Quit delay.

## Configuration

Edit `config/wildernessodysseyapi/wildernessodysseyapi-server.toml` under the active Minecraft instance/server directory and restart the world/server. This is the existing unified SERVER configuration; the legacy performance config is only a migration source. The isolated GameTest loads this same registered path under its generated game directory.

```toml
[performance]
safeMode = true

[performance.coordinator]
enabled = false
observation = "AUTO"
recording = false
sampleIntervalTicks = 100
windowTicks = 600
chunkWaitThresholdMillis = 50.0
chunkWaitStacks = false
asyncGovernor = "AUTO"
saveDrain = "OFF"
maximumOptionalMillis = 2.0
headroomFraction = 0.10
entryStableTicks = 100
cooldownTicks = 200
minimumDirtyTicks = 600
minimumQuietTicks = 100
maximumTrackedChunks = 4096
```

`enabled=false` bypasses all coordinator collection and policy. `/wo perf` explains how to enable it. `enabled=true` starts observation and the configured modules. `observation="OFF"` also disables controls because no observed spare capacity is available. `asyncGovernor="OFF"` preserves existing WO worker and scheduler policies. `saveDrain="OFF"` keeps partial dirty metadata available for diagnosis without submitting optional saves. AUTO and ON both obey the same safety, ownership, headroom and watchdog constraints; ON does not override them. Save drain defaults to OFF until an affected-instance profile identifies a cold dirty backlog.

`performance.safeMode=true` remains the default. Setting it false does not introduce a serializer, queue, worldgen scheduler or experimental phase. No coordinator module controls arbitrary third-party executors.

The optional startup JVM property `-Dwilderness.perf.instrumentation=false` removes all five coordinator timing/dirty/native-save mixins. Save tracking and drain are vetoed when this property is false. A server-config disabled switch leaves the installed hooks with an early inactive gate.

## Phase 1: WO resource governor

Policy is a server-session state machine: ENTRY, CHUNK_PRESSURE, OVERLOADED, RECOVERY, NORMAL and STOPPING. Integrated entry recovery starts after the first logged-in player's following tick; dedicated servers use ServerStarted. These are server milestones and do not prove that a client has rendered a playable frame. At least `entryStableTicks` healthy ticks are required before recovery.

The shared optional allowance is a small fraction of smoothed headroom below 50 MSPT, capped by `maximumOptionalMillis` and the current tick's remaining wall time. The existing Tick and Background schedulers and save adapter account actual cost against that same allowance. It is a time admission limit, not a chunk-per-tick quota. Native serialization and arbitrary task callbacks cannot be preempted once admitted.

EWMA at or above 45 MSPT, sampled CPU at or above 90%, GC collection-time fraction at or above 10%, or WO CPU queue occupancy at or above 75% immediately removes optional allowance. Recovery requires lower thresholds (40 MSPT, 80%, 5%, 50%) for the configured healthy interval and then restores allowance gradually. An observed slow synchronous chunk wait suppresses optional work for 100 ticks; it is a pressure signal, not proof of generation throughput or per-mod blame. JVM metrics remain sampled infrequently and unsupported metrics remain unavailable.

BACKGROUND and SAVE have independent cost watchdogs. Repeated costs beyond their admission reduce their allowance; three overruns pause that module for `cooldownTicks` (default ten seconds at 20 TPS). Recovery starts cautiously. Existing player-critical scheduler priorities retain their original execution contracts; BACKGROUND/IDLE work yields. Shared worker queues retain their existing bounded FIFO behavior: this change does not claim to introduce priority workers.

Only explicitly disposable telemetry enrichment, optional profiler analysis and analytics use the new bounded cancellation admission. At most two such jobs are in flight per session on the existing executors; completion releases admission. Overload, capacity and stopping reject further submissions without running them on the caller. STOPPING rejects and interrupts those jobs without waiting. Cancellation is cooperative; a callback that ignores interruption may continue. Basic telemetry, quest persistence, required computation/results and native saves retain their owners and required APIs.

When the async governor is enabled, shared executor teardown starts all current and retired pools draining together and shares one four-second deadline. It replaces independent two-second waits per pool; it does not add a thirty-second wait. This is the existing executor owner's bounded shutdown policy, including interruption of unfinished work at its deadline. Required quest closure and telemetry flush remain in their established lifecycle order and are not classified as disposable work.

## Phase 2: native opportunistic saving

A bounded index stores dimension/position, first and most recent dirty ticks, generation, pending generation, successful-save tick and prior serialization-cost estimate. Repeated notifications coalesce. It retains no chunk references and makes no chunk requests. Overflow omits metadata while leaving native dirty flags untouched. Candidate inspection is bounded and rotates through records; no loaded-chunk scan is introduced.

Coverage is explicitly partial: loaded full chunks reporting `setUnsaved`, direct vanilla block mutation and structure-map mutation, plus dirty chunk-load events. It is not a complete save backlog and does not count entities, players, SavedData or every direct/off-thread mod mutation. Native dirty state remains authoritative. Hot/young/pending records are deferred until both minimum dirty age and quiet age are met. Prior measured cost must fit the remaining allowance. The initial cost estimate is conservative but cannot predict an unusually expensive modded serialization.

The adapter requires an already-visible holder, no currently-loading chunk, and the native `isReadyForSaving()` barrier (no generation references and completed save dependencies). It retrieves only an already-loaded full chunk on the server thread and invokes the existing private `ChunkMap.save`. Native ChunkSerializer, NeoForge save events, mod NBT hooks, attachments, POI persistence and native IOWorker ordering remain in that path. The world format and compression are unchanged. There is no second IO queue or background access to mutable Minecraft state.

An observed native write future publishes only a detached attempt outcome; completion does not retain a server, world or chunk. Generation matching prevents an older indexed completion from cleaning a newer mutation. A failed save/write restores native dirty state when the chunk is loaded, logs once and disables optional drain for the session. Pending attempts are re-marked before native chunk unload, level unload and authoritative final saving. Normal native saving continues independently and can supersede optional work.

Any installed SAVE or CHUNK_IO ownership candidate, including the registered save mods and C2ME IDs, vetoes tracking/drain conservatively. Presence is verified; config-dependent patch activity is not guessed. Diagnostics and the WO governor can remain available. Missing native save/write instrumentation causes the adapter to stop and return to normal saving rather than assume that persistence was observed.

## Conditional Phases 3 and 4

The exact NeoForge 21.1.256 sources already serialize on the server and queue region writes through IOWorker. Phase 3 needs an affected-pack profile proving a remaining bottleneck, plus detached-data/event compatibility, bounded memory, ordering, failure and copied-world reload evidence. Another queue around native async IO is not justified by the current evidence.

Minecraft already owns ChunkMap/ChunkHolder/ServerChunkCache priorities and generation dependencies. Phase 4 needs an affected-pack profile proving a remaining priority problem after WO competing work yields, then compatibility with the installed scheduler, ChunkStatus ordering and deterministic generation. No ticket levels, readiness, RNG, lighting, view distance or generation concurrency are changed here.

## Validation and trials

Focused logic/config/architecture tests use:

```powershell
.\gradlew.bat :compileJava :performanceDiagnosticsTest '-PcodexBuildDir=.codex-build' --no-parallel
```

The transformed native adapter has a separate isolated Minecraft GameTest:

```powershell
.\gradlew.bat runGameTestServer -PcoordinatorGameTests '-PcodexBuildDir=.codex-build' --no-parallel
```

Its generated test world is `.codex-build/run-coordinator-gametest`. It checks the disabled master default, transformed direct block dirty tracking, a loaded chunk held by a generation dependency, chest inventory in stored chunk NBT, unfinished-write handling before final save, and a controlled serialization-event exception followed by normal-save retry. The expected exception is logged by NeoForge/native saving. This is not an injected disk-write failure, actual-pack acceptance or a measured speedup. Unit tests cover metadata overflow/coalescing, newer mutations and old completions, failure and lifecycle forgetting, shared budgets, recovery/watchdogs and disposable cancellation without interrupting a required job.

Use copied worlds, fixed mod/config/JVM/hardware/render/simulation settings and at least five trials per condition. First compare master disabled with observer-only (`enabled=true`, `asyncGovernor="OFF"`, `saveDrain="OFF"`). Then trial the governor with saving still OFF. Only trial native drain after the baseline establishes eligible cold backlog. Keep save-mod ownership vetoes intact.

Record wall-clock entry/Save & Quit, comparable P95/P99 MSPT windows, native and WO teardown phases, heap/GC/CPU pressure, actual generation throughput from an external measurement, queue gauges, partial dirty coverage and write amplification. Use `/wo perf record start` through quit and compare exported schema-3 reports. Follow the [observer guide](coordinator-phase0.md) for definitions and evidence limits.

Acceptance still requires copied-world load → modify → save → quit → reload → verify across vanilla/modded chunks, block entities/entities, attachments/capabilities, SavedData, players, structures and all relevant dimensions. Also exercise actual write failure, pending mutation/completion, unload/reload and installed-pack mixin composition. Automated compilation, logic tests, packaging and the isolated GameTest do not replace those checks.
