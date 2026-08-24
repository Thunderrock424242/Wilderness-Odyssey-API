# Wilderness Odyssey Simulation Engine

## Purpose

The top-level `simulation` package is the orchestration boundary for Wilderness
Odyssey's living-world systems. It answers a narrow question:

> Which optional simulation participants should evaluate this already-relevant
> region, using conclusions supplied by the systems that own the world state?

It does **not** merge the existing systems into a new global simulator. Weather,
water, ecosystem, vegetation, meteors, Riftfall, the Data Engine, and the Tick
Engine keep their existing ownership and lifecycle responsibilities.

The first foundation provides:

- deterministic registration for optional regional participants;
- bounded, deduplicated regional requests;
- one immutable context composed through current public service boundaries;
- typed consequence notifications adapted from `WorldDisturbanceService`;
- Data Engine scheduling and Tick Engine pressure admission;
- per-system failure isolation and timings;
- server lifecycle/config-reload cleanup;
- `/wo simulation status`, `/wo simulation region`, and integrated-server F3
  diagnostics.

The foundation deliberately registers no empty weather/water/vegetation
participants. Those owners already run their required work correctly. A
`SimulationSystem` should be added only when there is real optional regional
work to coordinate.

## Existing functionality reused

Several concepts proposed for a Simulation Engine already existed before this
package was introduced:

| Need | Existing authority reused |
| --- | --- |
| Combined regional climate and hazards | `EnvironmentServices.query()` and `RegionalEnvironmentSnapshot` |
| Typed cross-system disturbances | `WorldDisturbanceService` and `WorldDisturbanceType` |
| Wildlife ACTIVE/NEAR/DISTANT/DORMANT policy | `EcosystemSimulationManager` and `EcosystemZoneClassifier` |
| Player-interest indexing and network filtering | Data Engine `InterestManager` |
| Bounded cadence, queues, dirty coalescing, and async snapshot work | Data Engine |
| MSPT pressure, optional-work budgets, and adaptive throttling | Tick Engine |
| General background work and elapsed-time helpers | Background Efficiency APIs |
| Regional weather conclusions | `WeatherServices.query()` |
| Water and watershed conclusions | `WaterServices.access()` through the environment snapshot |
| Vegetation climate/disturbance conclusions | `ReactiveVegetationServices` through the environment snapshot |
| Meteor sites and Riftfall stage | their existing public services through the environment snapshot |
| Paged F3 diagnostics | the existing debug-overlay providers/pages |

Creating another weather bridge, water bridge, vegetation bridge, event queue,
player-interest manager, async executor, or region persistence layer would
duplicate those responsibilities. The Simulation Engine therefore adapts the
existing boundaries instead.

## Architecture

```text
Existing authoritative systems
  Weather | Water | Ecosystem | Vegetation | Meteor | Riftfall
        |
        v
EnvironmentServices + ecosystem regional/LOD APIs
        |
        v
Immutable SimulationContext / SimulationSnapshot
        |
        v
Simulation Engine
  - deduplicates known relevant regions
  - evaluates registered optional participants
  - isolates failures and records timings
  - exposes typed consequence notifications
        |
        v
Data Engine admission + Tick Engine pressure/budget
        |
        v
Authoritative subsystem APIs validate and apply their own results
```

The data flow is owner-first:

1. An owner updates its canonical state through its established lifecycle.
2. `EnvironmentServices` composes short-lived conclusions without taking
   ownership.
3. The Simulation Engine creates a regional context only for a player-occupied
   or explicitly requested cell.
4. Registered optional participants decide cheaply whether the context matters.
5. A participant calls only the public API of the subsystem that owns any final
   mutation.
6. Expensive pure calculations may use the Data Engine's immutable
   snapshot/compute/validate/apply path.

## Authority boundaries

### Weather

Weather remains the server-authoritative source for atmosphere cells, storms,
precipitation, temperature, humidity, wind, forecasts, lightning eligibility,
and severe-weather progression. The Simulation Engine reads the weather values
already present in `RegionalEnvironmentSnapshot`; it does not advance the
atmosphere or infer storms from client effects.

### Water

The water system remains authoritative for canonical water volume, watershed
state, local flow, flooding, groundwater/surface conclusions, tides, and water
mutation. The Simulation Engine does not place or remove water and does not
force a watershed/chunk to load.

### Ecosystem

`EcosystemSimulationManager`, `DistantWildlifeSavedData`, and the existing
behavior services retain wildlife LOD, real-versus-abstract representation,
population, group behavior, and migration ownership. The
`EcosystemSimulationBridge` performs only three read/adaptation operations:

- align a request key to the established 64-by-64 ecosystem cell;
- map wildlife ACTIVE/NEAR/DISTANT/DORMANT to the existing performance
  `ActivityLevel` vocabulary;
- expose the immutable `EcosystemRegionSnapshot` when the owner has one.

It does not alter animals or population ledgers.

### Vegetation

Reactive Vegetation keeps climate attachments, the loaded-chunk scheduler,
plant registration, work budgets, and final mutation authority. The Simulation
Engine reads the vegetation climate/disturbance fields already composed by
`EnvironmentServices`.

### Meteors and Riftfall

`MeteorSiteServices` remains the indexed source for successful meteor sites.
`RiftfallSystem` retains its stage clock and optional gameplay progression. The
Simulation Engine reads their conclusions and never recreates their state
machines.

## Relationship to `EnvironmentServices`

`EnvironmentServices` is the primary read boundary for regional environmental
context. The coordinator samples it once per processed request and supplies the
result to every participating `SimulationSystem`. This prevents each
participant from independently querying weather, watershed, vegetation, meteor,
and Riftfall state.

`RegionalEnvironmentManager` remains a read-only, short-lived chunk cache. The
Simulation Engine does not replace it with another environment cache.

## Relationship to `WorldDisturbanceService`

`WorldDisturbanceService` remains the successful-consequence publisher. It
continues to:

- record ecosystem environmental memory;
- request ecosystem regional refresh;
- record vegetation-owned disturbance pressure;
- invalidate regional environment conclusions.

After those established consequences are published, the service adapts the
same fact into an immutable `WorldSimulationEvent`. The Simulation Engine:

- dispatches the event synchronously on the server thread to typed listeners;
- isolates a failed listener;
- records event/listener diagnostics;
- coalesces a regional optional-work request when at least one
  `SimulationSystem` is enabled.

The dispatcher is intentionally small. It is not another task scheduler and it
does not replace NeoForge's general event bus. A simulation event is a fact, not
permission to mutate another owner's private state.

Current typed disturbances remain:

- lightning;
- severe weather;
- wildfire;
- flood;
- drought;
- meteor impact;
- radiation;
- Riftfall.

Future gunshot, machinery, vehicle, alarm, combat, animal-call, or structure
failure producers should first publish through the appropriate existing
world/disturbance authority. They should not add a new global sound/entity/block
scan.

## Relationship to the ecosystem LOD model

The common orchestration vocabulary uses the existing performance
`ActivityLevel` values:

| Simulation meaning | Ecosystem owner value | Common activity value |
| --- | --- | --- |
| Detailed, player-near work | `ACTIVE` | `ACTIVE` |
| Real entities with reduced evaluation | `NEAR` | `NEARBY` |
| Coarse/abstract work | `DISTANT` | `BACKGROUND` |
| Analytical catch-up only when relevant | `DORMANT` | `DORMANT` |

The initial request key uses the ecosystem's established 64-by-64 cell size so
overlapping players request one shared regional evaluation. This key exists only
for deduplication; it does not force weather, water, vegetation, meteors, or any
future system to adopt ecosystem distances or persistence.

Only the player-occupied cell is added during the periodic pass. The engine does
not construct a radius grid around every player. Disturbances and explicit
callers may request other known positions.

## Relationship to the Data Engine

The Simulation Engine registers `wildernessodysseyapi:simulation_orchestration`
as a background Data Engine system. The Data Engine supplies:

- central cadence;
- server spare-time admission;
- queue/backpressure behavior for future participant work;
- the existing bounded async snapshot/compute/validate/apply bridge;
- subsystem metrics already visible on the Data Engine debug page.

The Simulation Engine does not create a thread pool. A participant that needs
pure expensive computation should:

1. call `context.immutableSnapshot()` on the server thread;
2. submit that data through `DataEngine.runAsync(...)` under its own stable Data
   Engine system ID;
3. compute using only immutable/copied inputs;
4. revalidate dimension, region, generation/version, and owner state on the
   server thread;
5. ask the authoritative owner to apply the result.

Workers must never retain `SimulationContext.level()`, chunks, entities,
players, live registries with thread affinity, or mutable owner storage.

## Relationship to the Tick Engine

The Simulation Engine registers an optional background Tick Engine policy. The
Tick Engine determines the effective cadence from MSPT pressure, regional
activity, and gradual recovery. A pass processes at most:

- 16 regions while relaxed;
- 8 while busy;
- 4 under high pressure;
- 0 under critical or overloaded pressure.

This suspension is safe because the `SimulationSystem` contract is explicitly
for optional orchestration. Mandatory weather clocks, water safety, entity
restoration, Riftfall clocks, persistence, and other gameplay invariants must
not be moved behind this contract.

The coordinator also passes one elapsed-tick value from the last processed
regional state. It never expands missed time into a catch-up loop. Distant or
dormant systems may use that value analytically when their model supports it.

## Regional model and bounds

`SimulationRegionManager` stores two bounded transient structures:

- at most 2,048 pending region requests;
- at most 4,096 recently processed regional states.

Requests with the same dimension/cell identity coalesce. A later world
disturbance takes precedence over a player-interest request. When the pending
bound is full, new optional work is explicitly rejected and counted; no caller
runs the work inline.

Recent state is an access-ordered diagnostic/elapsed-time cache. It is not saved
to disk and is never treated as canonical simulation state. Per-dimension state
is removed on level unload and all state is removed on server shutdown/start.

The manager never:

- scans the world;
- enumerates unloaded chunks;
- force-loads a chunk;
- creates one object per block;
- creates a player-radius cell grid;
- replays one update per missed tick;
- duplicates work for players occupying the same cell.

## Extension API

A real optional regional participant registers once during common setup or
another stable bootstrap point:

```java
SimulationServices.register(new SimulationSystem() {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "wildernessodysseyapi", "population_ecology"
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        return PopulationEcologyConfig.enabled();
    }

    @Override
    public boolean shouldUpdate(SimulationContext context) {
        return context.activity() != ActivityLevel.DORMANT
                || context.elapsedTicks() > 0L;
    }

    @Override
    public void update(SimulationContext context) {
        SimulationSnapshot input = context.immutableSnapshot();
        // Submit pure calculation through the existing Data Engine if needed.
        // Revalidate and apply through the population owner's API on the server thread.
    }
});
```

Important extension rules:

- IDs must be unique; duplicates fail registration immediately.
- Ordering is deterministic by full resource-location string.
- `isEnabled()` and `shouldUpdate(...)` must be cheap.
- One participant failure is isolated and counted; it does not stop later
  participants.
- A participant may read another owner's public snapshot, but must not reach
  into private maps/saved data or mutate it directly.
- Event callbacks are synchronous and bounded; expensive responses request
  regional work or use the Data Engine.
- A participant that owns transient state must clear it in `onLevelUnload` and
  `onServerStopping`.
- A participant must react to reload-backed values in
  `onConfigurationReload` without silently discarding canonical state.

## Lifecycle

### Server start

`ServerLifecycleEvents` starts the Async Manager, Background Efficiency, Tick
Engine, and Data Engine first. It then starts the Simulation Engine and
registers its Data Engine lane. Registered system definitions receive
`onServerStarted` after those execution services exist.

### Server tick

The Data Engine polls the simulation lane every five ticks. The Tick Engine
selects the effective 20-tick-or-slower pass cadence. A pass deduplicates current
player cells and drains only the pressure-dependent region cap.

### Config reload

NeoForge config reload dispatch calls `SimulationEngine.onConfigurationReload`
on the logical server thread. The next pass becomes immediately eligible and
each system receives an isolated reload callback. Structural bounds remain
fixed for the current foundation; no pending canonical state is discarded.

### Level unload

The engine removes pending/recent entries for exactly that dimension and calls
each participant's dimension cleanup hook. It does not clear other loaded
dimensions.

### Server shutdown

Participant cleanup runs before the Data Engine and Tick Engine are stopped.
The regional queue and world-derived state are cleared. Registration
definitions remain process-scoped so a later integrated-server world can start
cleanly without duplicate bootstrap registration.

## Debugging and observability

`/wo simulation status` reports:

- running state;
- registered/enabled systems;
- typed event listeners;
- Tick Engine pressure;
- pending and tracked regions;
- ACTIVE/NEAR/DISTANT/DORMANT totals;
- accepted, coalesced, and rejected requests;
- processed regions and deferred passes;
- event/listener/system failures;
- last pass time;
- per-system updates, skips, failures, total time, and last time.

`/wo simulation region` samples the command source's current position through
`EnvironmentServices` and the ecosystem bridge. It reports common activity,
abstract ecosystem groups/population, habitat, water, wildlife activity,
migration pressure, vegetation stress, and hazard without mutating the world.

The existing paged F3 Performance page includes a `WO SIMULATION ENGINE`
section for an integrated server. Remote servers use the authoritative command;
simulation metrics are not broadcast continuously merely to populate F3. The
existing World page continues to display the synchronized shared environment,
and the Data Engine page continues to display scheduling/backpressure metrics.

## Performance rules

Every current and future participant must preserve these invariants:

- no whole-world scans;
- no unloaded-chunk enumeration;
- no optional chunk force-loading;
- no per-block simulation grid or object;
- no global entity scan per simulation tick;
- no duplicate regional work for overlapping players;
- no unbounded queues, caches, persistence, or catch-up loops;
- no caller-runs fallback when a bounded queue rejects work;
- no worker access to live Minecraft state;
- no direct cross-owner mutation;
- no mandatory gameplay invariant behind optional budget admission;
- no per-tick allocations proportional to world size.

Profiling should identify a real hot path before introducing more caching,
parallelism, or cadence complexity.

## What the foundation does not do

This phase does not implement:

- population ecology or carrying-capacity state;
- food webs or predator/prey population equations;
- persistent migration routes, territories, nests, or dens;
- carcasses, scavenging, decomposition, or disease;
- a wildfire model;
- NPC survivors, factions, settlements, or infrastructure;
- power grids, generators, facilities, or generalized acoustics;
- a new weather/water/vegetation/meteor/Riftfall scheduler;
- a new persistent region database;
- a new async executor;
- remote continuous simulation-debug synchronization.

## Recommended next feature

The best first real participant is **coarse population ecology for existing
distant wildlife groups**. It already has bounded persistent group ownership,
regional ecosystem snapshots, analytical elapsed-time motion, and explicit
real-versus-abstract invariants. A first iteration can calculate regional
carrying pressure from `SimulationSnapshot.environment()` and
`ecosystem()` without creating entities, scanning chunks, or taking ownership
away from `DistantWildlifeSavedData`. Any population change should be validated
and committed by the ecosystem owner on the server thread.

That feature will exercise the registration, regional deduplication,
elapsed-time, Data Engine async, Tick Engine pressure, lifecycle, and diagnostics
contracts without forcing weather, water, vegetation, or Riftfall to adopt a
new simulation model.
