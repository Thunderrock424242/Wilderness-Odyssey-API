# Water, weather, and ecosystem audit

Date: October 14, 2026. Branch: `in-dev`. Reviewed HEAD:
`6e6c585bd7b474ede553e9d21a61510c2b9e72b3`, including the existing working tree.

This is an audit, not a remediation. No gameplay source, tests, configuration,
world saves, or existing modified files were changed by the audit. Findings are
verified against source control flow; their gameplay triggers were not reproduced
in a running world. Prior audit results were used for orientation and were not
counted as current validation.

## Intended behavior and ownership

The review used the current subsystem documentation and implementation contracts:
finite water preserves volume and legacy terrain, localized weather exchanges
accounted precipitation and evaporation with hydrology, and wildlife reacts to
water, food, weather, and habitat while expensive work remains bounded.

| Responsibility | Existing owner retained by recommended fixes |
| --- | --- |
| Water queries and compact generated baseline | `WildernessWaterAuthority`, `GeneratedWaterChunk` |
| Disturbed finite water and block projection | `CanonicalWater`, `WaterVolumeChunk`, `WildernessFluidRegistry` |
| Regional water stores, transfers, and reversible surface claims | `RegionalHydrologyManager`, `AtmosphericWaterExchange`, `TemporaryFloodSavedData` |
| Weather state, detached calculations, and revision-checked application | `WeatherAuthority`, `AtmosphereGrid`, existing Data Engine integration |
| Wildlife needs, groups, and distance transitions | `EcosystemBehaviorGoal`, `AnimalGroupManager`, `EcosystemSimulationManager`, `DistantWildlifeManager` |
| Plant climate and selected mutation | `ReactiveVegetationScheduler`, `ReactiveVegetationServices` |
| Combined read-only environment samples | `RegionalEnvironmentManager`, public subsystem services |

No finding requires a replacement authority, a second simulation scheduler,
forced chunk loading, a new executor, or a persistence-format redesign.

All Java paths below are relative to
`src/main/java/com/thunder/wildernessodysseyapi/`.

## Findings

P1 means a high-priority ownership or conservation defect. P2 means an actionable
gameplay or supported-configuration defect. These priorities describe the traced
failure, not a measured frequency in the complete modpack.

### F01 — P1: Finite flow overwrites unconverted vanilla water

**Evidence:** `watersystem/water/volume/CanonicalWater.java:154-159` accepts plain
water as a destination, and `WildernessWaterAuthority.java:417-418` explicitly
includes `Blocks.WATER` in that classification. However,
`CanonicalWater.java:101-104` intentionally returns `EMPTY` for unconverted
vanilla water. Gravity and lateral planning in
`watersystem/water/fluid/WildernessFluidRegistry.java:323-329,408-419` therefore
see a physically full vanilla cell as empty capacity. `CanonicalWater.addVolume`
at lines 310-331 credits only incoming units and projects them over the original
cell. No later ordinary-water ownership guard prevents this.

**Trigger and effect:** Disturbed finite water reaches a full legacy vanilla
source, such as at an old/new terrain boundary. The original water is overwritten
with the transferred parcel, and the source parcel is debited normally. This
silently changes water that the documented explicit-conversion boundary should
leave alone. This finding does not assert behavior for every foreign fluid.

**Focused correction:** Reject unowned fluid-bearing destinations consistently
in admission and execution. Keep air, safe replaceable dry terrain, and owned
projections eligible. Any supported transfer into vanilla water needs an explicit
ownership policy that accounts for its existing volume.

**Missing regression:** Loaded-world gravity and lateral transfers beside a
contained vanilla source, asserting original target preservation, source debit,
and absence of implicit conversion.

### F02 — P1: Changing the initial water setting stops established finite flow

**Evidence:** The initial-choice contract is explicit in
`watersystem/water/config/WaterSimulationConfig.java:99-101` and
`WildernessWaterRules.java:16-21,53-56`. Persisted enabled authority remains
enabled after the startup setting changes. Nevertheless,
`WaterSimulationConfig.java:405-406` returns zero local-flow work when that raw
setting is false. `WildernessFluidRegistry.java:190-202` passes the persisted-mode
guard but executes no cells. `mixin/CanonicalWaterFlowMixin.java:40-49` continues
canceling native ticks for tracked water.

**Trigger and effect:** Save an enabled world, set
`water_simulation.enableWildernessOdysseyWater=false`, and reload or restart it.
Authority remains enabled, but finite flow stops; native flow cannot recover it.
The same raw-master gating also merits a focused review in compatibility helpers.

**Focused correction:** Return the configured work budget after the caller's
persisted-mode guard. Make runtime helper gates agree with the persisted mode
while retaining individual compatibility toggles. Do not implement an unsafe
automatic rollback of existing water.

**Missing regression:** A persisted-enabled world with the startup option later
false, checking actual finite flow and native-tick handoff.

### F03 — P1: Vanilla snowfall bypasses modeled snow accounting

**Evidence:** `mixin/ServerLevelLocalizedPrecipitationMixin.java:70-72` accepts
local snow/sleet and placement eligibility without checking regional snow
ownership. The current NeoForge source for `ServerLevel.tickPrecipitation`
then places or increments vanilla snow when `snowAccumulationHeight > 0`.
`weather/surface/SurfaceWeatheringScheduler.java:65-101` deliberately excludes
modeled hydrology from its equivalent harvestable-snow path because the regional
snow-water-equivalent (SWE) store already owns that mass. The invariant is
documented in `docs/watersystem/atmospheric-exchange.md:94-99`.

**Trigger and effect:** Local snow or sleet over an exposed solid surface in a
modeled hydrology chunk, with snow accumulation allowed. The atmospheric transfer
can fund regional SWE while vanilla independently creates collectible snow with
no debit against that store. Harvesting and replenishment can repeat.

**Focused correction:** Apply the supported-hydrology exclusion to the vanilla
snow-placement decision. Preserve precipitation callbacks and legacy unmodeled
snow behavior.

**Missing regression:** Exercise real `tickPrecipitation` on a modeled snowy
chunk and verify that retained SWE does not also produce harvestable snow.

### F04 — P2: Weather retention can reject every simulation batch

**Evidence:** `weather/storage/AtmosphereSavedData.java:78,136-140` reapplies
settings and trims without protected keys on every lookup. Capture admits the
complete player footprint in `weather/simulation/WeatherAuthority.java:730-745`.
During application, line 892 performs another lookup between whole-batch revision
checks. That lookup can remove inputs and make the second check fail before the
protected trim at line 920 is reached.

**Trigger and effect:** The accepted settings `maxPersistedCells=64` and
`activeSimulationRadius=3` admit a 9-by-9, 81-cell footprint for one player because
capture includes an extra border. Application trims it to 64 and rejects the
result. Repeated captures recreate the missing active cells. Both worker and
synchronous application encounter the defect. With default settings, enough
separated player footprints can encounter the same conflict at 4,096 cells.
Weather evolution and precipitation exchange commits can stop while calculations
continue to be attempted.

**Focused correction:** Give active admission and retention one consistent
policy. Do not destructively trim participating inputs during ordinary data
access. If the cap is strict, bound admission before forming the revision set.

**Missing regression:** Capture and apply an actual authority batch whose active
footprint exceeds the configured persistence cap. Pure revision-map tests do not
cover lookup-induced mutation.

### F05 — P2: Herd followers skip needs and interaction completion

**Evidence:** `ecosystem/behavior/EcosystemBehaviorGoal.java:135-139` returns for
followers before `buildContext` and the need integration at line 478. The separate
`GroupFollowerGoal` performs movement and looking only. Inherited drinking, rest,
and forage states do not execute the main goal's completion handlers, including
thirst restoration at lines 597-608.

**Trigger and effect:** Ordinary profiled animals form a group. Followers retain
their physiological values instead of accumulating needs; an already thirsty
follower does not satisfy thirst by accompanying the leader to water. This misses
the documented accumulation and drinking behavior in `docs/ecosystem.md:152`.

**Focused correction:** Keep expensive environmental decisions leader-owned,
but run cheap per-animal need integration and arrival/duration completion using
shared results. Followers need not issue independent searches or forecasts.

**Missing regression:** Real leader/follower needs over elapsed time, followed
by completed group drinking and resting with attachment assertions per animal.

### F06 — P2: Suspended leaders leave active followers without a decision owner

**Evidence:** `ecosystem/simulation/EcosystemSimulationManager.java:661-668` sets
feature-owned `NoAI` without changing group leadership. Only the leader reaches
`AnimalGroupManager.validateAndRecruit` past the follower return.
`ecosystem/group/AnimalGroupManager.java:196-198,388` resolves leaders by existence
and life, not ability to execute decisions. A still-loaded suspended leader is
therefore accepted by followers.

**Trigger and effect:** A group straddles a NEAR/DISTANT zone boundary with its
leader distant and still loaded, while observation rules prevent absorption.
Nearby followers can inherit stale decisions for as long as the owner remains
suspended. Normal entity-leave cleanup cannot help while that entity stays present.

**Focused correction:** Reassign leadership through the existing group manager
when an owner loses simulation eligibility, or permit bounded follower recovery
when the cached leader cannot decide.

**Missing regression:** Hold a distant leader loaded and unabsorbed while a
nearby follower must obtain an eligible decision owner.

### F07 — P2: Drinking-target cache reuse bypasses safety and species policy

**Evidence:** `ecosystem/service/CachedWaterSourceLocator.java:53-55` reuses a
200-tick section/radius cache. `valid` at lines 160-164 checks only water and
standability, omitting the flood/current checks at lines 125-128 and swimmer/depth
policy at line 137. The key does not include drinking abilities. Arrival checks
in `EcosystemBehaviorGoal.java:563` only require water presence, and drinking does
not restore the missing safety validation.

**Trigger and effect:** A shoreline floods or develops a strong current after
being cached, or a swimming species primes the cache for a nonswimmer. Later
callers can receive an approach their own search would reject.

**Focused correction:** Revalidate cached candidates using current water
conditions and requester settings; make cache compatibility explicit. Check
safety again when starting the interaction after travel.

**Missing regression:** Prime a safe target, change flood/current state, and
repeat with differing swimming profiles.

### F08 — P2: Weather reads below shallow surface water

**Evidence:** `weather/integration/WildernessWeatherWaterInfluence.java:101-105`
subtracts one from `LevelChunk.getHeight(MOTION_BLOCKING, ...)`. Targeted inspection
of the current NeoForge `ChunkAccess` source confirms that this API already
returns `getFirstAvailable(...) - 1`, the occupied block. `Level.getHeight` adds
one; these two APIs have different conventions.

**Trigger and effect:** A probe samples one-block-deep exposed water. The extra
subtraction reads its bed, classifying the column as dry. The biased coverage
feeds `AtmosphereInputSampler.java:99,110-112`, surface thermal response, and
typed inland/ocean weather potential. Deep water can mask this defect because
the lower sampled block is still wet. This finding does not claim a measured
storm-frequency change.

**Focused correction:** Query the occupied water surface directly. Check related
chunk-height samplers for the same convention mismatch without changing
intentional below-surface soil probes.

**Missing regression:** Actual chunk surface sampling for one-block and deep
water, compared with identical dry terrain. Existing `WaterInfluenceSampleTest`
only checks already-supplied coverage values.

### F09 — P2: Soil admission loses grassy vegetation coverage

**Evidence:** `watersystem/water/hydrology/RegionalSoilSampler.java:18-22` uses
that same chunk-height subtraction, then uses the lower block for both soil and
vegetation classification. A grass surface over dirt produces zero vegetation
at every probe. The no-leaves heightmap also cannot supply a meaningful canopy
leaves fraction through this branch.

**Trigger and effect:** Ordinary grassy catchments are treated as unvegetated.
The supplied vegetation fraction affects infiltration and ET in
`SoilHydrologyModel.java:19-22,35-38` and runoff residence in
`RegionalHydrologyModel.java:85`. At the same available water and forcing, zero
versus full vegetation selects a 0.25 versus 1.0 ET factor, a 1.0 versus 1.35
infiltration factor, and 12 versus 42 seconds of runoff residence.

**Focused correction:** Sample actual surface vegetation separately from any
intentional subsurface soil classification, retaining the fixed 16-column budget.

**Missing regression:** Admission sampling over grass/dirt, bare dirt, canopy,
and tagged soil, asserting the stored fractions before model evolution.

### F10 — P2: Forest canopy hides ground forage

**Evidence:** `ecosystem/service/CachedFoodAvailabilityService.java:61-65` checks
only the world-surface block and the block immediately below it. Under a thick
canopy both are leaves. The built-in
`data/wildernessodysseyapi/tags/block/ecosystem/forage_blocks.json` contains grass
blocks, grasses, and ferns, not leaves.

**Trigger and effect:** Grass-rich forest can report zero available food.
Hungry herbivore decisions then choose migration/travel for depleted food in
`EnvironmentalBehaviorDecisionModel.java:83-93`; the food input also affects
need progression.

**Focused correction:** Reuse a bounded ground probe below foliage, as the plant
scheduler already does, while preserving the 96-column food-sampling cap.

**Missing regression:** Equivalent grass/fern ground with and without a thick
leaf canopy should remain visible to the food service.

### F11 — P2: Clear cold weather cannot execute the intended shelter response

**Evidence:** `EcosystemBehaviorGoal.java:497-501,519-529` only discovers shelter
for precipitation, thunder, wind, floods, or group requests. Cold temperature is
absent, although `EnvironmentalBehaviorDecisionModel.java:37-43` explicitly
selects shelter for cold when a target exists. There is a second missing link:
`DefaultEcosystemBehaviorController.java:185-219` never executes a returned
`SEEK_SHELTER` decision, while its earlier shelter branch requires the same
non-temperature hazard test.

**Trigger and effect:** Exposed wildlife below its preferred minimum temperature
in clear calm weather does not seek nearby cover. A pure test that supplies
`shelterAvailable=true` misses both runtime gaps.

**Focused correction:** Include cold in bounded target discovery, handle the
selected shelter state through existing navigation, and use the same temperature
policy when deciding whether to leave cover.

**Missing regression:** Clear cold weather with real cover; assert discovery,
decision, arrival, and release after warming.

### F12 — P2: Final wildlife materialization omits habitat validation

**Evidence:** `ecosystem/distant/DistantWildlifeManager.java:531-546` validates
loaded terrain, view, instance spawn rules, and obstruction but does not call
the existing `habitatAllows` at line 745. That predicate is used for abstract
movement at line 420, not final spawn candidates. Spawn offsets reach 23 blocks
from the anchor.

**Trigger and effect:** Near a biome border, a valid abstract anchor can produce
a final candidate outside the species' allowed biome spawn list. Instance spawn
rules may independently reject some positions, but do not enforce this owner
predicate. This misses `docs/ecosystem.md:321,376`'s habitat-valid contract.

**Focused correction:** Check the existing loaded-only habitat predicate for
each final candidate before finalization and insertion. Keep failed population
abstract, as the current debit-after-insertion logic already does.

**Missing regression:** Biome-border materialization with rejected candidates
preserving abstract population and valid candidates succeeding.

### F13 — P2: Nine of ten severe-system ID residues cannot publish disturbances

**Evidence:** `weather/severe/SevereWeatherScheduler.java:33` runs only on ticks
divisible by ten; line 62 publishes only when `(gameTime + system.id()) % 1200`
is zero. Both conditions can hold only for IDs divisible by ten. Enumeration
over ticks 0-1199 and IDs 1-10 confirmed zero opportunities for IDs 1-9 and one
for ID 10.

**Trigger and effect:** A qualifying severe system whose ID is not divisible by
ten can produce particles and physical wind but never publish this intended
regional ecology/vegetation disturbance. `WorldDisturbanceService.java:72-96`
connects the missing signal to ecosystem memory/updates, vegetation disturbance,
and environment invalidation. Other independent weather-aware animal reactions
still exist.

**Focused correction:** Stagger within the 120 ten-tick slots, or use bounded
per-system deadlines. Preserve the existing scheduler and work cap.

**Missing regression:** Verify each ID residue receives its intended publication
opportunity across a complete 1,200-tick period.

## Documented limits relevant to the intended experience

These are existing scope choices rather than newly classified defects:

- Custom water's frozen surface response is cosmetic; it does not create a
  walkable ice sheet. Regional aggregate ice is a separate accounted store.
- The atmosphere/hydrology model approximates uncovered transport boundaries;
  it is not a globally closed climate, and it does not simulate while the server
  is stopped or reconstruct every missed storm.
- Atmosphere and regional hydrology use separate SavedData files. Their exchange
  is preserved across orderly saves, but an abrupt process crash between file
  writes is not an atomic transaction and can replay or lose the latest exchange;
  see `docs/watersystem/atmospheric-exchange.md:73-77`.
- Forage is currently an availability estimate. Complete consumption/regrowth
  and a full predator/prey food web are not implemented by that estimate.
- Abstract population is aggregated regional pressure and growth/decline, not
  individual breeding pairs, genetics, or a complete ecological network.

See `docs/watersystem/weather-coupling.md:54-58,72-90` and
`docs/ecosystem.md:367-375`. If the desired release experience requires these
behaviors, they need explicit feature work beyond the defects above.

Generation mapping is also unconditional in `GenerationWaterStateMapper.map`
and `mapFluid`, including initially authority-disabled worlds. Its relationship
to the intended disabled-world policy should be made explicit before classifying
it as a separate defect; this audit does not prescribe a conversion policy.

## Validation performed

Both commands completed successfully through the checked-in wrapper, serially:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.*' --tests 'com.thunder.wildernessodysseyapi.ecosystem.*' --tests 'com.thunder.wildernessodysseyapi.vegetation.*' --tests 'com.thunder.wildernessodysseyapi.environment.*'
```

The selected root tests executed **862 tests across 221 suites, with zero
failures, errors, or skips**, counted from the current XML reports under
`.codex-build/test-results/test/`. The compilation task reported its outputs
up to date. The test invocation compiled test sources and executed `:test`.

StructureGen emitted an existing content-catalog fingerprint mismatch warning
and declined modded catalog content; its 15 blueprints validated and generated
without errors, and the command still succeeded. No catalog was refreshed.

The audit also checked current NeoForge source entries for chunk/level heightmap
conventions and the vanilla snow-placement path. No dependency or generated JAR
was edited or extracted wholesale. The severe cadence enumeration is arithmetic
evidence, not a Minecraft execution test.

No new regression tests or production fixes were added. No full packaging build,
GameTest server, client, user world, two-client session, shader matrix, or
representative performance run was executed. Passing existing tests does not
validate the missing integration cases listed above.

## Recommended correction order

1. Repair water destination ownership, persisted-mode runtime gates, and the
   snow accounting bypass (F01-F03), with conservation and legacy-world regressions.
2. Repair atmospheric admission/application retention and herd ownership/needs
   (F04-F06), with supported-config and loaded-world transition regressions.
3. Correct target safety, terrain sampling, cold response, habitat checks, and
   severe publication cadence (F07-F13) under existing work bounds.
4. Exercise a disposable regular world and dedicated/two-client acceptance:
   legacy/new water boundaries, rainfall/SWE, clear cold shelter, forest forage,
   herd zone crossings, save/reload, and weather/water visual transitions. Record
   the exact artifact and configuration before any performance conclusions.
