# Wilderness River Network

## Scope and authority map (19 October 2026)

This is an incremental implementation of the river-network brief. New supported
Overworld terrain can use bounded, seed-based source-to-existing-water routes,
variable channel widths/depths, continuous bends and confluences. Exact post-carver
terrain remains the final write authority. Local sealed pools and certified lake
outlets provide a conservative fallback. Generation remains disabled by default.
This is not yet a complete global watershed generator or a verified full-pack
visual/multiplayer release; the remaining limits below are part of the contract.

| Responsibility | Existing owner and integration boundary |
| --- | --- |
| Water ownership | `WildernessWaterAuthority`; sparse `WaterVolumeChunk` overrides compact `GeneratedWaterChunk` |
| Generation writes | `GenerationWaterStateMapper`, noise-generator and ProtoChunk mixins; exact water states retain amount/falling and successful writes record spans |
| Generated classification | Biome ocean/river tags, otherwise lake or low aquifer; classification is optical metadata, not river topology |
| Detailed disturbances | `CanonicalWater.materializeGeneratedNeighborhood`; bounded loaded neighborhood, 4096 units/block, no background river-wide materialization |
| Local finite motion | `WildernessFluidRegistry`, `FiniteWaterFlowPlanner`; accepted target credit determines debit, elapsed-time and work caps |
| Catchment inventory | `RegionalHydrologyManager`, `RegionalHydrologyModel`, `RegionalHydrologySavedData`; rainfall, soil, groundwater, river, lake, floodplain and ice remain separate from generated water |
| Runtime topology | `RegionalDrainageGraph`; incremental priority flood over admitted 16 m cells, unknown frontier retained, not a generation terrain oracle |
| Local metadata | `WatershedTerrainInitializer`, `WatershedDrainageGrid`, `WatershedLocalFlow`; four-by-four drainage metadata, not a second discharge model |
| Hydraulics | `RiverHydraulics`; Manning velocity/discharge from finite regional volume, area, slope, roughness |
| Gameplay queries | `WaterServices`, `WatershedServices`, `HybridWaterBodyModel`; shared authority surface/current sampling |
| Entity forces | `WaveEntityPhysics`, `HydrodynamicForces`; one server force application, profiles/exemptions/steering remain owned there |
| Rendering | `WaterRenderCoordinator`, snapshot mesh cache, `WaterSurfaceEquation`; CPU/GPU shared waves and renderer fallback remain authoritative |
| Foam/debris/audio | Existing `WaterAmbientEffects` and `RiverSoundscape`; cosmetic bounded local effects, not physical floating entities |
| Sediment/erosion | `ErosionManager`, its material registry, protection checks, sediment ledger and mutation budgets |
| Climate | `WeatherAuthority` through `WeatherServices`, existing Ecliptic/Serene integrations; SWE melt/baseflow and exact atmospheric receipts already exist |
| Runtime cadence | `WaterPerformanceIntegration` through the existing Data Engine/Tick Engine; no new scheduler |

The October water/weather audit and conservation architecture remain the safety
baseline. Generated spans and normalized lake/discharge metadata never fund
regional inventory. Physical floods, recession and rain-fed pools continue to use
`RegionalWaterProjection` and the exact temporary-water ledger.

### Existing generation limitations

`GlacialRiverFeature` carves a short, clamped chunk-local segment and uses local
randomness. It does not provide stable border crossings or global confluences.
`GlacialWaterfallFeature` places frozen ice curtains, not flowing waterfalls.
The glacial segment carver skips when the river generation pass is enabled, so
two independent stream generators cannot carve the same area. Frozen ice
curtains retain their existing separate feature ownership.

The actual 1.21.1 `NoiseBasedChunkGenerator.getBaseHeight` uses
`iterateNoiseColumn(LevelHeightAccessor, RandomState, ...)` without requesting
chunks. Surface rules, structure adaptation/blending, carvers and decoration run
separately. Thus a noise height prediction is useful but cannot certify the
final terrain, protected structures, cave walls or receiving fluid surface.
Arbitrary custom generators have no proven chunk-free terrain contract.

## Connected noise planning and exact placement

`RiverConnectedPlanner` uses a world-aligned 16-block D8 lattice. A climate-weighted
source is emitted only when its entire bounded, strictly descending route reaches
predicted existing water. Unknown terrain, pits and work frontiers reject the
route. The presets admit at most 4/8/12 edges; they do not invent sea-level exits
or route flat inland plateaus uphill. A fixed four-step upstream area selects a
capped radius independently of the requesting tile and accepted source count.
All sources that could reach an edge are included in each query's overlap halo.
Topology keys and ordering use seed/world coordinates, independent of completion
order. Contribution counts are bounded source counts, not exact global catchment
area or a spendable water volume.

`RiverNoiseTerrain` uses the actual generator's `getBaseColumn` and immutable
biome source. The height accessor has no chunk-request API. The current supported
class is exactly `NoiseBasedChunkGenerator`; custom subclasses are skipped.
Structure adaptation, carvers and decoration are not promised by this prediction.

`RiverSourcePlanning` certifies the complete fine geometry of each source route
before admitting any of that route. A bad downstream ridge or receiving inlet
cannot leave an upstream fragment in another tile. The complete route includes
fine shoreline, cut-depth and exposed-bank checks against predicted terrain.
`RiverChannelShape` then unions admitted geometry before clipping a tile halo.
Widths respond to bounded upstream area, larger channels deepen centrally, and gentle
slopes receive bounded connected bends. Confluence stages use the lower compatible
surface; only inputs at that minimum stage contribute direction. Fine noise samples reconcile the exact receiving
shoreline across the channel width. This is not a terrain-optimizing valley
search, outer-bend erosion model, delta or braid generator. Steep/unsafe predicted
geometry can still be rejected by the actual terrain certificate. Merging routes
retains the original maximum-drop policy for physical admission; an unrelated
unsupported merged curtain cannot erase a different route's predicted geometry.

`RiverConnectedGeneration` writes only the invoking chunk, under one short fixed
mutation lock after immutable planning. It reads only Minecraft's already-admitted
CARVERS dependencies, with no chunk futures or extra requests. It rejects old or
upgrading chunks, structure starts/references, stale captures, foreign solids,
missing supports, open banks, incompatible receivers and over-budget cuts/writes.
Replaceable dry plants in the channel can be cleared; solid features and block
entities remain protected. Every own-slice check finishes before its first write.
Native receiving water retains its exact states and inventory. Generated columns
use the existing mapper/span authority, without sparse parcels or regional credit.

Read-only prediction does not make independently rejected physical slices atomic
across an entire multi-tile route. A protected chunk, old/new boundary, cave or
foreign decorator can veto one slice after another has been admitted. The current
conservative fallback cannot guarantee an uninterrupted globally certified river
through every such interaction. This is a remaining limitation, not evidence of
full arbitrary-mod chunk-order independence.

`RiverGenerationPlanning` reuses bounded Data Engine caches: 8192 raw predictions
and 128 immutable results per world, at most eight installed world caches, weak
world keys and explicit unload cleanup. A complete invocation counts both coarse
and fine samples against its configured cap (at most 4096). Repeated raster work
is also capped at 128 times that sample limit; the union retains at most 16384
columns and visits at most 131072 input columns. Exhaustion discards the entire
invocation, including any earlier provisionally admitted routes. A tile that
exhausts its budget may still suppress geometry that another query can afford;
this conservative fallback is not a universal border-continuity guarantee.
Concurrent cache misses may duplicate bounded pure calculations,
without waiting on another generation task. There is no new executor or global
river tick loop. Statistics expose hits/misses, calculations, budget rejections,
retained samples, elapsed planning time, rejected fine routes, aggregate raster
visits and fixed-enum physical veto counters. Reading an absent world's rejection
statistics does not install a cache.

The local lake planner additionally uses a bounded minimax flood to locate the
lowest known saddle. It accepts an outlet only below every unresolved boundary
spill, fills the basin below that saddle, and validates a descending connection
to actual receiving water. A lower unknown spill cannot be replaced by a higher
convenient known outlet. Closed high lakes remain valid without an invented exit.

## Original local planning contract

`RiverTerrainSnapshot` captures a fixed world-aligned coarse rectangle and halo
through a caller-supplied read-only `TerrainSampler`. Missing samples remain
unknown. The sampler must never generate/request chunks, block on futures, or
read mutable decoration produced by neighboring river work. Capture has a hard
sample cap, integer-overflow guards and immutable primitive storage. Its key
includes world seed, dimension, terrain/settings identity and planner version.

`RiverNetworkPlanner` computes steepest downhill D8 routing with stable
world-coordinate tie breaking. Equal-height cells stay sinks: a flat plateau is
not assigned an invented uphill outlet. The result includes confluences,
contributing area within the captured rectangle, terminal type and deterministic
reach identity. A separate priority-flood pass computes a *potential* spill
elevation; it never replaces downhill routing with an uphill flowing reach.

For overlapping snapshots, a cell with all eight neighbors known has the same
downstream edge regardless of capture order or thread scheduling. At an unknown
neighbor or outside the rectangle it is explicitly unresolved. No edge at such a
frontier is asserted safe for carving. Accumulation is a lower bound when an
unknown frontier can contribute upstream. A local minimum is a coarse closed
drainage basin, not proof that an entire physical lake is sealed.

This coarse contract cannot guarantee a globally complete network or exact
catchment area. The generation pass below adds a separate, tightly bounded
placement certificate; it never treats coarse basin metadata as proof of a
sealed physical lake.

`/wowater riverplan` is operator-only and captures at most 100 already-loaded
chunk-center columns in a world-aligned 8-by-8-chunk region with one-cell halo.
It does not change blocks, attachments, water inventory or hydrology admission.
Its output describes present loaded terrain, not a seed-wide generation preview.

## Implementation milestones

1. Audit and invariant baseline: documented above; compile the current dirty
   checkout before river edits.
2. Immutable bounded planner: tests for downhill routing, pits/spills, receivers,
   confluences, overlap, unknown borders, mutation safety, order and concurrency.
3. Inspection adapter: loaded-only command and world-backed nonmutation tests.
4. Continuous currents: interpolate existing synchronized Manning vectors without
   creating discharge; canonical motion overrides the background vector and the
   entity path must not add a second copy. Test missing samples, opposing flow,
   basin separation and physical/visual consumer parity.
5. Opt-in local terrain writer: capture post-carver terrain, certify complete
   32 by 32 block tiles, preflight supports/walls/protection/write permissions,
   then write native generated water and compact reach metadata.

The continuation adds bounded connected topology, adaptive raster geometry,
certified local spill outlets, data-driven bank hooks and reach-driven cosmetics.
Large globally certified catchments and the remaining acceptance work stay open.
Permanent generated rivers may change current/discharge, but physical stage
changes remain gated on funded geometry transactions. No legacy-world carving
or automatic migration is permitted.

## Local fallback and generation ownership

`riverWorldgen` in `config/wildernessodysseyapi/wildernessodysseyapi-common.toml`
owns new terrain settings. `enabled=false` is the default. The first supported
scope is the exact vanilla `NoiseBasedChunkGenerator` in the Overworld; custom
generator subclasses and other dimensions are skipped. Existing noise-router
modifiers may still participate in that generator, but their compatibility must
be verified in a fresh world.

`RiverTerrainCapture` records 256 surface heights, receiving-water flags and
sixteen solid-natural support bits per column after CARVERS. Captures are chunk
attachments, so interrupted generation can retain them. Their settings fingerprint
must match across all four participants. A completed tile discards all capture
arrays and retains only a processed marker and sparse reach columns. Neither
capture nor reach metadata is a water inventory.

Minecraft's FEATURES stage guarantees CARVERS dependencies and block writes
within one chunk. A fixed, world-aligned 2 by 2 chunk tile lies inside that radius
from any of its members. The first member's pre-decoration hook plans and writes
the entire tile. Sixty-four fixed lock stripes prevent duplicate same-tile river
writes without an unbounded cache or additional worker/scheduler. Subsequent
members skip the processed tile. A tile containing an existing LevelChunk,
upgrading terrain, missing capture or changed settings is rejected.

The coarse four-block lattice routes downhill toward actual receiving water or
a closed depression. A dense bounded flood certifies the pool shoreline above
sea level when appropriate. Unresolved border drainage and open slopes remain
uncarved. A receiving pool may include a shallow, bounded shore strip at the same
elevation; original receiving-water blocks remain intact. Rasterized tributaries
join the certified target, including the internal chunk boundaries. The writer
checks natural bed/support and walls, protects any tile containing valid structure
starts/references, rejects foreign blocks and checks all write permissions before
placing anything. The `river_replaceable` block tag allows explicit pack tuning;
bedrock, block entities and fluid-containing hosts are always excluded.

The write budget rejects the entire plan when exceeded. New water uses
`GenerationWaterStateMapper` and the normal `GeneratedWaterChunk` baseline:
4096 units per full block, with no sparse runtime inventory or regional credit.
Biome-aware beds use gravel, clay, sand, mud or stone. Supported drops are bounded
to four blocks; these are small falling transitions, not tall mountain waterfalls.
This local fallback retains a one-block stream radius. The connected path uses
the separate configured variable radius; braiding, deltas, point bars and a
terrain-optimizing centerline search remain future work.

Captured topology is independent of mutable decoration. The actual writer still
fails closed if another feature alters the site first. Consequently arbitrary
third-party cross-chunk decoration can affect whether a tile is accepted; full
chunk-order independence across such interactions is not claimed. Supplied
ProtoChunk fixture tests check all four first-member orders against unchanged
natural terrain. Full noise-world generation under multiple scheduling orders,
visual shore quality and optional terrain-mod combinations require separate
acceptance runs before recommending the flag for a pack.

## Validation and acceptance

### Current integration and units

`RiverCurrentField` optionally interpolates four cached chunk-center Manning
vectors, retaining the home vector for missing, dry or unrelated basins. This
coarse field is not a planned channel centerline and cannot supply bank slowdown,
waterfall velocity or exact bend hydraulics yet. It does not amplify speed from
the old local contributing-cell heuristic.

Watershed save/network values retain blocks/tick for compatibility. Canonical
motion and entity hydrodynamics use blocks/second. The composition boundary
converts regional motion once, then chooses the whole moving canonical vector
when present. Settled local detail resumes the regional background without
waking the finite solver. New funded floods/pools convert into canonical units
on placement. The entity owner no longer adds the center-point local-flow
correction to its already-resolved multi-point buoyancy sample.

Snapshot columns, mesh motion, immersion and spray use the same composition.
Changing smoothing queues native meshes through the existing render ownership
policy; it does not release resources or rebuild every frame. No new shader,
buffer, SPH simulation, network payload or scheduling lane was introduced.

Correcting the old unit mismatch increases a purely regional background current
to its actual modeled physical speed. Existing entity profile, drag/force limits,
steering controls and erosion protection/budgets still apply. Full-pack boat and
erosion tuning remains manual acceptance.

### Commands and results

Run serially from this feature worktree with JDK 21:

```powershell
.\gradlew.bat waterSimulationTest renderingTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat runGameTestServer riverNetworkBenchmark '-PcodexBuildDir=.codex-build' '-PauditGameTestRun=river-generation-02' '-Dneoforge.enabledGameTestNamespaces=wildernessodysseyapi_river_tests,wildernessodysseyapi_water_safety_tests' --no-parallel --console=plain
.\gradlew.bat :build '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

The earlier generation continuation used the `wilderness-river-network` worktree.
The clean PR branch `codex/wilderness-river-network-pr` starts directly from
`in-dev` at `a9b56c665406931b977e628cef251fc8eb5552f7`; unrelated OPC/WAGE/coastal
commits and crash logs are excluded. Earlier results below describe the original
feature worktree. Independent PR-checkout results are recorded separately at the
end of this document. No personal save or normal IDE output was reset.

Earlier feature-worktree evidence:

- Water simulation: **165 tests in 35 suites**, no failures/errors/skips.
- Rendering: **325 tests in 76 suites**, no failures/errors/skips.
- Disposable `river-generation-02`: **31/31 required GameTests passed**, including
  nine river tests and 22 existing water safety tests. Server startup, execution,
  save and shutdown completed. Final corrected source also passed all 31 in
  `river-generation-04`, including optional-smoothing API parity.
- RED was observed for missing catchment/persistence/sync APIs, the lower-receiver
  side-wall regression and the missing falling-surface handling before fixes.
- Independent source review checked the declared dependency/write radius against
  the actual Minecraft sources and found receiver-certification/current-observation
  issues; regression coverage and corrections are included. The final recheck
  confirmed all three findings resolved, with no new P1/P2 findings.
- Actual build/runtime: Minecraft 1.21.1, NeoForge **21.1.262**, Temurin **21.0.6**.
  No dependency, release version or security setting was changed.

World tests use detached real ProtoChunks for generation writes and real loaded
water for current/authority queries. They cover matching physical fluid and exact
compact spans, lake crossings, actual tributaries, supported falling columns,
all four first-member orders, whole-plan vetoes, stale receivers, existing-chunk
rejection, no duplicate sparse inventory, save/reload and read-only current API
parity. They do not constitute a full noise-world order/concurrency acceptance
run, interactive client test or two-client multiplayer test.

One later attempt stopped before Minecraft launched: `:generateStructures`' Java
process hit `EXCEPTION_ACCESS_VIOLATION` in compiled Guava cache code on Temurin
21.0.6 (`.codex-build/river-network/hs_err_pid2748.log`). This is recorded as a native JVM failure, not a
source/test failure. The fresh-process retry reached Minecraft and reproduced
the observation/smoothing defect (8 of 9 passed). After correction, the final
combined 31-test run passed and shut down successfully.

### Measured synthetic planner cost

`riverNetworkBenchmark` uses the existing offline Gradle runtime, seed 42,
50 warmups and 200 measured iterations on a 32-logical-CPU i9-14900HX. These
measure pure planning against already-captured arrays, excluding Minecraft
capture, block placement, chunk scheduling, networking and GPU work.

| Synthetic scenario | Columns | Planned writes | Mean ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Sealed elevated pool | 121 | 363 | 0.0859 | 0.1538 | 0.2241 |
| Receiving-water tributary | 264 | 786 | 0.1671 | 0.3197 | 0.5232 |
| Supported steep drop | 240 | 1038 | 0.1710 | 0.3543 | 0.4862 |
| Rugged field, mostly rejected | 3 | 9 | 0.0698 | 0.1731 | 0.2456 |

The maximum 4096-sample coarse planner averaged 2.2931 ms (p95 2.4628,
p99 2.5503); its synthetic capture averaged 0.0879 ms. These are incremental
component costs, not a before/after speedup of world generation or the modpack.
No production worldgen, GPU, network or heap-retention comparison was measured.
There is no river-wide polling, planner worker queue or growing global cache.

## Bank ecology, climate and presentation

Validated datapacks in `data/<namespace>/river/banks/*.json` select immutable bank
profiles by biome IDs/tags, priority, and stable resource-ID tie breaking. Nine
bundled profiles cover plains, forest, jungle, desert, badlands, swamp, beach,
mountain and glacial biomes. A profile contains `priority`, `biome_tags`, `biomes`,
weighted `bed`, and optional weighted `plants` arrays. Each entry uses `block`
and optional positive integer `weight`. Unknown fields, unsafe blocks, duplicate
entries and oversized inputs reject the complete reload, retaining the last valid
snapshot. Profiles are limited to 64 with at most 16 selectors/palette entries;
native inert beds and single-block native plants are explicitly whitelisted.

`riverBanks.dataDrivenPalettes=true` enables materials. `vegetation=false` keeps
new vegetation opt-in; `vegetationChance=0.18` and `maximumVegetationWrites=8`
bound same-chunk placements. Candidates require a dry native bank, natural
foundation, nearby source water, `canSurvive`, unprotected new terrain and remaining
write budget. No arbitrary biome decoration is replayed and no animals are spawned.

`EcosystemServices.riverHabitat` exposes only actual loaded wet reaches: biome,
body identity, depth/surface, composed m/s current, clarity, sediment, flooding and
existing climate-service **air** temperature. It is not a fish spawner or water
temperature simulation. Drinking safety now uses the shared physical current,
converted once to its existing blocks/tick threshold. Weather's existing bounded
water probe recognizes generated river/spring/lake/ocean spans outside vanilla
river biomes. Existing rain, groundwater, snowmelt, seasonal freezing, finite flood
and sediment owners remain authoritative; no second inventory/calendar is added.

Existing client effect owners now derive foam, mist/spray and audio from reach
kind, physical current, confluence and supported drop. Zero motion produces zero
new whitewater energy; lake reaches remain calm. The existing 48-slot local pool
advects foam and leaf/twig block flecks on the shared visible surface, removes
dry/missing/falling destinations, and applies a shared quality/distance emission
cap. Independent foam/spray/debris/audio toggles are available in the existing
client water config. These are cosmetic particles, not persistent floating items
or a new renderer. Interactive appearance and audio balance remain unverified.

## Current integration and persistence

`RiverGenerationChunk` writes version two and reads versions one and two without
changing the compact stride or existing water/watershed schemas. The new version
accepts configured falling intervals up to 16 blocks; old data remain valid.
It retains temporary terrain only until its owning generation slice is
processed. Completed sparse reach columns store waterbody identity, physical
stage/floor/top, normalized continuous direction, kind and a Manning speed limit.
The synchronized payload excludes all terrain captures and is capped at 256
columns. `RiverReachAttachmentSyncHandler` publishes late delivery through the
existing immutable `ClientWaterSnapshotStore`; generated/sparse/delta replacements
preserve reaches, and normal unload/dimension cleanup removes them.

`RiverReachServices.sample` and `observe` are loaded-only read APIs. Observations
report the generated surface-block stage and current in metres/second. Shared
`WatershedServices.localFlow` applies optional regional smoothing, then aligns the
modeled magnitude to reach direction and caps it by the hydraulic limit. Lake
columns remain calm. No current is inferred from terrain metadata when the
regional model supplies zero. Moving canonical detail still overrides the entire
background vector once through the existing authority path. Seasonal rainfall,
melt, sediment, foam/debris and entity forces keep their existing owners.

Falling generated columns retain physical falling fluid and compact falling
spans. They use native fluid geometry rather than a horizontal custom wave sheet;
the hybrid surface does not add waves or metadata stage offsets to that curtain.
Reach-aware impact mist/spray is now derived at an actual wet base. A tall
mountain plunge-pool generator and additional downward gameplay forces are not
implemented; native falling fluid and the current entity owner retain physics.

## Configuration and fresh-world acceptance

In `config/wildernessodysseyapi/wildernessodysseyapi-common.toml`, opt in before
creating a disposable test world:

```toml
[riverWorldgen]
enabled = true
connectedNetworks = true
planningQuality = "STANDARD"
maximumTerrainSamples = 4096
maximumChannelRadius = 4
maximumWaterfallDrop = 4
frequency = 0.35
minimumContributingCells = 3
streamDepth = 1
lakeDepth = 2
maximumCut = 4
maximumLakeColumns = 256
maximumWrites = 12288
lakes = true
waterfalls = true
naturalBanks = true
```

`enabled` defaults to **false**. Frequency weights connected sources by biome
rainfall and controls local fallback attempts; protection and geometry checks can
reject either path. Planning presets/topology settings should be changed only
with an understood transition at unexplored terrain, preferably in a new world.
`maximumCut` and depths use blocks. `maximumWrites` bounds block writes per
invoking connected chunk slice or complete local 2-by-2 tile. This pass supports
small tributaries and pools; finding a valid basin is not guaranteed near spawn.
Existing glacial streams are superseded only inside this supported generator
contract. Other generators keep their established glacial behavior.

Optional regional smoothing remains in `wildernessodysseyapi-server.toml`:

```toml
[water_simulation.watersheds]
smoothRiverCurrents = false
```

Existing watershed/hydrology switches, rainfall/roughness settings and entity
force strengths/exemptions continue to control those owners.

1. Create a disposable fresh Creative Overworld with cheats and the current JAR.
2. Explore new inland slopes, small depressions and shorelines. Check above-sea
   pools, tributary mouths, internal chunk crossings and supported small drops.
3. Use operator `/wowater riverplan`, `/wowater watershed` and `/wowater budget`.
   `riverplan` describes present loaded terrain, not a seed-wide generation preview.
4. Compare multiple copies of the same seed generated in different orders and
   parallelism settings. Capture block/reach hashes; investigate any placement
   veto caused by other cross-chunk decorators before enabling in a pack.
5. Test ridden/empty boats, swimming, dropped items, rain/recession, loaded borders,
   reconnect and a second client. Check shaders and native renderer fallback.
6. Load a copy of an existing save and verify previous terrain/water remain intact.
   Toggle generation off and on: old chunks must remain unchanged and saved reach
   metadata must still load.

No save migration or old-world recarving is performed. New attachments are absent
from legacy chunks. Disabling generation preserves existing water and reaches;
changing generation settings can produce a transition at unexplored terrain.
Physical flood/stage changes remain gated on the established funded transactions.

## Remaining work and acceptance limits

The full requested network is **unfinished**. Bounded cross-tile routes, capped
variable widths/depths, connected bends, local spill outlets, bank profiles,
opt-in vegetation, habitat queries and reach-aware cosmetics are implemented.
Still missing are an atomic whole-route physical certificate across arbitrary
protected/old/cave/decorator boundaries, large global watershed area, robust
valley-optimized headwaters across plateaus, outer-bend/point-bar geometry,
braids/deltas, climate-selected spring/wetland generation, large lake networks
and a validated tall-mountain waterfall/plunge generator. Existing erosion and
finite seasonal/flood models are reused. Permanent baselines are not physically
dried or lowered without a funded geometry transaction; this remains gated.

The native noise acceptance now exercises a real positive connected sample with
forward/reversed FULL requests. This is limited seeded acceptance, not full
noise-world visual quality or order independence across arbitrary modded
decoration, Tectonic/Terralith/Chunky/C2ME combinations, shader/Embeddium visuals,
and two-client acceptance remain unverified. The flag remains opt-in for that
reason. Dedicated GameTest startup confirms common code does not require client
render classes, but does not establish all optional-mod compatibility.

## 19 October continuation validation

### Initial continuation at 273734b9 (before latest-base integration)

This continuation was validated serially with Java 21.0.6, the checked-in
wrapper and isolated output. The completed command was:

```powershell
.\gradlew.bat waterSimulationTest renderingTest :test runGameTestServer :build riverNetworkBenchmark '-PcodexBuildDir=.codex-build' '-PauditGameTestRun=river-source-final' '-Dforge.enabledGameTestNamespaces=wildernessodysseyapi_river_tests,wildernessodysseyapi_river_acceptance_tests,wildernessodysseyapi_water_safety_tests' '-Dneoforge.enabledGameTestNamespaces=wildernessodysseyapi_river_tests,wildernessodysseyapi_river_acceptance_tests,wildernessodysseyapi_water_safety_tests' --no-parallel --console=plain
```

`completion-final-validation.log` completed successfully in **2m 49s**:

| Check | Actual result |
| --- | --- |
| Water suite | 199 tests, 40 suites, zero failures/errors/skips |
| Rendering suite | 315 tests, 71 suites, zero failures/errors/skips |
| Full root Java suite | 1699 tests, 445 suites, zero failures/errors; four existing skips |
| Dedicated GameTests | All 43 required tests passed; startup, execution, saving and shutdown completed |
| Native positive acceptance | 122 river columns in four FULL chunks, physical chunk crossing, matching forward/reversed physical+reach snapshots, matching save/reload with generation disabled |
| Optional vegetation | Enabled live-survival placement, disablement, denied writes, one remaining write, solid-feature preservation and no sparse inventory all passed |
| Root packaging | `:build` passed; 89 inspected production/nested classes match compiled bytes |
| Packaged resources | Nine valid bank profiles, native acceptance template and river lifecycle mixin present |

Three root-suite skips need Windows symbolic-link privileges; the fourth is the
separate opt-in large bunker fixture. No system permissions were changed. The
artifact is `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`, 30014244 bytes,
SHA-256 `abdc156a110171d843c146a39d85f0f383428628d9f7b0dda17cf6f5172c906d`.

The native acceptance used the standard Overworld density generator with a fixed
plains biome. Its bounded search evaluated 20 query tiles before finding a valid
site near block coordinates (-512, 128); eligibility is not guaranteed near spawn.
The physical order/reload checks exercise native generation scheduling and actual
blocks, not only detached ProtoChunk fixtures. They remain a limited seeded sample.

| Measured operation | Mean ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: |
| Real standard-noise column prediction, 64 columns | 2.1608 | 3.2448 | 33.8521 |
| Real Amplified-noise column prediction, 64 columns | 1.1241 | 1.4145 | 1.7087 |
| Native standard planning query, 20 queries | 220.1200 | 527.7706 | 755.5492 |
| Synthetic complete source plan, standard slope | 12.2529 | 13.2441 | 13.7600 |
| Synthetic detailed/rugged topology, no admitted outlet | 1.4733 | 1.6771 | 1.9266 |

The synthetic source benchmark uses 50 warmups and 200 measured iterations. Its
standard case retains 348 halo columns with 3363 samples and 110250 raster visits;
the rugged case rejects all routes and is not equivalent generation work. Real
Amplified measurements cover prediction only, not a heavy-world generation run.
The first four-chunk native FULL batch took **1137.3191 ms** with generation
enabled; the same seed, request order and batching with generation disabled took
**376.8526 ms** in a subsequently warmed JVM. This single sequential pair includes
different cache/JIT states and supports no stable overhead ratio or speedup claim.
The cold-query cost is material; generation remains opt-in. No GPU, network-byte,
heap-retention, full-modpack or two-client performance comparison was measured.

Earlier failures were useful regressions: completed neighbor wrappers were being
treated as new terrain; fine inlet lowering could resume uphill; future falling
curtains could be rejected as air; pending neighbor clearance could be missed;
whole-query fine rejection could erase a neighboring tile's route. Each received
a focused regression and correction. The final green run includes those fixes.

### Latest-base integration

The first PR merge checks against `in-dev` commit `4533cb7f` failed compilation
before Minecraft started: that base had moved Data Engine under OPC, while the
new cache import still used its previous package. The PR branch was merged with
that base and now imports the existing `opc.dataengine.cache.DataCache`, preserving
the same cache implementation and API. No replacement cache owner was introduced.
The 18 reported Qodana warnings received focused changes: known immutable samples
and required test fixtures have explicit non-null assertions; integer coordinate
decoding and long seed arithmetic are explicit; native search loops have braces;
redundant shoreline iterations stop early. Two narrow mixin inspection annotations
explain the runtime-transformed receiver instead of changing injection behavior.

`base-integration-validation.log` completed successfully in **3m 16s**, with the
same isolated serial command as above, plus explicit `compileJava` and without
rerunning the unchanged benchmark; the audit world was `river-base-integration`.

| Check on the merged base | Actual result |
| --- | --- |
| Water suite | 199 tests, 40 suites, zero failures/errors/skips |
| Rendering suite | 343 tests, 80 suites, zero failures/errors; one opt-in GPU fixture skipped |
| Full root Java suite | 1762 tests, 460 suites, zero failures/errors; five skips |
| Dedicated GameTests | All 43 required tests passed; 122 connected columns, physical crossing, forward/reverse and save/reload checks passed again |
| Root build | Passed |

The additional skip is the upstream opt-in WAGE cloud GPU fixture; the other four
retain the symbolic-link and large-bunker reasons above. No client or GPU proof is
inferred from those skips. The native repeat measured 20 queries at mean 217.8602 ms,
p95 498.4399 ms and p99 739.0447 ms; enabled/disabled four-FULL-chunk batches took
1071.2035/419.4859 ms in the same sequential warmed-JVM comparison described above.
These remain limited samples with material cold-query cost, not a speedup claim.

After strengthening two canonical-current fixture preconditions, a final serial
`compileJava runGameTestServer :jar` run in `river-fixture-final` passed all 43
required tests again in **1m 20s** (`base-fixture-final.log`). The last native
sample retained 122 columns and measured mean 214.4181 ms, p95 493.8669 ms, p99
748.3888 ms across 20 queries; enabled/disabled batches took 1111.3809/365.9000 ms.
The final packaged JAR is 30075790 bytes, SHA-256
`06b0ec0dd0ffbb0798458df1cbde3e64d79035bc03d3b37d44e7080ccd0d4d58`.
All 147 inspected compiled production/nested classes associated with this PR's
changed sources match packaged bytes; nine bank profiles and the native acceptance
template are present. Source/diff checks passed. The GPU, visual and multiplayer
acceptance boundaries above remain unchanged by this package proof.

### Earlier feature-worktree command ledger (historical local milestone)

- `final-validation.log`: water **165/165**, rendering **325/325**, GameTests
  **31/31** in `run-audit-river-generation-04`, benchmark and root `:build` passed
  in 3m 1s. Dedicated test execution took 5.779 s; remaining time includes setup.
- The final entry-origin guard and tile-coordinate lock hash were then recompiled:
  `package-final.log` passed water **165/165** and root `:build` in 36 s. These
  admission/striping refinements do not replace the missing full noise-world
  generation-order acceptance run.
- Eight inspected production/mixin classes in the final JAR exactly match the
  latest compiled bytes. The lifecycle mixin, replaceable tag and GameTest
  template are packaged. `git diff --check` and new-file whitespace/JSON checks
  passed. No full repository JUnit suite or interactive client was run.
- Artifact: `.codex-build/libs/wildernessodysseyapi-5.0.0.jar` (29934496 bytes).
  SHA-256: `b24636ce4ebffd8bd69b2314e86ba92768d27c15b73ab8f2cfff7b0fcd41a12b`.
- All validation used the checked-in wrapper, Java 21 and isolated `.codex-build`
  output. Validation from this chat was serialized and no subagent ran Gradle.
  The separate original `in-dev` checkout was preserved.


### Clean PR branch verification

The river-only PR worktree is `wilderness-river-pr`. Its 53-file diff includes
river planning/generation, shared current consumers, attachment/render handoff,
focused tests and three river documents. All ported source/resource/test files
match the reviewed feature worktree; the shared config and build script were
adapted to preserve the existing `in-dev` owners and test setup. The original
mixed feature branch remains preserved.

- `pr-compile.log`: Java compilation passed against the direct `in-dev` base.
- Independent extraction review found no P1/P2 scope or adaptation issues;
  `git diff --check` passed.
- Focused suites on the clean branch: **165 water tests** and **304 rendering
  tests**, no failures/errors/skips. The earlier 325 rendering count includes
  the separate WAGE work and is not the PR-branch total.
- Full root Java suite: **1650 tests**, zero failures/errors, **4 skipped**.
  Three skip because this Windows account cannot create symbolic links; one
  explicitly requires the separate opt-in 4g bunker fixture task. No system
  permissions or security settings were changed to bypass those assumptions.
- `pr-validation.log`: **31/31 required GameTests passed** in the unique
  `run-audit-river-pr-01` disposable world, with successful startup/save/shutdown.
  Root `:build` passed; the combined validation command completed in **5m 28s**.
- The final clean-branch JAR's eight inspected production/mixin classes match
  current compiled bytes. The lifecycle mixin, tag and test template are present.
  Artifact size: 29881470 bytes. SHA-256: `5d5587a10874169110db8040d094fcfff8225def4751c5f2337878e7ffb4adfd`.
- All checks used Java 21, the checked-in wrapper and isolated `.codex-build`
  output. No interactive visual or two-client acceptance is implied.

Exact clean-branch validation command:

```powershell
.\gradlew.bat waterSimulationTest renderingTest :test runGameTestServer :build '-PcodexBuildDir=.codex-build' '-PauditGameTestRun=river-pr-01' '-Dforge.enabledGameTestNamespaces=wildernessodysseyapi_river_tests,wildernessodysseyapi_water_safety_tests' '-Dneoforge.enabledGameTestNamespaces=wildernessodysseyapi_river_tests,wildernessodysseyapi_water_safety_tests' --no-parallel --console=plain
```
