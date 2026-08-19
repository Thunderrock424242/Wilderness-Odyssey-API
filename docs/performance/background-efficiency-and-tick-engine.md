# Background Efficiency Framework and Tick Engine

## Scope and ownership

These packages govern only work that Wilderness Odyssey explicitly opts into:

- `performance/background` owns general deferred work, activity classification, safe snapshot computation, networking batches, analytics/IO batches, and framework metrics.
- `performance/tickengine` measures server pressure and governs optional WO work through tick budgets, deferred tick tasks, adaptive intervals, and explicit missed-tick policies.

Neither package replaces Minecraft's tick loop. They do not cache world state, add dirty flags, skip vanilla ticks, alter random ticks, change TPS, or throttle arbitrary vanilla/modded entities and block entities. No mixin is used.

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

The final two values define the longest interval required when suspension is forbidden and whether full temporary suspension is safe. The engine includes default registrations for `weather`, `ecosystem`, `water`, `labs`, `aether`, `analytics`, `worldgen`, `structures`, and `network`; these registrations do not automatically change those systems.

WO block entities may use `AdaptiveBlockEntityTicker` around only their expensive custom section. WO entities may use `AdaptiveEntityWork` around optional AI decisions, target analysis, or environment scanning. Never place base ticking, physics, combat, capability safety, or direct player interactions behind these helpers.

## Metrics and debug UI

`BackgroundMetrics.snapshot()` exposes queue counts, task outcomes, async state, batch backlogs, activity gauges, and aggregated per-subsystem time. Systems that own a known set of regions or objects can publish current activity gauges with `setActivityCount`.

`TickEngine.snapshot()` exposes TPS, current/rolling/worst MSPT, pressure, budget use, deferred queue pressure, background queue pressure, throttled subsystem count, and subsystem timings. Integrated singleplayer displays a `WO TICK ENGINE` section on the existing Performance F3 page. Dedicated/remote clients deliberately show that server metrics are not synchronized; no new debug packet or permission surface was added.

## Compatibility and rollout

The only runtime integrations in this implementation are config registration, server start/stop lifecycle, the pre/post tick event bridge, the background-budget bridge, and integrated-server debug presentation. Weather, ecosystem, water, labs, mobs, block entities, and existing payloads retain their current behavior until each owner is migrated and profiled separately.

For a large-modpack validation pass, profile the Tick Engine's measured full-event MSPT against Spark/Minecraft timing, watch both bounded queue pressures, confirm recovery does not release all deferred work together, and verify direct player actions remain immediate at every pressure level.
