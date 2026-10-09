# Phase 0 performance diagnostics

This release adds observation and evidence for a later performance coordinator. It preserves the existing Minecraft/NeoForge save path, chunk scheduling, gameplay cadence, worker policies and world format. No new executor is created. Safe mode defaults to true; no coordinator optimization is enabled even when safe mode is set false.

The repository-wide architecture and shutdown findings are in [coordinator-phase0-audit.md](coordinator-phase0-audit.md). The suspected 30-second Save & Quit delay still needs a capture from the actual launcher instance. Source inspection identifies possible contributors, not the measured cause.

## Commands and recording

Commands require permission level 2. `/wo perf` is equivalent to `/wo perf status`.

| Command | Evidence |
|---|---|
| `/wo perf status` | Rolling EWMA/P50/P95/P99 MSPT, sampled heap peak/usage, processors, process CPU and GC collection-time pressure, existing Tick Engine optional budget, recording/export status. |
| `/wo perf save` | Native save/stop, per-dimension save, WO teardown and lifecycle durations; failures and omitted phase measurements. Save backlog/hot/cold counts are explicitly unavailable in Phase 0. |
| `/wo perf async` | Existing shared CPU/IO active/maximum/queued counts, retired pools, main-thread handoff, Background/Tick/Data queues. Data workers already belong to shared CPU totals. |
| `/wo perf worldgen` | Slow synchronous chunk blocking spans, maximum observed duration and retained coordinates/thread names. Generation throughput and lighting pressure remain unavailable. |
| `/wo perf ownership` | Installed candidate IDs/versions/scopes across ten performance areas, possible overlaps and uncertainty. |
| `/wo perf record start` | Starts bounded sample retention and enables automatic export at server stop. |
| `/wo perf record stop` | Pauses sample retention and disables automatic stop export. Already retained samples remain available. |
| `/wo perf export` | Queues one detached JSON report on Minecraft's existing IO pool. The response means queued; completion/failure is logged separately. |

For a shutdown capture, run `/wo perf record start`, play normally, then Save & Quit. Leave recording active through quit. A successful write is logged as `[WO Performance] Report written: ...`. Reports go to `logs/wo-performance/wo-perf-<timestamp>-<sequence>.json` under the active Minecraft game directory. This is the launcher instance's logs directory, rather than the API source checkout.

Recording can also be enabled in the server config before loading a copied test world. This works when a singleplayer world's command permissions do not allow the diagnostics commands.

## Configuration

Settings are assembled into the existing unified SERVER spec, `wildernessodysseyapi-server.toml`, under the world's `serverconfig` directory. Legacy `wildernessodysseyapi-performance-server.toml` is a migration source, not the current file to edit. Restart the server/world after editing these observation settings.

```toml
[performance]
safeMode = true

[performance.coordinator]
observation = "AUTO"
recording = false
sampleIntervalTicks = 100
windowTicks = 600
chunkWaitThresholdMillis = 50.0
chunkWaitStacks = false
```

`OFF` disables collection and recording for this observer. `AUTO` and `ON` use the same bounded observation in Phase 0; neither enables optimization or changes worker limits. `performance.safeMode` belongs to the new coordinator and does not replace existing feature-specific config. Existing Tick/Background/Data engine settings remain owned by those systems.

Default JVM/recording sampling runs every 100 server ticks, about five seconds at 20 TPS. A lagging server stretches that wall-clock interval. Valid sampling intervals are 20–1200 ticks; rolling windows are 20–12000 ticks. The slow-wait threshold is 1–60000 milliseconds. Detailed wait evidence is admitted at most once every ten wall-clock seconds, globally per server session, with at most 32 retained spans. Stack capture is off by default and capped at 16 frames of 256 characters each when enabled.

To remove the two native instrumentation mixins at application startup, add this JVM property to the launcher/server JVM arguments and restart Minecraft:

```text
-Dwilderness.perf.instrumentation=false
```

The server-config OFF mode bypasses timing publication/clocks but leaves the installed wrappers in place. The startup property prevents those mixins from applying. Native instrumentation is optional (`require=0`); other mods may remove or replace the targeted calls, so an absent native phase is not proof that the save took zero time. Inspect `omittedPhaseMeasurements` as well: the phase map retains at most 96 keys.

## Understanding a report

The report has a session UUID, schema version, UTC start timestamp, outcome, mode/safe-mode settings, environment, ownership, rolling performance, timing evidence, queue gauges and optional sample history. A new integrated world creates a fresh session. Current server identity is released on stop; late worker evidence cannot be published into another world. The last completed detached report can remain in memory until a later stop.

Environment contains Minecraft/mod IDs and versions, Java/VM details, JVM uptime at server entry, heap/GC arguments and the observation settings needed to interpret sampling. Arbitrary JVM properties and config file contents are excluded because they may contain credentials. Player data, world paths, service URLs and credentials are excluded. Coordinate/thread/optional stack evidence is local diagnostic data; review it before sharing a report.

Tick percentiles are exact nearest-rank percentiles of the retained rolling window. They are not percentiles for the entire trial. Heap peak is the maximum observed sample, not the exact peak between samples. GC collection-time fraction is an interval pressure proxy; it is not exact stop-the-world pause time. Unsupported JVM metrics use negative values and display as unavailable. Use JFR or a proven external profiler when exact pause/allocation evidence is needed.

Queue gauges are approximate snapshots during concurrent activity. Shared CPU totals already include Data Engine workers. Disabled Data Engine metrics are unavailable, and dirty Data Engine entries do not count dirty save chunks. Client audio, structure-block IO and JDK HTTP machinery are outside these queue totals. There is no new priority-queue breakdown or worker governor.

Timing phases may nest. `native/saveEverything` contains player/world persistence; `native/saveAllChunks` and `native/dimension/<dimension>` can overlap it. Level-save duration includes its normal synchronous and wait work and is not pure serialization cost. `native/stopServer` and `shutdown/stopping_to_stopped` can overlap. Never add a parent and its children to estimate total elapsed time.

`wo/shutdown_handler` contains quest closure, water capture, runtime cleanup, Tick/Background/Data shutdown, shared async shutdown and telemetry flush in the existing order. The queue gauges prefixed `atShutdown/` were captured before that teardown. Compare `wo/async_shutdown` with `atShutdown/retiredPools` to investigate cumulative executor waits; compare `wo/quest_close`, `wo/telemetry_flush` and native phases to separate other contributors.

Entry timings begin at `ServerAboutToStartEvent`. They end at server starting, server started, and the first tick end after the first player login. They omit application/resource startup and do not prove that the client rendered a playable frame. Existing client reports in `logs/loading-stalls` remain the resource/screen/thread evidence source. Shutdown timing spans `ServerStoppingEvent` to `ServerStoppedEvent`; the actual Save & Quit screen can include more time.

Chunk evidence covers the `managedBlock` span on the server or the off-thread marshal/join span. It omits synchronous ticket/scheduling work before managed-block and ordinary cache hits. One request may produce both a server and a caller span. Counts are observed slow spans, not unique chunks, generated chunks or per-mod blame. Optional caller stacks can help trace a request but must be interpreted with the actual installed pack.

Ownership verifies installed mod presence using runtime `ModList`; configurable patch activity is unverified. Multiple candidates are retained, including complementary rendering scopes. The registry never authorizes WO takeover. A C2ME ID or an autosave mod ID cannot establish which of its config-dependent patches is active by itself. Third-party configs are not guessed or rewritten.

## Bounds and failure behavior

The observer stores one fixed tick ring, 96 phase keys, 32 rate-limited wait spans, at most 120 recording samples and bounded optional stacks. Historical samples are rolling snapshots with captured gauges; report workers receive immutable detached data. Percentile sorting occurs on snapshots, never each tick. JVM beans are cached. Export serialization and file IO run off the game thread on an existing Minecraft IO pool.

Only one report write is admitted at a time across sessions. The writer keeps the newest ten matching WO JSON files and preserves unrelated files. A busy/rejected writer never runs a report write inline or blocks shutdown. If a manual export is still in flight when automatic terminal export is attempted, the terminal snapshot is retained in memory and a warning is emitted; it is not guaranteed to reach disk. Avoid a manual export immediately before quit when collecting a terminal report. Disk/serialization failure is logged and releases admission for a later request. Partial diagnostic files can remain after a write failure; they are not world persistence files.

Original save/stop/wait operations run once, with their original return value and exception behavior. Timing publication failures cannot replace ordinary original-operation failures. The observer performs no chunk request, live-world worker serialization, cancellation, persistence substitution or third-party executor control.

## Validation and reproducible trials

The focused Gradle task is:

```powershell
.\gradlew.bat :compileJava :performanceDiagnosticsTest '-PcodexBuildDir=.codex-build' --no-parallel
```

It exercises observer statistics/eviction, bounded wait capture/phase omissions, ownership, original-operation failure/result preservation, session identity/OFF/reset, recording bounds/detachment, report rejection/file failure/retention, conservative config defaults, startup mixin gating, and existing architecture/loading/config regressions. Packaging uses the normal checked-in Gradle wrapper. Automated source/unit/package checks do not establish actual pack mixin composition, multiplayer behavior, world-data safety or measured performance improvement.

Use copies of the same world and keep mod versions/configs, Java/JVM arguments, render distance, simulation distance and hardware/storage fixed. Record those client/server settings separately; they are not all exported. Run at least five trials per condition. Compare observation OFF with AUTO to measure instrumentation overhead first. Compare medians and tail behavior rather than a single run. Later governor/save changes require a separately authorized intervention and the same benchmark controls.

For each trial record wall-clock world-entry and Save & Quit screen times, exported entry/shutdown/native/WO phases, comparable rolling P95/P99 MSPT windows, fresh-generation throughput from a proven external measurement, heap peak and GC pressure. Save backlog remains unavailable in Phase 0. Keep a trial table with the report filename and settings. Do not infer generation throughput from wait-span counts.

Live acceptance still requires copied-world load → modify → save → quit → reload → verify across vanilla/modded chunks, block entities/entities, NeoForge attachments/capabilities, SavedData, players, structures and Nether/End/modded dimensions. Check mixin application in the actual pack log, both with instrumentation enabled and with the startup gate disabled. Exercise original save failure propagation through the transformed methods where practical. This implementation has not launched or modified a gameplay world to claim that proof.
