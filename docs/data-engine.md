# Wilderness Odyssey Data Engine

## Purpose

The Data Engine is a reusable, server-owned efficiency layer for Wilderness Odyssey systems. It reduces how often work is created and how much state is moved before trying to make individual calculations faster.

Minecraft and NeoForge still own the authoritative server tick, worlds, chunks, entities, networking lifecycle, and thread rules. The Data Engine is not a second game tick engine. Its scope is Wilderness-owned scheduling, dirty-state tracking, bounded work queues, final-state coalescing, caching, player interest, delta batching, safe asynchronous calculation, and measurement. It never loads, retains, sends, unloads, tickets, force-loads, or governs generation of Minecraft chunks.

The root service is `DataEngine.get()`. Mutable API calls are server-thread-only unless their Javadoc explicitly says otherwise.

## Production integrations

The ecosystem is the first production adopter. `ecosystem_runtime` uses the central scheduler for periodic loaded-wildlife scans and distant-population maintenance, and it uses dirty-state coalescing for login, respawn, dimension-change, and config-driven client refreshes. Tick Engine pressure and activity policy may defer that optional maintenance; player-driven zone changes and manager-owned AI restoration remain on the ordinary server tick. The existing distant-wildlife payload codec and SavedData authority are deliberately unchanged in this first phase.

Weather is the second production adopter. `weather_runtime` converts the configured simulation/snapshot cadence into one central schedule and then submits one coalescible callback per loaded level, allowing the Data Engine to recheck Minecraft's live time allowance between dimensions. Phase 2B captures biome/water/environment inputs, frozen neighborhoods, persistent-system influence, config, and cell revisions on the server thread; only the detached atmospheric math runs through the shared worker pool. `WeatherAuthority` validates the level generation and the complete captured revision set before applying the batch, updating tracked systems, or touching SavedData. One calculation is tracked per level, failed/dropped work becomes due again after a bounded timeout, and explicit worker backpressure retains work for a later poll instead of caller-running it. Due snapshot publication is deferred behind an accepted calculation and enters the dirty queue under a separate session-local key; the existing bounded `WeatherRegionSyncPayload` v4 and per-player revision manager remain the wire authority. Tick Engine pressure can expand the polling interval, but weather is non-suspendable and a deliberately slower operator-configured interval is never shortened. Lightning, wildfire, surface weathering, severe entity/block effects, and survival bridges stay on the server thread and recheck the live allowance between callbacks. When the Data Engine is disabled, the same authority uses its synchronous, bounded direct fallback.

Water is the third production adopter. `water_runtime` splits optional per-level maintenance into distinct coalescing lanes for regional sea/hydrology state, shoreline flow, local SPH snapshots, canonical-volume snapshots, regional network publication, and periodic SPH persistence. The central queue therefore rechecks Minecraft's live time allowance and the shared Data Engine budget between units instead of one event handler running every due water owner as a block. Successful completion advances each lane independently; delay, queue coalescing, or tick-counter rollback creates no catch-up loop. Tick Engine pressure may expand the central poll to at most 100 ticks, but the non-suspendable water policy ensures that persistent gameplay state continues to advance. Disabling the Data Engine uses the same bounded lanes synchronously and rechecks the live allowance before each one. Canonical water storage, watershed and sea-state SavedData, existing payload codecs, player-interest bounds, and loaded-chunk-only rules remain owned by their established subsystems. Gameplay-critical SPH collision, motion, and settlement remain on the direct server tick because they read and mutate live Minecraft state; no water worker-thread migration is claimed without a detached snapshot boundary and representative profiling evidence.

Reactive Vegetation is the Phase 4 production networking adopter. Its burst of
initial climate snapshots created by normal chunk tracking is encoded with the
existing `ReactiveVegetationSyncPayload` codec and queued as normal-priority
`reactive_vegetation` deltas. Entries for the same player, dimension, and chunk
coalesce to the newest complete snapshot, then share the bounded
`DataPacketBatch` transport. The client handler decodes back into the existing dimension-aware,
revision-checked vegetation store; the Data Engine does not own chunk tracking
or plant state. Visual changes after initial tracking remain direct packets so
an already visible chunk is not deliberately delayed. A disabled engine,
disabled `networkBatching`, or a rejected bounded-queue submission uses the
original direct payload immediately. Network protocol version 22 makes the new
client handler a required connection capability instead of silently accepting
an incompatible client.

## Pipeline

```text
Wilderness state change
        -> markDirty(system, key)
        -> active dirty collection (no global scan)
        -> bounded priority queue
        -> duplicate final-state updates coalesce
        -> scheduled/server-thread handler
        -> spatial or explicit interest filter
        -> changed-field delta
        -> bounded per-player packet batch
        -> NeoForge client payload handler
```

Critical individual events bypass the Data Engine's non-critical time budget and batching delay, but they never bypass NeoForge's live `ServerTickEvent.hasTime()` allowance. Once Minecraft reserves the remaining tick for its own work, including chunk IO and generation completion, critical Data Engine items stay queued for a later tick. They still obey hard capacity and packet-size safety bounds. If even a critical item cannot be accepted, the API returns `false` and logs/rates the backpressure event instead of silently losing it. The authoritative owner should retain its own dirty state or retry safely.

## Registering a system

Register systems after `DataEngine.start(...)` during the current server lifecycle. Registration creates a per-system metric bucket and optionally adds one central schedule.

```java
public static final ResourceLocation WEATHER_CELLS =
        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "weather_cells");

DataEngine engine = DataEngine.get();
engine.registerSystem(DataSystemRegistration.builder(WEATHER_CELLS)
        .frequency(UpdateFrequency.FAST)
        .intervalTicks(() -> WeatherConfig.cellIntervalTicks())
        .priority(UpdatePriority.HIGH)
        .onScheduledUpdate(server -> WeatherCells.scheduleActiveCells(server))
        .onDirtyUpdate((server, dirty) -> WeatherCells.processFinalState(server, dirty.objectKey()))
        .build());
```

The interval supplier is evaluated on the server thread after every run, so a config reload can change cadence without scattered modulo checks or registration replacement. Missed intervals do not create a catch-up storm: a system is scheduled from the current tick after it becomes due.

Use `EVENT_ONLY` when the owner should run solely in response to explicit state changes.

## Dirty state and queued work

```java
weatherCell.setRain(newRain);

boolean accepted = DataEngine.get().markDirty(
        WEATHER_CELLS,
        weatherCell.packedId(),
        "rain intensity changed",
        UpdatePriority.HIGH
);
```

Dirty entries are pushed into active priority collections. Marking the same system/key again updates its existing reason, tick, mark count, and highest priority without adding another active node.

Dirty handlers represent final state. They may safely coalesce. Individual interactions, damage, transactions, and other irreversible events must use non-coalescible actions:

```java
DataEngine.get().submit(QueuedUpdate.event(
        LAB_DOORS,
        interactionSequence,
        UpdatePriority.CRITICAL,
        server.getTickCount(),
        () -> doorController.activate(playerId, doorId)
));
```

The queue is bounded. When full, it may evict only coalescible lower-priority work for more urgent input. It never treats irreversible event nodes as supersedable.

## Changed-field deltas and batching

Each system defines its own stable changed-field bits and compact body codec. Reflection, JSON, and generic NBT are not used by the hot transport.

```java
long changedFields = RAIN_CHANGED;
byte[] body = WeatherRainDeltaCodec.encode(cell.rain());

DataDelta delta = new DataDelta(
        WEATHER_CELLS,
        weatherCell.packedId(),
        changedFields,
        UpdatePriority.HIGH,
        body
);

DataEngine.get().sendDelta(region, weatherInterestProfile, delta);
```

Pending deltas coalesce only when `systemId`, `targetKey`, and `changedFields` are identical. This makes `rain .51 -> .54 -> .63` become one `.63` body while preventing an unrelated pressure or lifecycle field from being lost. A producer that wants a later multi-field delta to supersede an earlier one should accumulate those fields in its own explicit codec/state before submission.

`DataPacketBatch` uses resource ids, VarLong target/field values, a compact priority byte, and length-prefixed binary bodies. Output obeys configured entry and approximate-byte bounds plus stricter hard decode limits. Critical deltas may bypass delay as one bounded packet.

Client systems register an explicit handler with `DataDeltaHandlerRegistry`; the handler owns decoding and application for its system id. Client handlers must never infer or mutate authoritative server state.

## Player interest

`InterestManager` maintains player positions in eight-chunk coordinate buckets once per eligible server tick. These are ordinary in-memory recipient-index keys: they do not query chunk objects, influence vanilla chunk packets, or create/retain chunk tickets. Region sends query intersecting buckets rather than comparing every world object with every connected player.

```java
InterestProfile profile = new InterestProfile(
        4,   // NEAR: full detail
        12,  // REGIONAL: reduced rate/detail
        32   // DISTANT: summarized state
);

InterestRegion region = new InterestRegion(dimensionId, cellChunkX, cellChunkZ);
InterestTier tier = engine.interest().classify(player, region, profile);
```

Wrong-dimension players always receive `NONE`. Systems choose their own radii. Non-spatial data can use explicit feature subscriptions, as the proof integration does for the operator debug page.

## Caching

Typed caches are registered under an owning system and unique name:

```java
DataCache<ClimateCellKey, ClimateSnapshot> climateCache =
        engine.registerCache(CLIMATE, "regional_climate", 4096);

climateCache.put(key, immutableSnapshot, 20L, server.getTickCount());
Optional<ClimateSnapshot> cached = climateCache.get(key, server.getTickCount());
```

Caches are bounded access-order LRU maps. Tick expiration is checked on lookup, avoiding an every-tick expiration scan. They are thread-safe, but cached values must themselves be immutable or correctly confined.

## Asynchronous calculation and immutable snapshots

Data Engine worker tasks reuse the mod's existing CPU executor. The Data Engine does not create another executor or leak another thread pool. Its submission path is non-blocking and never falls back to running the calculation on the Minecraft thread when the shared worker queue is saturated.

```java
ClimateInput snapshot = ClimateInput.copyMinimumFrom(serverOwnedCell);
long expectedRevision = cell.revision();

engine.runAsync(CLIMATE, "ClimateCell", UpdatePriority.NORMAL, true,
        new AsyncDataTask<ClimateResult>() {
            @Override
            public ClimateResult compute() {
                // WORKER SAFE: snapshot only; no level/chunk/entity access.
                return ClimateMath.calculate(snapshot);
            }

            @Override
            public boolean isStillValid(ClimateResult result) {
                // SERVER THREAD ONLY.
                return cell.revision() == expectedRevision;
            }

            @Override
            public void apply(ClimateResult result) {
                // SERVER THREAD ONLY.
                cell.apply(result);
            }
        });
```

Thread contract:

- Minecraft server thread: create the smallest immutable input snapshot, register/mark/submit work, validate results, mutate worlds/entities, and queue network deltas.
- Worker threads: pure calculation, aggregation, planning, analytics, or compression preparation using immutable/copied input only.
- Client thread: decode registered deltas and update client presentation/snapshot stores only.

Do not capture a `Level`, chunk, entity, player, block entity, or other live Minecraft object and touch it from `compute()`. Validation exists because the world may change while calculation is running. Completed results are bounded and applied through a separate server-thread queue.

Localized weather follows this split literally: `SimulationBatch` contains only immutable records, copied sets/maps/lists, captured tracker influence, and scalar settings. Its worker task calls only the pure calculation entry point. Level/config generation changes, cell edits/removals, unloads, and superseded submissions reject the entire result before mutation so cell evolution and persistent-system observations cannot be partially mixed across generations.

The shared async executor is configured under `[asyncThreading]` in
`wildernessodysseyapi-common.toml`; Data Engine limits its own in-flight use
separately. Executor shutdown remains owned by the existing `AsyncTaskManager`,
while Data Engine clears pending calculation results and world references before
that shared executor is stopped.

## Configuration

Server config: `config/wildernessodysseyapi/wildernessodysseyapi-server.toml`

The file has one reversible `performance.enabled` master switch for all three
Wilderness performance engines. Data Engine retains its own subsection switch.
Neither switch disables or replaces Minecraft/NeoForge ticking, chunks,
entities, networking, saves, or other lifecycle owners.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `performance.dataEngine.enabled` | `true` | Data Engine processing switch |
| `performance.dataEngine.tickBudgetMs` | `2.0` | Shared non-critical main-thread budget |
| `performance.dataEngine.maxQueueSize` | `10000` | Pending update action bound |
| `performance.dataEngine.maxDirtyEntries` | `10000` | Active dirty-key bound |
| `performance.dataEngine.maxCompletedTasks` | `1024` | Worker results waiting to apply |
| `performance.dataEngine.maxAsyncInFlight` | `128` | Data Engine use of shared workers |
| `performance.dataEngine.networkBatching` | `true` | Enables delayed small-delta grouping |
| `performance.dataEngine.maxBatchEntries` | `256` | Per-packet delta entry bound |
| `performance.dataEngine.maxBatchBytes` | `32768` | Approximate per-packet byte bound |
| `performance.dataEngine.maxBatchDelayTicks` | `2` | Collection delay before a batch is due |
| `performance.dataEngine.maxPendingNetworkBytes` | `8388608` | Global pending network-memory bound |
| `performance.dataEngine.interestManagement` | `true` | Enables spatial recipient filtering |
| `performance.dataEngine.defaultCacheMaxEntries` | `4096` | Default registered-cache LRU bound |
| `performance.dataEngine.metrics` | `true` | Enables counter/timing collection |
| `performance.dataEngine.debugLogging` | `false` | Enables rate-limited lifecycle/backpressure detail |

Queue and async structural bounds changed during a live config reload apply on the next server start so existing work is not discarded. Budget, batching, interest, metric, and logging settings update live.

## Metrics and debugging

The engine measures submissions, processed/coalesced/failed updates, dirty and queued gauges, queue peak, network batches/entries/estimated bytes, cache hits/misses/entries, async submitted/completed/rejected/queued work, main and worker processing time, interest filtering, dropped/superseded background work, backpressure, and per-system totals. Per-system totals now include the real packet batches containing that system, its entry count, and its estimated encoded delta bytes; a mixed packet counts once for every represented system while the global total still counts one packet.

Commands require permission level 2:

```text
/wo dataengine stats
/wo dataengine resetstats
/wo dataengine benchmark
```

The benchmark is an isolated 512-submission in-memory workload. It does not enqueue gameplay state or send packets. Its timing is a development observation, not a claimed production before/after improvement.

`/wo dataengine stats` includes an `Async` line with submitted, completed, rejected, waiting-to-apply, and cumulative worker-time values. These counters make worker saturation and completion visible on a live server without enabling per-tick debug logging.

The existing paged F3 HUD includes `WO DATA ENGINE`. Only a permission-level 2 player actively viewing that page subscribes. Once per second, the engine schedules a debug update, marks its final snapshot dirty, coalesces it, filters to explicit subscribers, encodes one compact delta, and lets normal batching deliver it. Closing/changing the page sends one unsubscribe transition; there is no client request every tick and no metric packet for uninterested players.

## Lifecycle and failure isolation

- `ServerStartingEvent`: the existing async executor starts first, then Data Engine creates fresh per-server bounded state and registers internal integrations.
- `ServerTickEvent.Post`: the existing async handoff and Data Engine stages recheck the event's live `hasTime()` allowance before each queued callback or batch. A one-time true snapshot cannot authorize later work after Minecraft reserves the remaining tick.
- `ServerStoppingEvent`: water persistence/shutdown completes, Data Engine clears queues/caches/player references, then the shared async executor stops.
- Player disconnects disappear from the interest index on its next refresh.
- Dimension movement relocates a player between buckets.
- Integrated-server restarts receive a fresh engine lifecycle.
- Queued and async-apply failures identify their subsystem, increment failure metrics, log once per actual failure, remove the bad task, and continue.
- Recurring backpressure warnings are rate-limited and disabled unless debug logging is enabled.

## Recommended migration order

1. Ecosystem: first production phase integrated. Continue profiling the periodic scan and migrate only proven immutable calculations; retain server authority, existing packet compatibility, and population persistence.
2. Weather: Phase 2 is complete through Phase 2A scheduling/coalesced publication and Phase 2B immutable cell calculation. `WeatherDataEngineGameTests` closes the loaded-server correctness gate by creating real level-owned cells and asserting the normal schedule, one shared-worker completion, revision-checked server-thread apply, and zero rejection/failure delta; the same server run confirms Data Engine initialization and normal shutdown. This is correctness evidence, not a representative performance profile. Any later network phase is measurement-gated and must retain `WeatherAuthority`, payload-v4 full-snapshot recovery, and client interpolation ownership.
3. Water: Phase 3 is complete for bounded scheduling/coalescing and Tick Engine measurement. `WaterPerformanceIntegrationTest` covers cadence, pressure, key separation, overflow, first-run behavior, and tick rollback; the loaded-server integration test requires `water_runtime` registration, positive processed work, and no failure delta. This is correctness and lifecycle evidence, not a representative performance profile. Any future async water phase remains measurement-gated and must first define an immutable input, pure computation, and bounded revision-checked server-thread apply without replacing canonical water storage, vanilla-fluid compatibility, chunk ownership, or render-thread upload ownership.
4. Networking-heavy systems: Phase 4 is complete for Reactive Vegetation's bursty initial chunk snapshots. Keep later migrations subsystem-specific; weather recovery snapshots, water correctness-sensitive streams, and immediate gameplay changes retain their established transports until representative measurement justifies a compatible move.
5. Aether and analytics: use event-driven aggregation and shared async workers, with bounded persistence queues.
6. Labs/power: convert power changes into explicit server-owned events driving doors/lights/alarms; do not poll every component every tick.

Each migration should add subsystem-specific correctness tests and measure actual server tick, allocation, and network changes before claiming performance gains.
