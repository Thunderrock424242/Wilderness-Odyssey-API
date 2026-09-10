# Regional watersheds, rivers and funded flooding

`RegionalHydrologyManager` owns finite catchments behind the existing
`WatershedSimulationManager` lifecycle. The normal and legacy weather-hydrology
facades reach one scheduler, never two melt/ET/placement models.
See [conservation architecture](conservation-architecture.md) for ownership and
[atmospheric exchange](atmospheric-exchange.md) for exact ET receipts and SWE.

## Cached terrain and finite state

A 16x16 record persists SWE, soil, aquifer, runoff, river, lake, floodplain and
ice quantities, receipts, temperature, cached forcing and simulated time.
Generated water remains separate. New records start dry rather than deriving
fictitious amounts from old normalized fields.

Normal chunk load admits DEM and sixteen loaded soil/vegetation probes.
Block tags `hydrology_sand`, `hydrology_gravel`, `hydrology_clay`,
`hydrology_rock` and `hydrology_impermeable` select profiles; ordinary terrain
defaults to loam. All tags use the `wildernessodysseyapi` namespace.

Admitted state advances without players or loaded chunks. Only detailed metadata
and block projection require loaded terrain. Records beyond the admission cap
remain outside the finite model; lowering the cap never evicts old inventory.

## Drainage, rivers and lakes

An incremental D8 priority-flood graph propagates spill elevation, basin, outlet
and area across known cells. Unknown frontier stays closed until a known outlet
exists. An edited node invalidates the cached graph; rebuilding is time-sliced
and routing is withheld during rebuild. No unloaded terrain is sampled.

Each reach has a 16-metre length, accumulation-derived width, rectangular section
and configurable Manning roughness. Q is m3/s, velocity m/s, stage metres.
Normalized conditions and blocks-per-tick currents derive from these fields.
The older local terrain grid remains metadata, not another force multiplier.

Runoff and groundwater release gradually. Lakes fill below their spill capacity
before discharging. River bankfull or lake containment excess enters floodplain
storage. Closed basins retain excess rather than exporting it to unknown terrain.

## Detailed projection and protection

```text
actual river/lake excess -> regional floodplain
                                  |
                  reserve exact 4096-unit parcel
                                  v
safe connected loaded target -> canonical water + funded terrain claim
                                  |
                           matched recession
                                  v
                         funding region runoff
```

Flood expansion needs real floodplain inventory, a surface-water representative
and derived flood conditions. A bounded deterministic candidate window is
filtered for water connectivity and sorted low-first. This is priority placement
within the window, not a whole-basin terrain fill. Ponds/wetlands additionally use
sampled depressions and sinks; springs require groundwater/baseflow. All
projections must reserve actual water before placement.

Solid/nonreplaceable terrain, foreign fluids, block entities, protected tags,
structure starts and chunks with structure references are rejected. Story
starter-bunker bounds are checked separately. Air or explicitly tagged
replaceable plants may be occupied. No placement/outlet search loads chunks.

Claims preserve exact units, funding region and original terrain even when split
or moved across chunks. Ordinary water and incompatible funded claims cannot
mix implicitly. Buckets take ownership externally; withdrawn units cannot later
be credited back during recession.

Recession waits for excess storage to fall and respects lifetime rules. It
requires matching ledger, flag, projection and exact quantity, then removes
canonical units and returns them to funding-region runoff. Hidden displacement
reservoirs retain claims. Foreign overwrites relinquish only stale claims.
Saved plants restore only into empty terrain where they still survive.

## Configuration

Under `water_simulation.regional_hydrology`:

| Key | Default | Meaning |
| --- | ---: | --- |
| `rainfallMillimetresPerGameHour` | 12 | Rain depth at intensity one; game hour = 1000 ticks/50 simulation seconds. |
| `evaporationMillimetresPerGameHour` | 0.15 | Base potential ET before weather/availability modifiers. |
| `channelRoughness` | 0.045 | Manning n in seconds/metre^(1/3). |
| `bankfullDepthMetres` | 1.25 | Channel containment depth. |
| `aquiferResidenceSeconds` | 1800 | Baseflow timescale. |
| `maximumRegions` | 8192 | Admission cap, never an inventory eviction limit. |
| `topologyNodesPerTick` | 128 | Incremental graph work budget. |
| `catchupStepsPerRegion` | 4 | Coarse intervals per selected record. |
| `budgetToleranceMilliUnits` | 0 | Warning tolerance; 1000 milli-units = one canonical unit. |
| `thermalStorageEnabled` | true | Aggregate liquid/ice and temperature. |

Existing watershed controls retain region selection, temporary-cell caps,
placement/removal limits, groundwater enablement, transient lifetimes,
sediment/debris and synchronization budgets. Old normalized/probe-rate settings
remain for compatibility but do not create water or replace physical rain/ET
rates.

Catch-up uses two-second to one-hour intervals with exponential stores and
cached old forcing over old gaps. Work-capped elapsed time stays pending.
Current cached atmosphere is captured for the next interval after catch-up.
Stopping the Minecraft server stops simulation; offline wall time is not added.

## Diagnostics and testing

`/wowater budget [pos]` shows exact stores, boundaries, residual, forcing time,
Q/stage, funded claims and work counters. Existing watershed debug queries and
client snapshots remain available.

JUnit covers closed balance, projection reserve/rollback/return, soil/aquifer
memory, SWE, ET receipts, downstream conservation, depression spill, storm
hydrograph, priority-flood topology and persistence. Compiling a GameTest does
not prove its world behavior; see [the report](realism-upgrade-report.md).

In a disposable world:

1. Compare rain on sand and impermeable surfaces, soil/runoff and delayed
   baseflow. Monitor exact residual throughout.
2. Fill a closed depression then observe spill into an admitted lower outlet.
   Unknown frontiers must not cause chunk loads.
3. Follow storm rise, funded overflow and recession, comparing projection
   receipts to detailed claims.
4. Split/move/bucket temporary water; place a wall and edit a claimed plant.
   Check for duplicate returns or damage to player/structure blocks.
5. Save/reload wet state, leave and return, and vary work budgets while checking
   stored quantities, timestamps and catch-up backlog.
6. Check dedicated server/two clients for matching currents, snow appearance,
   reconnect, dimension unload and region transitions.

Gameplay, multiplayer and long-running performance proof remains distinct from
source, compilation and JUnit evidence.
