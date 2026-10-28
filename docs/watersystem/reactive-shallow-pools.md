# Reactive shallow pools: initial physical-region checkpoint

Date: 2026-10-28. This extends the cinematic and canonical-dam checkpoints.
It is a deliberately limited Phase 3 implementation, not the complete Hybrid
Water Engine 2.0 or a general deep-reservoir height-field solver.

## Active integration and authority

The existing `WildernessFluidRegistry` canonical queue calls
`ReactiveWaterController.tryTick` before voxel discharge. An admitted region
handles that invocation; its members do not also run the voxel solver in the
same evaluation. A per-level tick set prevents multiple member evaluations or
same-tick topology replacement from advancing the patch twice. The controller
adds no event subscriber, scheduler, executor, renderer or saved inventory.

`ShallowPoolAdmission` follows connected horizontal air/owned-water cells using
only admitted loaded terrain and the existing callback safety halo. It admits
at most 16 cells at one elevation, with full solid floors, air above and closed
dry side barriers. Unknown frontiers, open drops, deeper columns, replaceable
plants, untracked generated baselines, external fluids, hosted/imported water,
temporary claims, pending projections and hidden displacement are rejected.
Ambiguous solid side shapes remain closed; no sub-block permeability is added.
Rejected domains continue through existing voxel flow. Generated oceans remain
compact rather than being imported into this solver.

The membership map is disjoint. A topology merge removes old memberships before
publishing its replacement. The default cache limit is 64 regions per dimension
(configurable 1–256); excess admissions fall back to voxel flow. Lowering that
limit prevents new admissions; already cached regions retire through normal
sleep/invalidation/lifecycle handling.

## Numerical and canonical boundary

`ShallowWaterFaceMath` is the shared local-inertial discharge and CFL arithmetic
used by both the derived coastal grid and this physical path. The coastal grid
keeps its represented depth and boundary accounting; none of that volume funds
canonical requests.

For each substep, `CanonicalSurfaceFlux` reads fresh canonical integer units,
computes signed face discharges in double precision, and applies shared donor
and receiver limits against the immutable snapshot. It cannot spend incoming
water or newly vacated capacity in the same substep. Quantization rounds each
request down; remainders stay in canonical storage. No interpolated depth is
written back as water volume.

`CanonicalFluxBridge` uses `CanonicalWater.addTransferredVolume`; only accepted
credit debits the donor on the same server thread. Ordinary temperature and
position/parcel flag handling reuse the existing canonical transaction. Initial
admission excludes temporary-funded water; its voxel/flood owner stays active.
No rain, ocean input, regional claim, SPH mass or cosmetic wave funds this path.

The region stores only face momentum, membership and time. It refreshes volume
before each substep and attempts at most four CFL-limited substeps per queued
evaluation, observing the caller's existing absolute time deadline. Unprocessed
time is retained while the cache remains active. Admission/shape checks and an
individual canonical transaction are bounded work, not hard real-time preemption.
This is an experimental local-inertial approximation, not calibrated CFD.

## Lifecycle and diagnostics

Accepted dry-terrain edits invalidate nearby indexed members before checking
for adjacent wet cells. A change beside a currently dry part of a region can
therefore wake water farther inside it. Geometry is resampled before the next
physical step. Quiet regions release their cache after twenty quiet evaluations
and leave canonical cells sleeping in their existing chunk attachments.

Chunk unload drops affected caches before detaching canonical work. Level cleanup
and feature disablement drop only caches. Cached timing and momentum are not a
new persistent physics history; canonical quantities and awake work remain the
durable state. This does not establish real disk eviction or restart acceptance.

An isolated withdrawal regression exposed an empty-pool membership leak: once
all parcels were drained, no wet queued cell remained to release the cache.
Canonical cell-change notification now releases that membership immediately.
The same callback retires membership when provenance becomes unsupported; it
wakes remaining canonical work without moving or deleting any units.

`/wowater inspect` includes read-only `reactive=RegionStatus[...]` detail for its
position. `ReactiveWaterController.status` provides aggregate active cells,
regions, completed evaluation count, accepted units moved and deferred seconds.
These are diagnostics, not a new water ledger. Moved units are cumulative
transport (a parcel can move repeatedly), not newly created inventory.

## Configuration and validation

The server's `water_simulation.enableReactiveShallowPools` defaults to **false**.
`water_simulation.reactiveShallowPoolMaxRegions` defaults to **64**. Clients need
no extra option or packet: presentation still consumes canonical snapshots.
Enable this experimental path only in a disposable acceptance world first.

Normal serial validation commands:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.water.reactive.*' --tests 'com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShallowWaterGridTest'
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PreactiveWaterGameTests --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PwaterSafetyGameTests --no-parallel
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.simulation.*' waterSimulationTest build
```

The new GameTest profile uses `run-reactive-water-gametest` under the isolated
build directory and a separate namespace, reusing `water_flow.nbt`. Tests that
disable the feature or release chunk caches run in separate batches to avoid
clearing another test's in-progress state.

Measured results:

- Initial numerical tests failed to compile because the new planner was absent.
  After implementation, all **17** focused numerical/coastal tests passed.
- Initial integration compile caught use of a package-private generated-span
  helper. Admission now reads an existing loaded attachment without expanding
  that API; compilation passed.
- Initial five-test server run had one test-isolation failure: a topology test
  counted every active region rather than the region at its position. Positional
  diagnostics corrected the assertion; the expanded eight-test run passed.
- A first empty-pool test was masked by another fixture's global cache clearing.
  In its own batch, the complete nine-test run had exactly one required failure:
  empty membership survived withdrawal. The callback repair addresses that case.
- A separate provenance regression reproduced stale membership after a cell
  became imported. The expanded callback now retires that membership immediately.
- The final **12/12** required opt-in GameTests passed, including natural tick
  routing, exact capacity/temperature, same-tick suppression, dry-member edits,
  deadline deferral, sleep, cache release, empty withdrawal, size/region caps and
  an unloaded frontier and unsupported provenance. Wrapper 32 seconds, reported
  GameTests 2.254 seconds.
- Final default-disabled water-safety profile: **30/30** required GameTests
  passed, including the existing finite dam lifecycle and read-only inspection.
  Wrapper 31 seconds, reported GameTests 2.568 seconds.
- Final root water/weather coverage: **586 tests across 132 suites**, zero
  failures, errors or skips. The offline `waterSimulationTest` also passed
  **205 tests across 41 suites**, zero failures, errors or skips. These suites
  overlap; their counts are not a combined unique-test total.
- The same final invocation packaged successfully in 36 seconds without a JVM
  init script or test-source exclusions. This is focused root coverage, not a
  complete-repository JUnit run. The built `wildernessodysseyapi-5.0.0.jar`
  contains the controller, flux bridge/planner, shared math, GameTests and their
  namespaced template. No generated binary was edited by hand.
- The main author reviewed the diff for authority, geometry, callback/thread
  assumptions, cache bounds and lifecycle. Two review regressions reproduced
  and repaired the empty-pool and unsupported-provenance membership leaks.
  Scoped diff/whitespace/conflict checks passed. Unrelated playtest edits were
  preserved; no independent reviewer, commit or remote operation was involved.

Both server profiles in this checkpoint use the previously documented ignored
session-only init script `hybrid-water-barrier-jvm.init.gradle`, adding the exact
Guava compiler exclusion described in [the dam report](canonical-dam-checkpoint.md).
No production JVM settings changed. These results do not prove that the native
JDK crash is fixed. No speedup, MSPT, FPS or multiplayer result is claimed.

## Remaining engine work

General multi-layer/stepped surface columns, larger open domains and interfaces
to voxel waterfalls still need verified admission and flux contracts. Deep dams,
floodgates, funded storm-surge propagation, river/SPH transitions, actual disk/
restart conservation, client appearance, two-client synchronization, optional-mod
profiles and prolonged performance acceptance remain. The default finite voxel
path and the earlier controlled dam test continue to cover their own scopes.
