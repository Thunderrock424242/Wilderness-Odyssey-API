# Performance coordinator: Phase 0 audit

Scope: Minecraft 1.21.1, NeoForge 21.1.256. Phase 0 observes and records; it does not change saves, worker limits, chunk scheduling, lighting, gameplay cadence, or world generation. The pre-existing dirty checkout is preserved.

## Shutdown finding

There is no 30-second executor termination wait in the Minecraft mod source. This does **not** prove WO is absent from the reported 30-second Save & Quit delay. A launcher-instance capture is required to attribute it.

* `async/AsyncTaskManager.shutdownExecutor`: waits up to **2 seconds per pool**, CPU then IO, then every retired pool. Repeated live config reloads can accumulate draining retired pools. A sufficiently large retired-pool list can therefore approach 30 seconds without a 30-second literal. Timeout causes interruption, without a second wait for termination.
* `quest/runtime/QuestServerSession.close`: pumps ordered persistent quest I/O and polls its completion in 10 ms increments for up to **5 seconds**. This can run before the shared pools stop.
* `server/ServerLifecycleEvents.onServerStopping`: quest close → per-level SPH persistent capture → water/debug/simulation cleanup → Tick Engine → Background Engine → Data Engine → shared async shutdown → synchronous telemetry spool persistence → player cache cleanup. Capture and spool costs have no fixed time limit; they need measurement.
* `telemetry/TelemetryQueue`: final disk snapshot takes `persistenceLock`; an earlier write can hold that lock. HTTP retry sleeps occur on I/O workers, not intentionally on the game thread.
* `structureblock/StructureBlockIoExecutor`, `performance/background/AsyncComputeManager`, and compatibility diagnostics request interruption without a termination wait.
* AI HTTP requests are canceled at HIGHEST stopping priority. Client voice cancels playback/microphone requests on logout/unload; audio latch waits run on a playback worker.

The separate structure viewer has a 30-second update-feed deadline; it is not Minecraft Save & Quit. The standalone Aether launcher joins its server for up to 10 seconds. Neither is a mod-world executor termination timeout.

## Async architecture map (before changes)

Paths in this table are relative to `src/main/java/com/thunder/wildernessodysseyapi/` unless stated otherwise.

| Owner | Workers and queue | Inputs / results | Lifecycle and shutdown |
|---|---|---|---|
| `async/AsyncTaskManager` | CPU: configured `maxThreads`, default processors minus one. IO: `max(4, maxThreads * 2)`. Both `ArrayBlockingQueue`, default 256. Main-thread handoff is a concurrent queue with an explicit capacity counter. | Payload calculation → generation-checked server callback, or pure direct worker task. No caller-runs rejection. | Started/reloaded through shared lifecycle/config owners. Each current and retired pool can wait 2 seconds. |
| `dataengine/async/DataWorkerPool` | **Reuses shared CPU pool**; bounded in-flight and completed-result queues. | Immutable calculation → owner-validated server apply. Update queue coalesces explicit supersedable entries; critical work has backpressure. | Accepting flag prevents late result publication. No new executor. |
| `performance/background/AsyncComputeManager` | Separate bounded CPU executor and bounded result queue; **disabled by default** pending production adoption. | Snapshot → pure compute → server apply. | Pool identity rejects late results; `shutdownNow`, no await. |
| `performance/background/BackgroundEfficiencyManager`, Tick Engine, Simulation Engine | Server-thread bounded schedulers, not additional worker pools. Analytics dispatches through existing async infrastructure. | Optional work uses live `ServerTickEvent.hasTime`; metrics and gradual pressure recovery already exist. | Existing lifecycle teardown clears optional queues. No chunk lifecycle ownership. |
| `quest/workshop/QuestIoDispatcher` | Ordered 128-content-entry lane plus one cleanup slot on shared IO. One-worker, one-entry fallback only when shared async is explicitly disabled. | File-only tasks and serialized quest store operations; server callback handoff remains session-owned. | Close releases leases; session waits up to 5 seconds. No broad migration in Phase 0. |
| `structureblock/StructureBlockIoExecutor` | One daemon, 32-entry bounded queue. | Immutable paths and decoded file limits; live serialization stays on server thread. | Recreated per server; interrupts queued/running postprocessing at stop. |
| `loading/LoadingProfileMonitor` | One client-lifetime scheduled daemon; one periodic task plus one final report task. Stack/site/history/session retention is bounded. | Immutable screen phase observations, JVM thread metadata; no worker-side live world/screen access. | Nonblocking client shutdown. Existing resource/world-entry report remains independent. |
| `modconflictchecker/DedicatedConflictDetector` | One scheduled compatibility diagnostics daemon; fixed periodic task. | JVM deadlock/thread and compatibility evidence. | Starts/stops through registry conflict lifecycle; no await. |
| `ai/voice/client/VoiceAudioPlayer`, `MicrophoneCapture` | Separate client-lifetime single-worker executors backed by implicit unbounded queues. Microphone admission is one active recording; upstream voice playback is bounded. | Bounded WAV/audio data, device input; HTTP voice calls use JDK async HTTP. | Logout/unload cancels feature activity. Executors are daemon/client-owned. Queue bounds at producer and executor should be reviewed in Phase 1. |
| Aether backend, telemetry, playtest, feedback, verification relay | JDK `HttpClient.sendAsync` machinery; Aether/telemetry waits occur within admitted shared-worker operations. | Bounded request/response bodies where implemented; immutable outbound metadata. | Scoped AI cancel; telemetry retry/flush/spool ownership is retained. HTTP machinery is not arbitrary third-party work WO may cancel. |
| `modlisttracker/commands/ModListDiffCommand` | A custom thread dispatches a Swing GUI to the EDT on command invocation. | Mod-list comparison GUI. | Manual developer/operator action, not world generation/save work. |
| `aether-server/` (separate application) | Bounded HTTP and generation pools, two scheduled timers, launcher/shutdown hooks. | Protected standalone inference and persistence. | Separate process. Must not count these as Minecraft-controlled CPU workers. |
| `tools/structure-viewer/` (separate application) | Renderer, 4-entry loader, 1-entry updater, watch thread. | Model/file rendering and update discovery. | Separate GUI/application. Renderer implicit queue and update deadlines are not Minecraft save attribution. |

No explicit `ForkJoinPool` or common-pool `runAsync`/`supplyAsync` submission was found in mod production code. CompletableFuture also appears as Minecraft pipeline results, HTTP results, quest result composition, and ready/completed values; an occurrence is not an additional worker pool.

## Cross-cutting source audit

The repository-wide audit covers production Java in the root mod, standalone Aether application, and structure viewer. Test and GameTest-only workers are distinguished. A reproducible, line-indexed search inventory is written to `.codex-build/performance-audit/source-inventory.txt` for executor/thread creation, futures, blocking waits, synchronization/locks, timeouts, lifecycle, chunk/save/worldgen hooks, and mixins.

Locks cluster in: shared worker lifecycle; quest ordered storage, lease and reward state; telemetry queue/persistence; bounded Data Engine completion/cache registries; runtime/client snapshot stores; rendering/shader compatibility; AI circuit/profile/config stores; simulation registries; weather/water state; and config/spec initialization. They are not globally replaced. Inventorying a lock does not establish contention.

Worldgen/chunk owners include native biome/structure registration, spawn placement, temporal-rift generator, meteor/glacial feature systems, generated-water integration, and narrow generation-error reporting. Save-adjacent mixins currently concern structure blocks and water/attachment representation. None is evidence of a general WO replacement for region-file saving. Existing mixin/plugin lists were inspected; optional WorldEdit/Iris/Sodium/Embeddium integrations use startup resource checks.

## Minecraft pipeline inspected

Evidence: the exact generated `neoforge-21.1.256-sources.jar` and targeted `javap` inspection, never modified. Relevant classes: `MinecraftServer`, `ServerChunkCache`, `ChunkMap`, `IOWorker`, `EntityStorage`, `DimensionDataStorage`, and NeoForge lifecycle/tick hooks.

* `MinecraftServer.stopServer` drains outstanding chunk work, saves players and worlds, posts level unload, closes levels/resources/storage. `ServerStoppingEvent` precedes that final path; `ServerStoppedEvent` follows it. Event-to-event time is a shutdown milestone, not the exact client Save & Quit screen interval.
* `ChunkMap.save` runs ordinary chunk serialization and `ChunkDataEvent.Save`, then schedules `write`. `IOWorker.store` already coalesces positions and performs region I/O asynchronously. Final saving waits for readiness and flushes that worker. Entity flush also joins its region-storage synchronization.
* `DimensionDataStorage.save` calls each nonnull SavedData save. It is a separate cost from chunk serialization.
* `ServerChunkCache.getChunk` can marshal off-thread calls to the server and join; the server branch can managed-block for chunk readiness. It also has cache/loading fast paths and NeoForge's currently-loading deadlock bypass. Instrumentation must preserve all of them and must not request any extra chunk.

## Ownership and limits

The launcher path has not yet been supplied. No deployed pack mod list is inferred from Gradle dependencies or stale `run/config` files. Runtime ModList IDs/versions provide authoritative **presence**, not proof that a configurable patch is enabled. Missing/unverified config information never permits takeover.

The registry covers SAVE, CHUNK_IO, CHUNK_LOADING, WORLDGEN, LIGHTING, TICK_OPTIMIZATION, MEMORY, RESOURCE_LOADING, RENDERING, and WO_BACKGROUND_WORK. Recognized candidates include C2ME and component IDs, Shinoyuki BetterAutoSave (`shinoyuki_betterautosave`), Fast Async World Save (`fastasyncworldsave`), Smooth Chunk Save (`smoothchunk`), Lithium/Canary/Radium, ModernFix, FerriteCore, ScalableLux/Starlight, Sodium/Embeddium, and ImmediatelyFast. Multiple candidates are retained. ImmediatelyFast's immediate drawing and terrain rendering are different scopes; simultaneous presence alone is not a conflict.

Confirmed ID sources: [BetterAutoSave properties](https://github.com/ShinoyukiMiyako/Shinoyuki-BetterAutoSave/blob/main/gradle.properties), [Fast Async World Save NeoForge metadata](https://github.com/someaddons/FastAsyncWorldSave/blob/neo1.21/src/main/resources/META-INF/neoforge.mods.toml), [Smooth Chunk Save NeoForge metadata](https://github.com/someaddons/smoothchunksave/blob/neo1.21/src/main/resources/META-INF/neoforge.mods.toml). Third-party options are not guessed or rewritten.

## Phase 0 implementation design and plan

1. Keep read-only observation under `diagnostics/performance`, separate from optimization governance. Preserve the existing architecture test that prohibits chunk owners depending on governors. Config is assembled under the existing `performance` server category, with `safeMode=true`, observation AUTO/OFF/ON and optional recording OFF by default.
2. Maintain a fixed tick ring and EWMA; compute exact rolling percentiles only for low-frequency samples/commands. JVM heap/GC collection-time proxy/process CPU sampling uses a cached sampler every several seconds, not every tick. Unsupported metrics are explicitly unavailable.
3. Read shared CPU/IO/retired pool, Background, Tick and Data Engine metrics without adding workers. Data Engine worker counts must not be added to shared CPU counts a second time. Dirty Data Engine entries are not dirty save chunks.
4. Observe startup, first logged-in player's following tick, stopping, stopped, overall save calls, per-dimension saves, and WO teardown boundaries. World-entry and shutdown milestone definitions must be disclosed. Normal saving stays authoritative, including failures.
5. Use two narrowly gated diagnostic mixins for exact native save/stop calls and synchronous chunk request duration. Wrap original execution once in `try/finally`; preserve returns and thrown exceptions. No mutation, cancellation, serialization replacement, priority changes, or worker dispatch. Startup `wilderness.perf.instrumentation=false` removes these hooks. Missing targets skip instrumentation and are disclosed as unobserved.
6. Provide operator `/wo perf status|save|async|worldgen|ownership|record start|record stop|export`. Recording holds bounded immutable history; one globally admitted report write uses Minecraft's existing IO pool. No new executor, no wait, bounded report retention. Unknown save backlog is reported unavailable until Phase 2; do not scan loaded chunks to fabricate it.
7. Test rolling-window eviction/EWMA/percentiles, invalid/empty values, rate-limited bounded wait capture, cross-session reset, multi-owner detection, disabled/fail-open observation, immutable export and writer rejection/retention. Compile, focused tests including architecture/config/loading, then root packaging through the normal isolated Gradle wrapper.

## Acceptance and next evidence

Phase 0 acceptance is source, test, and package evidence for diagnostics. It is **not** a performance improvement claim. Do not start Phase 1 or Phase 2 from this audit alone.

Use copied worlds and identical mod/config/JVM/render/simulation settings for at least five baseline trials and five later intervention trials. Record world-entry milestones, Save & Quit screen wall time, shutdown/native-save/WO-teardown durations, rolling P95/P99 MSPT, fresh-chunk throughput, heap peak and GC pressure, queues and dirty backlog (unavailable in Phase 0). Compare medians and tails, plus write amplification for later save work. A native full save may include IO waiting; level-save duration is not pure serialization time. A long getChunk call is synchronous request latency, not necessarily generation and not per-mod blame.

For this phase, perform load → modify → save → quit → reload → verify with vanilla and modded chunks, entities/block entities, attachments/capabilities, SavedData, players and all dimensions. The observer does not replace persistence, but live compatibility and overhead still require a launcher run. Forced failure paths must preserve the original result/exception. No world edits or live Minecraft launches are performed merely to produce a package.
