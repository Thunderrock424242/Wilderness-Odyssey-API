# Reactive reservoir columns checkpoint

Date: 2026-10-28. This extends the [initial shallow-pool implementation](reactive-shallow-pools.md)
to bounded multi-depth basins, stepped receiving channels and spill openings.
The existing `enableReactiveShallowPools` server option remains **false by default**.
Its name is retained for configuration compatibility.

## Implemented behavior and boundaries

The existing canonical queue still invokes `ReactiveWaterController`. Physical
regions now contain up to **32 horizontal columns**, **eight cells per column**
and **256 capacity positions**. Their configured active-region limit remains
unchanged. Every capacity position belongs to one indexed region; admitted
members bypass voxel flow during that physical evaluation. No new ticker,
renderer, network payload, persistent schema or water inventory was added.

`ShallowPoolAdmission` follows actual horizontal openings into vertical air/
ordinary-water intervals. It locates a full solid floor, validates packed
canonical water, and records the supported ceiling/capacity. Shared X/Z alone
does not connect two rooms: conflicting vertical intervals cause rejection.
Terrain and every accessed callback halo must already be loaded. Generated
baselines, external/hosted/imported water, temporary claims, hidden displacement
and pending projection retain their earlier conservative exclusion.

The initial frame includes limited air headroom above existing water. Per-column
capacity is capped at eight slots even if more dry air exists. Positive water
outside that cap cannot be silently truncated. New water immediately above an
indexed footprint retires its old cache before replacement; a ninth wet layer,
over-deep shaft or unrepresentable connected room arrangement stays voxel-owned.
Source-local frame changes remain bounded; the sampler never explores a whole
generated ocean or an unlimited downstream world.

Some connected channels and capped single-interval chambers can now be represented.
This is not a general cave/pressure solver. Ambiguous side shapes remain blocked,
waterlogged hosts remain excluded, and no door/trapdoor permeability adapter was
added. Exceeding the column/depth/loaded envelope falls back to ordinary finite
voxel flow rather than creating an independent region over the same cells.

## Numerical and canonical contracts

`CanonicalSurfaceFlux` now consumes bed heights, whole-column capacities and
vertical face openings. Surface head is `bed + canonical units / 4096`. It reuses
`ShallowWaterFaceMath`'s local-inertial discharge and CFL bound. The existing
coastal caller retains its uncapped-opening overload and its separate derived
volume accounting.

Each face uses the higher bed as its sill and the lower adjoining ceiling as
its opening top. Discharge area is bounded by that opening. Forward momentum
cannot drain a donor whose water surface is below the sill. Outlets at or above
each sill share only the snapshot water above that threshold; lower outlets can
continue without being frozen by a nearly dry high opening. All incoming faces
share the receiver's original capacity, and newly received water cannot be
spent again during that substep. Integer rounding stays in canonical storage.

`CanonicalColumnFluxBridge` drains the highest wet donor cells and fills the
receiver's lowest available cells. Each parcel uses the existing accepted-credit
bridge, so only accepted destination units debit the donor. Partial rejection
keeps the remainder in its original owner. The tests check volume-weighted
temperature as well as exact units and packed columns. No whole numerical
column is converted back into interpolated block volumes.

The controller retains the existing four-substep cap and caller deadline.
Geometry sampling and a completed substep are bounded work, not hard real-time
preemption. Footprint replacement removes prior owners before registration,
including when a merge occurs at the region cap. A same-tick replacement does
not gain an extra initial time interval. Unprocessed time is retained in active
caches; lifecycle cache loss still preserves canonical quantities and awake
work, rather than a persistent numerical-motion history.

## World-backed proof

The existing finite dam geometry is reused by a separate opt-in GameTest.
It funds an upper inlet, fills a two-level basin, opens a one-block breach,
and drains into a lower receiving channel. The opt-in case requires physical
membership at the filled lower/upper basin and after the stepped breach.
Neither a successful voxel-only run nor the existence of a solver class meets
that assertion. All movement uses normal server ticks.

Every sampled step checks the original 18-cell ledger. Attachment serialization,
queue detachment/re-registration and physical-cache cleanup occur before breach
and after drainage. **These round trips are not actual disk eviction or a server
restart.** Those acceptance checks were not run in this earlier checkpoint.

The subsequent [disk persistence checkpoint](reservoir-disk-persistence.md)
supplies actual eviction/reload and two normal dedicated-server restarts for
this bounded reservoir. The GameTest results in this report remain the earlier,
separate checkpoint record.

Observed final 20-test run:

| Quantity | Canonical units |
| --- | ---: |
| Accepted finite inlet funding | 16,384 |
| Final upstream | 4,098 |
| Final opening | 1,368 |
| Final downstream | 10,918 |
| Conservation residual | 0 |

The fixture reached twenty stable samples after 316 sampled steps. Integer
partitions/timing can vary with queue evaluation; the exact total is asserted
throughout. These are test results, not gameplay MSPT/FPS measurements.

Additional world checks cover partial multi-layer receipts, packed cells and
temperature, suspended layers and disconnected vertical openings, cached-frame
growth, funded overtopping, deep loaded chunk-edge exchange, and the full
eight-layer/ninth-layer boundary. The prior lifecycle/disabled/cap/frontier
regressions remain in the same profile. The oversized-domain fixture now uses
36 columns, and the unsupported-host fixture remains a waterlogged slab; a
one-cell bed step is intentionally supported now.

## Validation ledger

All Gradle/Minecraft work ran serially with the checked-in wrapper and isolated
output. Approved outside-sandbox execution was needed for Gradle's user cache.
No security settings, generated JARs or dependency cache contents were changed.

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat compileJava waterSimulationTest '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.water.reactive.CanonicalSurfaceFluxTest'
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PreactiveWaterGameTests --no-parallel --init-script .codex-build/hybrid-water-barrier-jvm.init.gradle
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PwaterSafetyGameTests --no-parallel --init-script .codex-build/hybrid-water-barrier-jvm.init.gradle
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.simulation.*' waterSimulationTest build
```

- Before implementation, the complete 13-test opt-in run failed its new dam
  assertion: the multi-depth filled basin never entered the physical solver.
- Numerical and column-bridge tests first failed to compile against the absent
  column APIs. The initial eleven numerical tests then passed after implementation.
- The integrated 17-test run passed; the expanded 19-test run also passed with
  actual sill overtopping and a loaded chunk boundary.
- The new depth-limit regression reproduced one failure in a complete 20-test
  run: unused air headroom incorrectly rejected a full eight-cell column.
  Floor-first capacity admission fixed it. The final **20/20 required opt-in
  GameTests passed**, followed by normal save/shutdown. Wrapper 45 seconds;
  reported test execution 4.742 seconds.
- The default-disabled **30/30 required water-safety GameTests passed** after
  column integration, including the original voxel dam. That run preceded the
  final opt-in-only headroom repair. Wrapper 32 seconds; reported tests 2.703 seconds.
- Final root water/weather suite: **592 tests across 132 suites**, zero
  failures, errors or skips. The offline `waterSimulationTest` passed **211
  tests across 41 suites**, also with zero failures, errors or skips. These
  suites overlap; the counts must not be added as unique tests.
- The same final invocation packaged successfully in **35 seconds**, without
  the GameTest JVM init script or test-source exclusions. The built
  `wildernessodysseyapi-5.0.0.jar` contains the column/layout model, column flux
  bridge, column GameTests and physical dam test. This is focused root coverage,
  not a complete-repository JUnit pass.
- Main-agent review checked topology windows, shared availability/capacity,
  provenance, default-disabled behavior, loaded reads, same-tick replacement and
  lifecycle. The eight-layer admission finding was reproduced before repair.
  Diff, new-file whitespace and conflict-marker checks passed. No independent
  review, commit, push or remote operation was performed by this work.

GameTest runs retain the previously documented **session-only JVM compiler
exclusion** for the observed Guava/Temurin native crash; see the
[earlier dam report](canonical-dam-checkpoint.md). No production JVM option changed,
and these results do not prove that native JVM failure has been fixed.

## Remaining work

The subsequent [disk persistence report](reservoir-disk-persistence.md) covers
actual disk unload/reload and normal dedicated-server restart conservation.
Integrated-client Save & Quit remains pending.
Larger or unlimited open domains, general 3D/pressure interfaces, verified
floodgate geometry, funded storm propagation, river/SPH transitions and client/
two-client/optional-mod/performance acceptance remain. The implementation is
bounded and opt-in, and no live visual or long-duration performance claim is made.
