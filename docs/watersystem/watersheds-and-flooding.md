# Watersheds, Dynamic Rivers, and Localized Flooding

The watershed system adds believable chunk-scale hydrology without
replacing Wilderness water's generated spans, sparse canonical authority, or
client render ownership. It is intentionally not computational fluid dynamics.

The ownership path remains:

```text
Minecraft generation
  -> GenerationWaterStateMapper
  -> Wilderness fluid in ProtoChunk
  -> GeneratedWaterChunk vertical spans
  -> LevelChunk promotion and attachment sync
  -> WildernessWaterAuthority / WaterAccess
  -> immutable water and watershed client snapshots
  -> WaterRenderCoordinator
```

Watershed state describes the conditions affecting that water. It does not
become another source of physical volume truth.

## Compact data model

`WatershedSavedData` stores one entry per initialized chunk in a bounded
per-dimension map. Each entry contains seven packed condition/topology words plus basin,
representative-position, revision, timing, and flood-cursor values. Save data
and network payloads reuse the packed words.

The immutable `WatershedConditions` API exposes:

| Category | Values |
| --- | --- |
| Terrain | canonical basin ID, average elevation, eight-way downstream direction, drainage accumulation, 4 by 4 tributary cells |
| Rain/runoff | recent rainfall, recent snowmelt, soil saturation, stored runoff, downstream discharge |
| Surface | water-level offset, flood risk/threshold/state, active temporary cells |
| Appearance | sediment, clarity, floating-debris intensity |
| Movement | current X/Z and current strength |
| Classification | stream, river, lake, wetland, coastal, aquifer, or none |

Normalized fields use unsigned 16-bit quantization. Surface offset and current
components use bounded signed 16-bit quantization. No per-block watershed array
is stored. Each of the sixteen local drainage cells uses one four-bit direction
and one four-bit contributing-area value.

## Deterministic initialization

`WatershedServerEvents` initializes a chunk after it is available as a
`LevelChunk`. `WatershedTerrainInitializer` reads only:

- a fixed 4 by 4 local height lattice;
- fixed local edge and corner samples; and
- the existing compact `GeneratedWaterChunk` top spans.

It does not ask for neighboring chunks. The lowest measurable local edge picks
the cached downstream direction, while exposed generated spans classify the
water feature and supply one representative surface position.

Local basin IDs are seed-, dimension-, and 8-by-8-chunk-region-derived and
remain stable across reloads. `WatershedBasinSavedData` adds a versioned,
deterministic union table: when a loaded chunk drains into another loaded
region, their local IDs are aliased to the unsigned-minimum canonical ID. Chunk
load also checks only already-loaded upstream neighbors. The alias survives
reloads, but reconciliation never requests an absent chunk.

The same 4 by 4 height lattice produces `WatershedDrainageGrid`. Local cells
route to their steepest lower neighbor, accumulate contributing cells in
elevation order, and expose confluences without a block-resolution river graph.

## Rainfall, runoff, and drought

`WatershedSimulationManager` periodically queues loaded chunks inside the
configured player distance. A per-tick chunk budget drains that queue. Every
updated chunk samples the public immutable `WeatherQuery` at its representative
water position and advances `WatershedSimulationModel`.

The pure model:

1. accumulates liquid rain or hail into rainfall memory;
2. converts synchronized stored surface snow into delayed thaw runoff above freezing;
3. raises soil saturation over repeated passes;
4. converts more rain and snowmelt to runoff as saturation rises;
5. retains runoff when the cached downstream chunk is unavailable;
6. transfers a bounded share when the downstream chunk is already loaded;
7. approaches river discharge more quickly during buildup than decay;
8. derives gradual water-level, flood-risk, sediment, clarity, current, and
   debris targets; and
9. decays rainfall, snowmelt memory, and saturation safely when localized weather is disabled.

No neighbor is force-loaded. Outgoing runoff remains stored until a valid
loaded downstream state exists. Oceans stay level-neutral in this phase; local
weather-driven sea state and tides continue to own their existing ocean paths.

The former probe-based `WeatherHydrologyManager` remains as the compatibility
fallback only when watershed simulation is disabled. Both managers are never
active together, preventing duplicate rain credits.

## Dynamic surface and current behavior

`WaterAccess#getWatershedConditions` and `getLocalWatershedFlow` are the stable
public queries. The API version is 3. `WildernessWaterAuthority` and
`HybridWaterBodyModel` add the synchronized watershed offset and local-cell
current to the same generated surface, wave, tide, canonical current, and
local-disturbance calculation already used by gameplay.

Most changing level is metadata-driven:

- river/lake surfaces rise gradually during sustained wet conditions;
- drought exposes the upper part of generated banks by lowering the custom
  surface and gameplay immersion boundary;
- discharge adds directional current without scheduling vanilla fluid ticks;
- boats, floating items, and living entities sample raised surfaces and local tributary current through existing multi-point buoyancy;
- sediment darkens and reduces underwater visibility;
- fixed-cap foam, debris, and mist emitters provide river ambience without entities;
- sparse vanilla water accents respond to discharge, confluences, flooding, and local rain; and
- ecosystem water searches reject dangerous flood/current targets while animals seek shelter from local flood hazards.

The offset is bounded below one block by default. It does not rewrite whole
rivers, waterlogged hosts, heightmaps, aquatic generation, or structure water.

## Compact client synchronization

`WatershedRegionSyncPayload` sends nearby packed chunk conditions once per
second alongside the existing sea-state cadence. Its decoder rejects more than
the maximum 33 by 33 configured window. Clients atomically publish immutable
`WatershedConditions` in `ClientWatershedSnapshotStore`.

Only changed received conditions invalidate the affected water mesh and its
loaded cardinal neighbors. The existing generated/sparse water payloads are not
resent. Mesh rebuilds incorporate level offset, current, sediment, and clarity;
camera immersion consumes the same offset/current and increases turbidity from
the synchronized sediment value. Surface ambience consumes the same local cell
direction as the mesh and gameplay sample. A fixed 48-slot emitter pool handles
foam, debris, and mist; the river soundscape scans only a small fixed
neighborhood and plays at most one local vanilla accent per bounded cadence.

## Temporary floodwater safety

Actual block placement is reserved for gameplay-relevant overflow and is much
more conservative than the metadata surface.

`TemporaryFloodManager` requires all of the following:

- flooding is enabled and the chunk has a generated representative surface;
- flood risk has crossed the configured threshold;
- the candidate chunk is already loaded;
- the candidate is no more than one block above the representative surface;
- horizontally adjacent authoritative water already exists;
- the target is air, or is both normally replaceable and explicitly included
  in `wildernessodysseyapi:watershed_flood_replaceable`;
- the target is not in `wildernessodysseyapi:watershed_flood_protected`;
- the target has no block entity; and
- no valid structure start bounding box contains the target.

The default replaceable tag is empty, so ordinary flooding places only into
air. Packs may opt simple vegetation in. Version-two flood ledger entries store
the exact replaced block state and restore it after successful canonical
recession when it can still survive at the position. Legacy entries migrate
with air as their safe original-state fallback. Player builds are not a
supported replaceable target.

Placement passes through `CanonicalWater.placeTemporaryFlood`, which creates a
sparse full cell marked `FLAG_TEMPORARY_FLOOD` and projects the namespaced
Wilderness fluid without native fluid spread. `TemporaryFloodSavedData` records
the exact position only after that succeeds. A full ledger rolls the placement
back immediately.

Recession requires three facts at once:

1. the exact saved flood position still exists;
2. the canonical cell still carries `FLAG_TEMPORARY_FLOOD`; and
3. the world block is still the matching plain Wilderness projection.

If a player, another mod, normal canonical flow, or a block placement changes
the cell, recession drops only its stale ledger claim. Permanent generated,
imported, player-placed, waterlogged, and third-party water cannot pass the
removal gate. Placement and removal have independent strict per-dimension tick
budgets and never operate in unloaded chunks.

## Configuration

Settings live under `water_simulation.watersheds`:

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enabled` | `true` | Enables compact watershed simulation. |
| `rainfallAccumulationRate` | `0.045` | Rain memory added by a maximum-intensity pass. |
| `snowmeltRate` | `0.035` | Stored snowpack routed as delayed warm-weather runoff. |
| `drainageRate` | `0.025` | Soil/runoff drainage and dry-weather decay. |
| `maximumWaterLevelOffset` | `0.45` | Absolute metadata surface offset in blocks. |
| `floodingEnabled` | `true` | Allows exact temporary overflow. |
| `floodThreshold` | `0.88` | Combined risk required to begin flooding. |
| `maximumFloodPlacementsPerTick` | `2` | Global dimension placement cap. |
| `maximumFloodRemovalsPerTick` | `4` | Global dimension recession cap. |
| `simulationDistanceChunks` | `6` | Player-centered loaded-chunk radius. |
| `updateIntervalTicks` | `40` | Queue refresh cadence. |
| `chunksPerTick` | `6` | Time-sliced chunk update cap. |
| `maximumSavedChunks` | `32768` | Packed per-dimension state budget. |
| `maximumTemporaryFloodCells` | `8192` | Exact flood-position ledger cap. |
| `sedimentEffects` | `true` | Enables runoff sediment/clarity response. |
| `debrisEffects` | `true` | Enables debris metadata/particle hook. |
| `debugLogging` | `false` | Emits infrequent bounded queue summaries. |

## Diagnostics and testing

Use `/wowater watershed` for the command source's current chunk or
`/wowater watershed <x> <y> <z>` for another loaded position. It reports basin,
terrain, chunk/local direction, contributing cells, confluence, rainfall,
snowmelt, saturation, runoff, discharge, level offset,
flood threshold/state, temporary cells, sediment, clarity, current, debris,
queue length, processed/initialized counts, mutations, and elapsed microseconds.

Automated tests cover chunk and local direction selection, confluence
accumulation, basin alias persistence, rainfall/snowmelt accumulation and decay,
downstream availability, flood activation, drought recession, weather-disabled
fallback, packed save/reload, original-state flood ownership, network bounds
and round trips, river sound cadence, and sediment tint alpha preservation.

For an in-game pass:

1. create a new world so rivers contain generated Wilderness metadata;
2. run `/wowater watershed` beside a river;
3. use `/weather thunder` and observe rainfall, snowmelt after a thaw, saturation, runoff, discharge,
   current, sediment, and level offset increase over multiple passes;
4. confirm overflow expands by only a few loaded adjacent cells per tick;
5. place or replace water independently and confirm recession does not remove it;
6. run `/weather clear` and observe gradual discharge/level decay and exact
   temporary-flood recession; and
7. repeat after save/reload and on a dedicated server.

## Remaining live validation

The implementation remains deliberately loaded-only and metadata-driven.
Client runtime validation should still cover visual blending at chunk-cell and
basin boundaries, boat handling through a confluence, sound balance beside a
storm river, vegetation restoration after save/reload, and mixed-mod ecosystem
navigation. Offsets above the configured conservative range still do not create
custom block collision shapes; the shipped default remains below one block and
uses the shared animated immersion/buoyancy boundary.
