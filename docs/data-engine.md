# Wilderness Odyssey Data Engine

## Purpose

The Data Engine is a reusable, server-owned efficiency layer for Wilderness Odyssey systems. It reduces how often work is created and how much state is moved before trying to make individual calculations faster.

Minecraft and NeoForge still own the authoritative server tick, worlds, chunks, entities, networking lifecycle, and thread rules. The Data Engine is not a second game tick engine. Its scope is Wilderness-owned scheduling, dirty-state tracking, bounded work queues, final-state coalescing, caching, player interest, delta batching, safe asynchronous calculation, and measurement. It never loads, retains, sends, unloads, tickets, force-loads, or governs generation of Minecraft chunks.

The root service is `DataEngine.get()`. Mutable API calls are server-thread-only unless their Javadoc explicitly says otherwise.

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

The shared async executor is configured in `wildernessodysseyapi-async.toml`; Data Engine limits its own in-flight use separately. Executor shutdown remains owned by the existing `AsyncTaskManager`, while Data Engine clears pending calculation results and world references before that shared executor is stopped.

## Configuration

Server config: `config/wildernessodysseyapi/wildernessodysseyapi-data-engine-server.toml`

| Setting | Default | Meaning |
| --- | ---: | --- |
| `dataEngine.enabled` | `true` | Master processing switch |
| `dataEngine.tickBudgetMs` | `2.0` | Shared non-critical main-thread budget |
| `dataEngine.maxQueueSize` | `10000` | Pending update action bound |
| `dataEngine.maxDirtyEntries` | `10000` | Active dirty-key bound |
| `dataEngine.maxCompletedTasks` | `1024` | Worker results waiting to apply |
| `dataEngine.maxAsyncInFlight` | `128` | Data Engine use of shared workers |
| `dataEngine.networkBatching` | `true` | Enables delayed small-delta grouping |
| `dataEngine.maxBatchEntries` | `256` | Per-packet delta entry bound |
| `dataEngine.maxBatchBytes` | `32768` | Approximate per-packet byte bound |
| `dataEngine.maxBatchDelayTicks` | `2` | Collection delay before a batch is due |
| `dataEngine.maxPendingNetworkBytes` | `8388608` | Global pending network-memory bound |
| `dataEngine.interestManagement` | `true` | Enables spatial recipient filtering |
| `dataEngine.defaultCacheMaxEntries` | `4096` | Default registered-cache LRU bound |
| `dataEngine.metrics` | `true` | Enables counter/timing collection |
| `dataEngine.debugLogging` | `false` | Enables rate-limited lifecycle/backpressure detail |

Queue and async structural bounds changed during a live config reload apply on the next server start so existing work is not discarded. Budget, batching, interest, metric, and logging settings update live.

## Metrics and debugging

The engine measures submissions, processed/coalesced/failed updates, dirty and queued gauges, queue peak, network batches/entries/estimated bytes, cache hits/misses/entries, async submitted/completed/rejected/queued work, main and worker processing time, interest filtering, dropped/superseded background work, backpressure, and per-system totals.

Commands require permission level 2:

```text
/wo dataengine stats
/wo dataengine resetstats
/wo dataengine benchmark
```

The benchmark is an isolated 512-submission in-memory workload. It does not enqueue gameplay state or send packets. Its timing is a development observation, not a claimed production before/after improvement.

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

1. Ecosystem: move active-region cadence and expensive immutable-snapshot calculations first; retain server authority and existing persistence.
2. Weather: migrate dirty weather cells and recipient-first field deltas; retain the current `WeatherAuthority` and client interpolation ownership.
3. Water: integrate only proven hot synchronization/calculation boundaries; do not replace canonical water storage, vanilla-fluid compatibility, or render-thread upload ownership.
4. Networking-heavy systems: move repeated compatible small payloads to explicit codecs and bounded batches one subsystem at a time.
5. Aether and analytics: use event-driven aggregation and shared async workers, with bounded persistence queues.
6. Labs/power: convert power changes into explicit server-owned events driving doors/lights/alarms; do not poll every component every tick.

Each migration should add subsystem-specific correctness tests and measure actual server tick, allocation, and network changes before claiming performance gains.
