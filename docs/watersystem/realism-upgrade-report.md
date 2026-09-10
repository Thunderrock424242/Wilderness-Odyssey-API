# Water realism upgrade: engineering report

Implementation on the existing `in-dev` working tree for Minecraft 1.21.1,
NeoForge and Java 21. This report covers the connected conservation/hydrology
pass, its compatibility boundaries and the actual validation evidence.

## 1. Files changed

The water work changes/adds **74 files** listed below. Existing
`build.gradle` edits and `docs/development/tick-engine-jvm-crash.md` were
preserved, not authored or reverted by this pass. Generated builds, test-world
state and logs are not source deliverables. No commit/push was performed.

### Engineering documentation

- `docs/watersystem/atmospheric-exchange.md`
- `docs/watersystem/coastal-solver.md`
- `docs/watersystem/conservation-architecture.md`
- `docs/watersystem/overview.md`
- `docs/watersystem/realism-upgrade-report.md`
- `docs/watersystem/watersheds-and-flooding.md`
- `docs/watersystem/weather-coupling.md`

### Production Java

- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/ocean/shore/ShallowWaterGrid.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/ocean/shore/ShorelineWaterManager.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/command/WaterDebugCommand.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/config/WaterSimulationConfig.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/fluid/CanonicalWaterFlowGameTests.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/fluid/FiniteWaterFlowPlanner.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/fluid/WildernessFluidRegistry.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/AtmosphericWaterExchange.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/GroundwaterModel.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/HydrologicFlux.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/HydrologicReservoir.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/HydrologicStorage.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RainwaterBodyManager.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalBudgetDiagnostics.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalDrainageGraph.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalHydrologyConfig.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalHydrologyManager.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalHydrologyModel.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalHydrologySavedData.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalHydrologyState.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalSoilSampler.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalWaterProjection.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RiverHydraulics.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/SnowWaterEquivalentModel.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/SoilHydrologyModel.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/TemporaryFloodManager.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/TemporaryFloodSavedData.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WaterBudget.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WaterBudgetAuditor.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WatershedChunkState.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WatershedSavedData.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WatershedServices.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WatershedSimulationManager.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WatershedTags.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WeatherHydrologyManager.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/integration/WaterPerformanceIntegration.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/SPHSimulationManager.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/SPHSimulator.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/SphSettlementGameTests.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/SphWaterSavedData.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/volume/CanonicalWater.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/volume/WaterVolumeChunk.java`
- `src/main/java/com/thunder/wildernessodysseyapi/weather/client/surface/WeatherSurfaceRenderer.java`
- `src/main/java/com/thunder/wildernessodysseyapi/weather/simulation/AtmosphereSimulationEngine.java`
- `src/main/java/com/thunder/wildernessodysseyapi/weather/simulation/SurfaceWeatherModel.java`
- `src/main/java/com/thunder/wildernessodysseyapi/weather/simulation/WeatherAuthority.java`
- `src/main/java/com/thunder/wildernessodysseyapi/weather/simulation/WeatherPhenomenaModel.java`
- `src/main/java/com/thunder/wildernessodysseyapi/weather/surface/SurfaceWeatheringScheduler.java`

### Data resources

- `src/main/resources/data/wildernessodysseyapi/tags/block/hydrology_clay.json`
- `src/main/resources/data/wildernessodysseyapi/tags/block/hydrology_gravel.json`
- `src/main/resources/data/wildernessodysseyapi/tags/block/hydrology_impermeable.json`
- `src/main/resources/data/wildernessodysseyapi/tags/block/hydrology_rock.json`
- `src/main/resources/data/wildernessodysseyapi/tags/block/hydrology_sand.json`

### JUnit tests

- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/ocean/shore/ShallowWaterGridTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/ocean/shore/ShorelineWaterManagerTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/fluid/FiniteWaterFlowPlannerTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/HydrologicConservationTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/RegionalHydrologyModelTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/TemporaryFloodSavedDataTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/hydrology/WatershedChunkStateTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/SPHSimulatorMirrorTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/SphWaterSavedDataTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/volume/CanonicalWaterDisplacementTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/watersystem/water/volume/WaterVolumeChunkTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/weather/simulation/AtmosphereSimulationEngineTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/weather/simulation/AtmosphericWaterExchangeTest.java`
- `src/test/java/com/thunder/wildernessodysseyapi/weather/simulation/SurfaceWeatherModelTest.java`

## 2. Architecture introduced

The existing owners remain in place. Added regional classes separate persistent
inventory (`RegionalHydrologyState/SavedData`), unit-bearing models
(`SoilHydrologyModel`, `SnowWaterEquivalentModel`, physical
`GroundwaterModel`, `RiverHydraulics`), cached terrain/drainage, bounded runtime,
exact projection transactions and diagnostics. `AtmosphericWaterExchange`
bridges accepted quantities into the existing weather worker pipeline.

The old watershed/legacy-weather managers are lifecycle facades for one regional
scheduler, not competing models. Existing packet shapes, public normalized
conditions, finite buckets, namespaced fluids and generated spans remain.
See [the ownership diagram](conservation-architecture.md#ownership-and-units).

## 3. Water ownership model

- Generated water: compact baseline spans until local disturbance; not duplicated
  into regional inventory.
- Regional: finite SWE, soil, groundwater, runoff, river, lake, floodplain and ice.
- Canonical: exact detailed water, 4096 units/full block.
- SPH: exact represented canonical units, independent of numerical particle mass.
- Temporary ledger: ownership/provenance claim, not another copy of volume.
- Atmosphere/ocean: explicitly documented open boundaries; normalized vapor and
  effectively infinite ocean are not secretly claimed to be finite global stores.
- Waves, coast grid, snow/freeze overlays and normalized conditions: derived
  representations, not spendable water.

Old normalized hydrology does not encode measurable opening inventory. New
regional stores start dry without deleting existing generated/canonical/SPH water.

## 4. Conservation equations and invariants

Regional units are milli-canonical (4,096,000/m3). Internal transfers use the same
accepted amount on both sides. Receipt counters are overflow-checked before
mutation; residual tolerance is configurable.

```text
input boundaries - output boundaries - storage change = 0 milli-units
local source before + target before = source after + target after
regional projection debit = canonical credit
canonical recession debit = regional return
canonical mobile debit = SPH represented volume
successful SPH settlement credit = represented volume
failed settlement: canonical partial writes roll back; SPH retains volume
```

`/wowater budget [pos]` exposes exact regional stores, boundary receipts,
funded canonical claims as a memorandum, residual warning, discharge/stage,
temperature, last forcing and work/timing counters. It never force-loads or
repairs terrain. Projection claims are not added to regional storage.

## 5. Hydraulic flow changes

Head now includes depth/elevation and a bounded hydrostatic column sample.
Orifice-style transfers use explicit elapsed seconds, opening/capacity limits
and a common multi-neighbor equilibrium limiter. Gravity/head, retained momentum
and exponential drag replace per-visit speed/drag assumptions; caps retain
gameplay stability. Duplicate same-tick queue entries are deferred.

Destination acceptance determines source debit. Cell modifiers preserve flags.
Flood claims split/move transactionally, temperature mixes by volume, and failed
claim transfer restores canonical target/replaceable terrain. Solid displacement
retains unresolved water and its ownership. Ordinary and temporary parcels do
not mix implicitly.

The current safe-host rule is intentionally conservative: solid walls block,
but arbitrary fence gaps/waterlogged mod blocks are not new canonical hosts.
One-second local time caps and integer quantization mean coarse-budget behavior
is stable/bounded, not mathematically identical under arbitrary scheduling.

## 6. Hydrology, river and flood changes

One persistent 16x16 cell uses tagged soil profiles and sampled vegetation.
SWE supports accumulation, thaw and rain-on-snow. Soil infiltration/retention/ET
feeds a capacity-aware aquifer with delayed baseflow and storm memory.

Incremental priority-flood D8 routing gives known cross-chunk outlets, drainage
area, basin and spill elevation. Unknown frontier stays closed. Each record has
a Manning reach with physical Q/velocity/stage and bankfull capacity. Lakes
fill before spill. Actual river/lake excess moves into floodplain storage.

Flood/pond/wetland/spring placement reserves real inventory before creating an
exact canonical parcel and funded claim. Recession returns water to its funding
region, including after cross-chunk movement. External extraction releases the
claim instead of creating a duplicate return.

Protection remains loaded-only and rejects structures/references, block entities,
protected tags and unsupported terrain; starter-bunker bounds receive an
independent check. Candidate elevation priority is bounded to each connected
candidate window, not a whole-basin flood-fill.

Admitted state advances without players. Bounded two-second to one-hour catch-up
intervals retain pending time, use exponential stores and held historical
forcing, then refresh weather for the next interval. Detailed mutation still
requires naturally loaded terrain.

## 7. SPH changes

Exact represented units remain separate from particles. Merge overflow rejects
the whole credit instead of saturating. Persistence ensures a location marker
for water-bearing bodies and rejects malformed unlocatable inventory. Reload
does not drop owned water merely because the active visual cap is now lower.

Settlement rollback also restores replaceable terrain. Added world-backed
capacity/success and partial-settlement rollback fixtures. Temporary provenance
is not yet an SPH payload, so floodwater stays canonical. See
[SPH ownership](conservation-architecture.md#sph).

## 8. Weather coupling

Regional accepted rain/ET and SWE publish receipts. Weather snapshots immutable
watermarks, applies ET once per accepted asynchronous revision, and acknowledges
only applied work. Rejected calculations and forecasts cannot consume water
feedback. The modeled footprint suppresses independent evaporation/heuristic
moisture supply; uncovered terrain keeps its existing explicit boundary forcing.

SWE drives cosmetic snow through existing snapshots and the bounded overlay.
The weather scheduler no longer manufactures collectible snow on modeled
terrain; old vanilla snow remains external. Aggregate temperature and liquid/ice
exchange conserve regional quantities without new solid blocks. Existing custom
water freeze shaders remain cosmetic. See
[weather exchange details](atmospheric-exchange.md).

## 9. Performance protections and consumers

- Sparse canonical storage and bounded active queues; at most one evaluation of
  a cell per level tick.
- Bounded elapsed clocks with lifecycle cleanup.
- Non-evicting regional inventory admission cap (default 8192).
- Incremental DEM topology passes (default 128 nodes/tick); no terrain scanning
  on a background worker or unloaded chunk.
- Bounded round-robin regional/catch-up, projection/recession and packet budgets.
- Existing SPH body/particle scheduling and loaded-area checks.
- Coastal shared-face conservation, positivity limiter, CFL cap and retained
  elapsed backlog; explicit side-specific ocean boundaries.
- Operator counters for canonical cells/units/time, SPH particles/time,
  regional/river intervals/time, detailed projection receipts and mass residual.
- Existing entity/boat and erosion authorities consume actual shared currents.
  Removed the old local-grid multiplier from the regional physical current.
  No independent force loop or unbounded erosion mutation was added.

The graph constructor still copies/sorts admitted keys before its incremental
passes; this is bounded by admission, not constant-time. No large-modpack tick
profile or long-duration soak result is claimed.

## 10. Persistence and migration

Regional SavedData version 1 persists exact stores/receipts, fractional weather
remainders, temperature, time and cached forcing/topology. Reducing admission
limits never evicts existing inventory.

Temporary ledger version 5 stores funding region and exact units. Versions 1-3
migrate to full blocks except version-3 wetlands (2048 units); version 4 keeps
its exact claims. Unfunded legacy returns use a named boundary, never a fabricated
historical regional debit. Future/truncated/duplicate inventories fail rather
than silently becoming empty owners.

Old normalized watershed data, basin aliases and hydrology remainder files are
preserved but no longer advanced as a second physical model. Existing world
registries, water attachments and client protocol shape are preserved.
Cross-file crash-consistent journaling remains absent: ordinary save/reload
conservation is not equivalent to atomic chunk-plus-SavedData crash recovery.

## 11. Tests and actual results

All commands use the checked-in wrapper, JDK 21 and isolated output:

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.simulation.*'
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat runGameTestServer -PcodexBuildDir=.codex-build --no-parallel
```

- Production compilation: passed.
- Focused water + weather simulation JUnit: **407 tests, 0 failures, 0 errors,
  0 skipped** on the integrated physics/projection/catch-up changes.
- Full JUnit pass before the last three focused regressions: **1107 tests,
  5 failures, 3 skipped**. The five failures concern untouched AI config,
  loopback URL validation, missing packaged logo, voice duration and cloud
  shader contract (listed below). The entire repository suite is not green.
- Packaging: completed successfully. This repository's `build` explicitly
  depends on `assemble`; it does **not** imply tests passed.
- World-backed GameTests: **all 31 required tests passed** on the rerun,
  including actual canonical placement, flood safety and SPH settlement/rollback.
  The first run exposed a fixture issue: at far-world coordinates a float
  particle rounded outside its integer-centered enclosure. The corrected
  fixture encloses the actual solver anchor. The server completed tests and
  shut down successfully; this was not merely a startup check.
- Final combined `build test` with the focused filters completed successfully
  on 2026-09-10: refreshed packaging and **407 tests, no failures/errors/skips**.
  The generated JAR was inspected for the regional manager/diagnostic classes,
  SPH settlement fixtures and all five new soil tags. `git diff --check` passed.

Installable artifact: `.codex-build/libs/wildernessodysseyapi-4.2.0.jar`
(26,733,670 bytes). Use the regular JAR, not a `-sources.jar`.
SHA-256: `3d81274a4ada6964e2061d658cf1ad1012c67204fa4e3b15005307373e785863`.
Use a disposable world or a backup copy for the remaining gameplay trials.

Full-suite failures (outside changed feature paths):

| Test | Observed failure |
| --- | --- |
| `AIConfigLoaderTest.bundledConfigDefinesAllSixFirstClassSubsystemProfiles` | Expected empty string, got null. |
| `OllamaLocalRuntimeTest.resolvesOnlyValidatedLoopbackHealthEndpoints` | No value present. |
| `ReleaseArtifactContractTest.packagedRecipesAndTagsUseMinecraft121SingularDirectories` | Missing packaged `logo.png`. |
| `CryoWakeupVoiceAssetsTest.manifestMatchesSourceTextAndEveryMeasuredFemaleVoiceClip` | `filtration_offline` duration expected 87, actual 88 ticks. |
| `RaymarchedCloudShaderContractTest.fragmentInterpolatesAtlasVolumesWithHardPrimaryAndLightingCaps` | Cloud shader source-contract assertion at line 45. |

Earlier sandbox compilation hit NeoForm `AccessDeniedException` on generated
`output.jar`; approved normal Gradle execution outside that sandbox reached
compilation and tests. No ACL/security/cache-JAR modification or manual compiler
workaround was used. Two attributable source/test issues were corrected:
Java comparator type inference, and an old coast test expecting immediate
boundary overwriting rather than the new inertial approach to equilibrium.

Build warning: StructureGen's catalog fingerprint differs from the current
dependency environment, so optional modded catalog content is not used. All
three bundled blueprints validated/generated. Existing deprecated rendering
API warnings also remain.

Added/extended regressions cover hydraulic head/dam opening/time/equilibrium,
cell flags and displacement temperature, exact parcel split/funding/reload,
legacy wetland migration, rejected future ledgers, SWE/soil/aquifer/ice/budget,
bankfull and lake spill, storm hydrograph, topology continuity, analytical
catch-up, projection rollback/return, SPH overflow/persistence/settlement,
weather receipt retries and coastal wet/dry/CFL/boundary balance.

Manual client visuals, boat feel, two-client multiplayer, historical-world
upgrade trials and long-running performance remain unrun. Runtime registration
or GameTests do not substitute for those checks.

## 12. Known approximations

1. The atmosphere is normalized; the ocean is effectively infinite. Exact
   regional mass accounting does not imply a closed global atmosphere.
2. Generated lakes/rivers remain a separate baseline; finite regional stores
   model incremental catchment water, not an inferred total of every old lake.
3. Drainage is admitted-terrain-only, 16-metre D8, with closed unknown frontier;
   public legacy basin aliases remain distinct presentation identities.
4. Channels are rectangular reaches, lakes coarse stage-area stores, floods
   local connected low-first windows. No full river network geometry/CFD.
5. Catch-up holds cached forcing; long nonlinear hydrographs can differ from
   continuous short steps. No offline wall-clock evolution.
6. Thermal storage is aggregate temperature/phase, not latent-heat/enthalpy
   conservation. No walkable custom ice or complete SPH temperature/provenance.
7. SPH uses existing world-space float positions; sub-block precision degrades
   at far-world coordinates. Exact owned units do not fix spatial precision.
8. Coastal regions are independent derived current grids; their represented
   volumes are not physical water and overlapping regions do not share mass.
9. Native Gerstner and existing tides are retained; no new astronomical
   constituent/fetch-resolving global spectrum or detached breaker geometry.
10. Cross-file crash journals, arbitrary waterlogged/fence gap canonical flow
    and global sediment/energy transport are not introduced.
11. Erosion and entity controls remain budgeted existing consumers; realistic
    gameplay tuning and modpack performance require live measurements.

## 13. Recommended next-generation work

After the live checks in [watersheds and flooding](watersheds-and-flooding.md):

- Persist a compact historical forcing series and benchmark budget changes
  against short-step hydrographs and large admitted-region populations.
- Add stable incremental basin partition/rebuild and surveyed channel/lake
  stage-area geometry; extend connected flood filling without widening mutation
  authority or force-loading terrain.
- Journal cross-file ownership transitions and extend SPH payloads with funding
  provenance and temperature before allowing temporary water into SPH.
- Move SPH coordinates to a local origin for reliable far-world sub-block motion.
- Calibrate discharge/current/erosion response with gameplay and profiling data.
- Consider tested local-inertial/shock-capturing extensions, current/fetch
  coupling and bay tide modifiers only where measurements justify the cost.
