# Waterfalls, finite discharge and coastal storms

Date: 2026-11-02. Minecraft 1.21.1 / NeoForge 21.1.262, on `in-dev` with uncommitted changes.

This extends the existing [hybrid engine](hybrid-water-engine-2.md),
[reactive reservoir columns](reactive-reservoir-columns.md) and
[storm runoff](storm-runoff-checkpoint.md). The user selected waterfall forces
and finite discharge before larger experimental open-water regions.

## Ownership and implemented behavior

`WildernessWaterAuthority` and canonical/generated water remain the only physical
owners. `WildernessFluidRegistry` still advances disturbed finite water under its
existing queue, elapsed-time and deadline limits. `WaveEntityPhysics` still owns
server entity forces, and `WaterRenderCoordinator` still owns rendering. No new
ticker, executor, packet, water reservoir, renderer event, dependency or mixin was
added to production gameplay.

### Waterfall flow and impacts

- Finite gravity discharge now retains existing downward velocity instead of
  resetting every parcel to resting head speed. Exact source/capacity limits and
  accepted-destination-first transfers remain intact. The existing eight m/s
  fall-speed cap remains. The optional SPH handoff receives the same elapsed-time
  gravity velocity as the canonical receiver.
- `WaterfallCurrent` validates generated waterfall metadata against the current
  loaded wet curtain, including sparse edits. At most five neighboring candidates
  and seventeen cells per admitted curtain are considered. Missing chunks, dry
  gaps, unsupported thin/hosted water and absent physical inflow reject the
  derived current. Metadata's planned speed alone cannot create a jet.
- `WaterfallHydraulics` derives local downward velocity from **lip** inflow and
  fall distance. Selecting the strongest candidate prevents overlapping falls
  from multiplying the force. Receiving-water motion spreads outward with a
  small eddy component, decays with distance/depth, and is capped at 1.5 m/s
  horizontally. Reduced-time phases remain stable in long-running worlds.
- `AuthorityWaterAccess` exposes this current through the existing public sample,
  drag and multi-point buoyancy paths. Client motion remains presentation; the
  server remains authoritative. The model owns no parcels and transfers no water.

### Waterfall surface detail

Native falling-fluid geometry remains the water curtain and compatibility
fallback. The new coordinator subpass dresses verified exposed faces with
animated whitewater material. It does not hide native faces or replace shader-pack
water. External shader packs retain their own curtain material.

Discovery checks 128 immutable metadata columns per client tick in a fixed nearby
window. There are at most 32 candidate slots, four refreshed per tick, at most
64 cached face quads per candidate, and one frame budget: Low 0, Medium 48,
High 128, Cinematic 192 quads. No world scan or mesh rebuild runs each frame.
Renewing a candidate preserves its geometry and impact cooldown; snapshots,
distance/expiry and level teardown remove stale work. Terrain-only edits can take
up to one eight-tick refresh cycle to update cached face exposure.

Receiving impacts reuse the capped shared displacement/foam and environmental
effect pools. Floating foam is offered only on a real neighboring receiving
surface; mist remains at the plunge base. These effects cannot create water.

Client control: `water_rendering.enableWaterfallSheets`, default **true**.
The existing river foam/spray and quality controls still apply to their effects.

### Conserved coastal storm surge

This is an **experimental coarse ocean-boundary model**, disabled by default,
not a general three-dimensional pressure solver or CFD storm surge.

Cached pressure, storm energy and onshore wind determine a bounded coastal target.
Onshore exposure reads four already-known regional terrain neighbors; it never
loads or scans terrain. Forcing is saved and follows the existing historical
catch-up policy. Old saves without the field begin with zero surge forcing.

Accepted `OCEAN_IN` credits fund existing regional `FLOODPLAIN` storage. Stage is
derived from that storage **plus live detailed flood claims**. A constant-time
funding index in `TemporaryFloodSavedData` is rebuilt from its existing saved
entries. Lifetime projection receipts are not live inventory: externally drained
water must not retain a phantom stage or prevent replacement funding.

The existing flood manager owns loaded-only, connected, protected-terrain
projection. A coastal column may rise from actual water beneath it; a full
projected voxel must fit below the funded mean waterline. Cosmetic waves never
open that gate. Recession records `OCEAN_OUT` only for accepted regional debits;
the existing canonical claim owner returns detailed water after aging and motion
permit removal. External withdrawals stay exported and are not credited back.

Mesh, immersion and common surface sampling compose coastal offsets from the
same synchronized representative sea level. An already raised canonical column
receives only the remaining height toward that absolute level. Common generated
surface lookup also follows up to four connected temporary canonical cells above
its immutable baseline, keeping real raised water queryable. Inland level-offset
behavior is preserved. Existing visual offset caps still apply; actual canonical
cells remain a lower bound on the presented surface.

Server controls under `regional_hydrology` within the water-simulation category:

| Setting | Default | Range / unit |
| --- | --- | --- |
| `enableStormSurge` | false | experimental physical flooding |
| `maximumStormSurgeMetres` | 2.0 | 0–4 metres |
| `stormSurgeResponseSeconds` | 120.0 | 20–3600 simulation seconds |

The watershed/weather coupling, dimension and localized-flood controls remain
authoritative. Disabling surge returns stored ocean-boundary water and permits
normal claim recession. The existing reactive-region option remains disabled by
default and was not expanded by this work.

## Bugs reproduced and fixed

Regression tests first failed for missing jet/discharge and sheet/storm behavior.
Additional review and loaded-world tests reproduced:

1. Fast lower parcels incorrectly becoming the waterfall lip's ballistic seed.
2. Eddy normalization slightly exceeding its horizontal speed cap.
3. Curtain quad winding pointing away from the exposed view.
4. Periodic discovery discarding published geometry and impact cadence.
5. Re-funding already projected surge water, or retaining phantom water after an
   external withdrawal, when using lifetime receipts as live inventory.
6. Applying the whole stage again above an already raised canonical surface.
7. Generated-surface lookup ignoring actual canonical floodwater above its top.
8. Flood projection lacking a funded vertical coastal start and mean-waterline
   check.

The new coastal test fixture also required corrections to its seed datum, full
template-roof clearance and age-aware recession wait. Those were fixture defects,
not evidence of a native JVM or production terrain failure. One read-only review
was performed; its three Important issues and the rediscovery flicker were
reproduced and repaired, with no finding deferred.

## Completed automated validation

All development processes ran serially through the checked-in wrapper and
`-PcodexBuildDir=.codex-build --no-parallel`.

| Check | Actual result |
| --- | --- |
| Focused numerical/geometry fixes | RED failures observed, then GREEN |
| Water/weather/mixin regressions | 881 tests, 194 suites; zero failures, errors or skips |
| Offline `waterSimulationTest` | 241 tests, 45 suites; zero failures, errors or skips; overlaps root coverage |
| Loaded water-safety profile | **33/33 required GameTests passed**, normal save/shutdown; 32-second wrapper |
| Isolated coastal storm profile | **1/1 required GameTest passed**, normal save/shutdown; 33-second wrapper |
| Full `test build waterDynamicsBenchmark` | **Successful**, one minute; root 1,895 tests/478 suites, zero failures/errors, five skips |

The full suite executed 1,890 root tests successfully. Five skipped cases were
three unavailable symbolic-link capabilities, the separate opt-in large bunker
fixture, and the opt-in cloud shader fixture. Subproject test tasks were
up-to-date; their execution is not claimed as fresh. Production water GPU fixtures
ran in the root water suite. Existing catalog fingerprint/deprecation and
unrelated optional-mod startup warnings remain separate.

The loaded coastal test measured these exact milli-canonical receipts:

| Receipt | Milli-units |
| --- | ---: |
| Ocean input | 1,572,880,926 |
| Ocean return | 1,571,856,926 |
| Projection out | 12,288,000 |
| Projection return | 11,264,000 |
| External canonical withdrawal | 1,024,000 |
| Regional conservation residual | **0** |

All remaining live detailed claims receded. The external withdrawal stayed
exported. This includes regional serialization/reload and actual block/claim
operations; it does not certify a separate disk eviction/server restart or a
naturally formed atmospheric storm.

Useful commands:

```powershell
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.*' --tests 'com.thunder.wildernessodysseyapi.mixinconfig.*'
.\gradlew.bat waterSimulationTest waterDynamicsBenchmark '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PwaterSafetyGameTests --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PauditGameTestRun=coastal-storm '-Dforge.enabledGameTestNamespaces=wildernessodysseyapi_coastal_storm_tests' --no-parallel
.\gradlew.bat test build '-PcodexBuildDir=.codex-build' --no-parallel
```

The observed dedicated GameTest runs retained the two existing launch compiler
exclusions and the prior session-only Guava exclusion in
`.codex-build/hybrid-water-barrier-jvm.init.gradle`. They used normal JIT. No JVM,
driver, security, ACL or global Java setting changed, and these runs do not prove
that the earlier native JVM/GLFW failures are generally resolved.

## Measured performance and acceptance limits

`waterDynamicsBenchmark` uses the normal offline Gradle runtime: Temurin 21.0.6,
32 processors exposed, 512 MiB heap, three warmups and seven measured rounds.
Each round advances 32 regions through 3,300 two-second intervals (105,600 steps,
including repeated storm build/recession and a detailed projection per region)
and samples 100,000 jets/impacts. Final median **12.319 ms**, slowest **13.002 ms**;
every interval checks zero residual and final claims are zero. This is pure
arithmetic cost, not real-time storm formation, entity throughput, GPU cost,
gameplay FPS or whole-server MSPT.

The loaded waterfall public-current fixture used 128 warmups and five rounds of
128 queries. Median **7,038 ns/query**, slowest round **15,891 ns/query**. It checks
the complete API on a small loaded waterfall; it is not a population or server
budget guarantee.

The [live renderer acceptance record](water-dynamics-runtime-acceptance.md)
documents native and active Sodium water/reload checks and the Iris/Distant
Horizons startup with Complementary Unbound shader activation. At the user's
request, remaining hands-on tests are handed over to them. Larger open
reactive regions/pressure geometry, a natural sustained storm/flood campaign,
new waterfall visual travel/boat acceptance, two-client synchronization and a
representative 30-minute FPS/GPU/memory soak remain separate acceptance work.
No commit, push, unrelated branch or system setting change was made.
