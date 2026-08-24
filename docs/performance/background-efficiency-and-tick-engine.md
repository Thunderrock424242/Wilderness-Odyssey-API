# Background Efficiency Framework and Tick Engine

## Scope and ownership

These packages govern only work that Wilderness Odyssey explicitly opts into:

- `performance/background` owns general deferred work, activity classification, safe snapshot computation, networking batches, analytics/IO batches, and framework metrics.
- `performance/tickengine` measures server pressure and governs optional WO work through tick budgets, deferred tick tasks, adaptive intervals, and explicit missed-tick policies.

Neither package replaces Minecraft's tick loop. They do not cache world state, add dirty flags, skip vanilla ticks, alter random ticks, change TPS, or throttle arbitrary vanilla/modded entities and block entities. They also do not load, retain, send, unload, ticket, force, or govern generation of Minecraft chunks. No mixin is used.

Every end-of-tick queue observes NeoForge's live `ServerTickEvent.hasTime()` supplier. The scheduler checks it before each optional callback, including callbacks labeled `CRITICAL` or `GAMEPLAY`, and immediately leaves remaining work queued when Minecraft withdraws the allowance. Those priority names order Wilderness-owned work; they never outrank Minecraft's chunk IO or generation completion.

## Unified performance configuration

The server-side performance stack now uses one file:

`config/wildernessodysseyapi/wildernessodysseyapi-server.toml`

Its nested layout remains under `[performance]`:

- `performance.enabled`: reversible master switch for all three Wilderness-owned engines.
- `performance.backgroundEfficiency`: background scheduler, bounded worker, activity, network-batch, and analytics-batch settings.
- `performance.tickEngine`: pressure, budget, deferred-work, profiling, adaptive interval, and tick-debt settings.
- `performance.dataEngine`: bounded update, dirty-state, async-result, network, cache, and metric settings.

Setting `performance.enabled = false` reloads Background Efficiency, Tick Engine,
and Data Engine in their disabled modes. Existing subsystem owners then use their
documented synchronous/direct fallbacks; this switch does not stop vanilla ticks
or take ownership of Minecraft entities, block entities, chunks, networking,
saves, or world lifecycle.

On the first launch after this change, the exact legacy background, tick, and
data config files are copied into the new section layout only when the unified
file does not already exist. The legacy files are retained unchanged as rollback
references. Once the unified file exists, it is authoritative and later edits to
the three legacy files are intentionally ignored.

NeoForge `CLIENT`, `COMMON`, and `SERVER` config types have different loading,
sync, and world ownership semantics, so they cannot safely become one literal
spec. The long-term consolidation model is a small scope-aware suite (client,
global/common, and world/server), with migrations handled one compatible group
at a time. This phase consolidates only the three performance `SERVER` specs.

## Submitting background work

Callbacks submitted to `BackgroundEfficiencyManager.scheduler()` run on the logical server thread. Keep one callback step bounded. Return `BackgroundTask.Result.DEFER` when the task retained a cursor and needs another processing pass.

```java
BackgroundEfficiencyManager.scheduler().submit(new BackgroundTask(
        "weather",
        WorkPriority.BACKGROUND,
        0L,
        server.getTickCount(),
        "region=" + regionId,
        () -> processOneWeatherRegionStep(regionId)
));
```

Use `CRITICAL` only for work that is genuinely required for correctness. Direct player actions, combat safety, validation, and important state sync should normally stay event-driven rather than becoming delayed tasks. Queue capacity is bounded globally and per subsystem; callers must handle a rejected submission without retrying every tick.

`TickEngine.scheduler()` is the corresponding queue for explicitly tick-aware work. It supports stale-task expiry and optional coalescing keys. A coalescing key represents equivalent pending work; a duplicate accepted under that key does not add another queue entry.

## Activity and sleep

`ActivityManager` classifies a block or chunk as `ACTIVE`, `NEARBY`, `BACKGROUND`, or `DORMANT`. Every subsystem supplies its own ascending distance thresholds. The helper checks the level's existing player list; it does not load chunks, retain player positions, or build a cache.

Direct interaction should set `directlyInteracting=true`, which always returns `ACTIVE`. A dimension with no players returns `DORMANT` unless a direct interaction was explicitly supplied.

Systems capable of sleeping can implement `ElapsedTimeSimulation` or `TickDebtAware`. Use game tick counters:

```java
long elapsed = ElapsedTimeSimulation.elapsedTicks(lastSimulationTick, currentTick);
simulation.simulateElapsedTime(elapsed);
lastSimulationTick = currentTick;
```

Do not turn elapsed time back into an unbounded `for` loop. `TickDebtManager` supports three explicit policies:

- `COLLAPSE`: one `advanceSimulation(elapsedTicks)` call.
- `INDIVIDUAL`: required steps remain separate, but only the configured number run per call; the returned result preserves remaining debt.
- `DISCARD`: missed optional work is intentionally dropped. Never use this for required gameplay state.

## Safe asynchronous computation

`AsyncComputeManager` enforces a bounded CPU pool and a bounded server-thread result queue. The safe sequence is:

1. On the server thread, copy required primitive/data values into an immutable snapshot.
2. Submit that snapshot to a pure calculation callback.
3. Let the framework enqueue the result.
4. Apply the result later from the bounded end-of-tick server-thread drain.

Worker callbacks must not reference or mutate `ServerLevel`, `Entity`, `BlockEntity`, `ChunkAccess`, registries, saved data, or another mutable Minecraft object. The Java type system cannot prove that a caller's object is immutable, so the snapshot boundary is a caller responsibility. Never call `Future.get()` or otherwise wait for optional computation from the server thread.

`submitWithoutResult` is intended for isolated analytics preparation or non-save IO. It does not add a no-op result to the server queue. Minecraft saves and authoritative world persistence must continue to use their existing lifecycle-owned paths.

## Network batching

Register a typed channel once, normally during common bootstrap:

```java
NetworkBatcher.Channel<WeatherUpdate> channel =
        BackgroundEfficiencyManager.network().registerServerChannel(
                "weather",
                "regional_state",
                (server, playerId, updates) -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        PacketDistributor.sendToPlayer(player, WeatherBatchPayload.from(updates));
                    }
                }
        );
```

Queue immutable updates with a recipient-local deduplication key. A newer value replaces an older pending value under the same key. The sender receives one immutable list when the batch reaches its size limit or maximum delay. Tracking-player dispatch is supported by passing the caller's already-known tracking collection to `queueForPlayers`; the batcher does not scan the world for recipients.

This infrastructure does not change existing packet formats. Migrate a subsystem only when it has a real combined payload. Important immediate synchronization should retain its direct send path.

## Analytics and IO batching

`AnalyticsBatcher` provides typed channels whose immutable batches run through `AsyncComputeManager`. It is appropriate for WO-owned analytics, optional logging, compression, and similar work. It is not appropriate for world saves or required durability writes. If the async pool is saturated, a drained batch is requeued within the configured bounded capacity rather than executed on the server thread.

The existing telemetry queue already batches retry delivery and remains unchanged in this implementation. It can migrate to `AnalyticsBatcher` later after its persistence and retry semantics are tested together.

## Tick pressure and budgets

`PerformanceServerEvents` starts measurement at the highest-priority NeoForge server pre-tick event and closes it in the lowest-priority post-tick handler after bounded WO work. `TickMonitor` stores preallocated 20-sample and 100-sample rolling windows. Escalation requires consecutive samples, while recovery requires a configured MSPT margin and moves only one pressure level at a time.

Default pressure entries are:

- `RELAXED`: below 30 ms.
- `BUSY`: 30 ms.
- `HIGH`: 40 ms.
- `CRITICAL`: 47 ms.
- `OVERLOADED`: 50 ms.

`TickBudgetManager` reserves the default 45 ms soft target under Minecraft's 50 ms target. It subtracts work already consumed before optional processing, applies the pressure multiplier, then applies the gradual-recovery multiplier. It never intentionally fills the entire target tick.

The Tick Engine controls the background scheduler only through `BackgroundSchedulerControl`. It can reduce the budget multiplier, cap allowed time through the caller budget, suspend `BACKGROUND`, suspend or gate `IDLE`, and inspect queue pressure without depending on scheduler internals.

## Registering and throttling a subsystem

Register stable metadata before using adaptive intervals:

```java
TickEngine.registerSubsystem(new SubsystemPolicy(
        "weather",
        "Weather",
        TickPriority.NORMAL,
        100,
        false
));
```

The final two values define the longest interval required when suspension is forbidden and whether full temporary suspension is safe. The engine includes default registrations for `weather`, `ecosystem`, `water`, `labs`, `aether`, `analytics`, and Wilderness-owned `network` work; these registrations do not automatically change those systems. Chunk generation and structure placement are deliberately not registered because their Minecraft-owned lifecycle must never be suspended or delayed by this engine. Optional discovery or analysis must use a separately named subsystem and operate only on caller-provided immutable data or already-loaded state.

WO block entities may use `AdaptiveBlockEntityTicker` around only their expensive custom section. WO entities may use `AdaptiveEntityWork` around optional AI decisions, target analysis, or environment scanning. Never place base ticking, physics, combat, capability safety, or direct player interactions behind these helpers.

## Metrics and debug UI

`BackgroundMetrics.snapshot()` exposes queue counts, task outcomes, async state, batch backlogs, activity gauges, and aggregated per-subsystem time. Systems that own a known set of regions or objects can publish current activity gauges with `setActivityCount`.

`TickEngine.snapshot()` exposes TPS, current/rolling/worst MSPT, pressure, budget use, deferred queue pressure, background queue pressure, throttled subsystem count, and subsystem timings. The ecosystem adapter records both its immediate player-driven zone pass and its bounded optional maintenance under the `ecosystem` timing. The weather adapter records per-level atmospheric maintenance and authoritative snapshot publication under `weather`; loaded-world effect schedulers remain server-thread work and retain their own bounded cadences. The water adapter records direct gameplay-critical SPH simulation plus the centrally queued regional, shoreline, synchronization, and persistence lanes under `water`. Integrated singleplayer displays a `WO TICK ENGINE` section on the existing Performance F3 page. Dedicated/remote clients deliberately show that server metrics are not synchronized; no new debug packet or permission surface was added.

## Compatibility and rollout

The ecosystem is the first production integration. Player movement still performs immediate zone classification and AI restoration, while periodic loaded-wildlife scans and distant-population maintenance enter the Data Engine's bounded schedule and obey the Tick Engine's adaptive `ecosystem` policy. Player lifecycle/config refreshes use coalesced Data Engine dirty state while retaining the existing payload codec.

Weather Phase 2B is the second production integration. `weather_runtime` centrally schedules optional atmospheric maintenance, places each loaded dimension in its own coalescible queue step, and routes due snapshot publication through retained dirty state. `WeatherAuthority` now captures immutable per-level batches on the server thread, delegates only frozen atmospheric math to the shared Data Engine worker pool, and validates lifecycle generation plus the complete captured cell-revision set before bounded server-thread apply. Saturation retains due work rather than caller-running it; timeouts recover failed or dropped completions, and disabling the Data Engine uses the authority's synchronous fallback. The non-suspendable Tick Engine `weather` policy can slow configured work under pressure without making an operator's slower cadence run more often. Weather SavedData, tracked-system mutation, the existing v4 regional payload, client interpolation, and every world-affecting callback remain server-thread confined. Weather's field-masked network migration remains deferred until measurement justifies a compatibility-preserving change to its full-snapshot recovery stream.

Water Phase 3 is the third production integration. `water_runtime` centrally polls once per eligible tick and creates separate session-local coalescing keys for each loaded dimension's regional state, shoreline, SPH snapshot, canonical-volume snapshot, regional network, and persistence work. Their normal eligibility cadences remain 1, 1, 4, 10, 20, and 20 ticks respectively; Tick Engine pressure may slow the central non-suspendable poll to 100 ticks. Each completed lane records its own cadence, so deferred work runs once after recovery rather than replaying every missed interval. Data Engine-off fallback executes the same owners synchronously while checking the live time allowance between lanes. Direct SPH physics remains outside the optional queue and on the logical server thread, and all water owners retain their existing loaded-chunk, SavedData, payload, and lifecycle rules. No worker-thread water phase or measured speedup is claimed: the inspected regional and SPH paths currently depend on live server-owned state, while the pure shoreline grid is small enough that mandatory copying would need profiling evidence before it could justify offloading. Labs, ordinary mobs/block entities, chunks, and other existing payloads retain their current behavior until each eligible owner is migrated and profiled separately. An architecture regression test rejects chunk-lifecycle APIs in the optimization packages and rejects optimization-governor dependencies from the worldgen and mixin packages.

Phase 4 adopts the Data Engine's real network transport for Reactive
Vegetation's initial per-player chunk snapshots. The existing payload codec is
wrapped in complete-snapshot deltas, coalesced by dimension and chunk, and
flushed in bounded packets after at most the configured delay. Client application still passes
through the existing dimension and monotonic-revision checks. Immediate visual
changes remain direct, and either performance master switch-off, Data Engine
batching switch-off, or bounded-queue rejection falls back to the original
direct payload. Per-system network counters expose the actual Phase 4 batch,
entry, and estimated-byte totals. This does not replace vanilla networking or
chunk tracking, and correctness/build evidence is not a claimed multiplayer
performance improvement.

The loaded-server gate is `WeatherDataEngineGameTests`. It runs through the real dedicated GameTest lifecycle and ordinary production schedulers, then requires an authoritative weather cell revision/simulation-tick advance, a positive shared-worker submission and completion delta, positive worker processing time, and no rejection or engine-failure delta. The same test requires the Phase 3 `water_runtime` metric bucket, positive centrally processed water work, and no water failure delta. The surrounding server run confirms Data Engine initialization and normal shutdown. This proves ownership, threading, registration, apply, and lifecycle behavior; it does not establish a before/after MSPT, allocation, network, or player-count improvement.

For a large-modpack validation pass, profile the Tick Engine's measured full-event MSPT against Spark/Minecraft timing, watch both bounded queue pressures, confirm recovery does not release all deferred work together, and verify direct player actions remain immediate at every pressure level.
