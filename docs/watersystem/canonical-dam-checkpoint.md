# Canonical barrier and dam lifecycle checkpoint

Date: 2026-10-28. Starting branch: `in-dev`, commit `3eb0cee5`.

This extends the [hybrid-water checkpoint](hybrid-water-engine-2.md) through the
existing finite voxel authority. It does not introduce a physical height-field
region solver, another reservoir, or a second water scheduler.

## Confirmed gap and implementation

Player placement events and native neighbor updates already wake local water,
but they do not cover every automation write. A direct dry-solid-to-air change
with neighbor notifications suppressed could leave adjacent canonical water
asleep. The same gap could retain an exposed displacement parcel indefinitely.
The first new world-backed regression reproduced the missing adjacent wake.

`ServerWaterBarrierMixin` observes successful `LevelChunk#setBlockState` returns.
`WaterBarrierUpdates` admits only changes between dry passable and dry blocked
states near already-loaded authority-owned water. It reads the changed cell and
six neighbors, using `getChunkNow` for a neighboring chunk. Generated spans are
checked against the existing authority; sparse dry overrides retain precedence.
The changed chunk must be the exact instance in the server's loaded cache;
detached chunks sharing its coordinates cannot notify live-world water.
Irrelevant edits do not create attachments or flow queues.

The observer calls the established `WildernessFluidRegistry.notifyTerrainChanged`
path once for an admitted edit. Existing cache invalidation, bounded local
generated-water materialization, watershed terrain refresh and canonical wake
queues remain responsible for their work. The ordinary finite-flow ticker
performs transfers under its existing cell/time limits and loaded callback halo.
The observer never projects blocks, moves volume or adds an executor itself.

The admission path skips:

- no-op and still-blocked/still-passable replacements;
- fluid projection changes and unsupported fluid-containing hosts;
- clients, off-thread writes, shutdown, disabled authority;
- provisional block snapshots and their restoration;
- internal canonical projection/reconciliation and bounded starter generation;
- detached changed chunks and unavailable neighboring chunks.

`WorldFluidMutationReconciler.suppressesTerrainWake` exposes only the existing
internal-write/generation context needed for this admission. The new mixin is
registered in the common mixin list and has no client dependencies. NeoForge has
no general accepted non-player chunk-write event that covers this path, hence
the narrow return injection rather than another broad polling loop.

## Supported barrier interpretation

Air and existing replaceable canonical hosts are passable; unsupported solids
remain blocked. A full barrier physically replaced by air can release water.
No sub-block permeability for stairs, fences, slabs, doors, trapdoors,
waterlogging or modded hosts is claimed. Opening a door without replacing its
nonreplaceable host does not become a new fluid opening through this change.

No configuration default, water quantity, funding mechanism, persistent schema,
network payload, entity force owner or erosion permission was added. This repairs
the accepted-edit wake path of the already-enabled water system. New advanced
physical-region behavior remains a separate feature-gated implementation task.

## Tests and controlled dam fixture

`WaterBarrierGameTests` adds seven loaded-world regressions:

1. Direct flags-2 breach wakes sleeping water and flows through ordinary ticks.
2. A hidden displacement parcel resumes after automation removes its solid.
3. No-op, still-closed and provisional/restoring writes preserve sleeping state
   and water revision.
4. A generated parcel stays lazy until an accepted breach, then enters the
   existing local queue without duplicating its generated baseline.
5. Bounded starter-generation terrain writes do not enter runtime scheduling.
6. A breach finds canonical water across a naturally loaded chunk boundary.
7. A detached chunk with live-world coordinates cannot wake the registered
   chunk's water.

`CanonicalDamGameTests` adds a controlled inlet, enclosed two-level basin,
removable wall and lower receiving channel. Only accepted canonical inlet credits
increase its finite funding ledger. Each sampled step checks all 18 possible
canonical cells, including the opening, against that ledger. The closed receiver
must stay dry; filling must raise water into the upper basin. After breach,
upstream storage must fall, downstream storage must rise and discrete levels
must remain unchanged for twenty sampled ticks.

At filled and drained stages the fixture serializes the actual chunk attachments,
unregisters their runtime queue indexes, decodes the attachments with the
production serializer and re-registers their work. Exact per-cell levels and
aggregate units must survive; physical flow after the first round trip exercises
the restored queue. This is an attachment/queue lifecycle test, **not** actual
disk eviction, Save & Quit, or a server restart.

Observed fixture ledger in the final successful 30-test run:

| Quantity | Canonical units |
| --- | ---: |
| Accepted finite inflow | 16,384 |
| Final upstream | 4,301 |
| Final opening | 1,349 |
| Final downstream | 10,734 |
| Final residual | 0 |

The fixture finished after 200 sampled steps and twenty stable samples. These
counts and GameTest execution times are not gameplay MSPT/FPS measurements.

## Commands and results

Commands use JDK 21, the checked-in wrapper and isolated output, one process at a
time. Gradle cache access required the approved outside-sandbox execution; the
first sandboxed compile stopped before source compilation with access denied on
the wrapper's `gradle-9.8.1-bin.zip.lck`. No permissions/security/cache changes
were made.

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat compileJava waterSimulationTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PwaterSafetyGameTests --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PwaterSafetyGameTests --no-parallel --init-script .codex-build/hybrid-water-barrier-jvm.init.gradle
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.simulation.*' build
```

- Baseline compilation passed.
- Initial 25-test run: adjacent breach wake failed as expected; the parcel test
  first had a fixture error because its water position was still solid. That
  setup was corrected. A later pre-fix run was interrupted after reproducing the
  adjacent-wake failure; it is not a completed passing run.
- Initial implementation compile found an attempted call to a package-private
  helper. The implementation now uses the existing public authority query;
  compilation passed without broadening the helper's API.
- Review added a detached-chunk regression. Before the identity guard, the
  complete 30-test run had exactly one required failure: the detached observer
  woke live-world water. The guard requires the registered loaded instance.
- Focused `waterSimulationTest`: **199 tests**, zero failures/errors.
- Default-runtime post-fix server run: **environment/runtime failure before
  tests**. Temurin 21.0.6+7 crashed in `matcher.cpp:2205` on `C2 CompilerThread1`
  compiling `com.google.common.cache.LocalCache$Segment::removeValueFromChain`.
  Evidence is in the ignored local `hs_err_pid40880.log`. This is not a passing
  GameTest run or evidence of a water assertion failure.
- A session-local Gradle init script adds exactly
  `-XX:CompileCommand=exclude,com.google.common.cache.LocalCache$Segment::removeValueFromChain`
  to `runGameTestServer` only. No production build setting or JDK installation
  changed. With that diagnostic: the first **25/25**, then **29/29**, and finally
  **30/30 required GameTests passed**, followed by successful save and shutdown.
  The final wrapper invocation took 46 seconds; reported test execution was
  4.493 seconds. This does not prove prevention of native crashes in the modpack.
- Final broad root water/weather suite: **580 tests across 131 suites**, zero
  failures, errors or skips. The same wrapper invocation packaged successfully
  in 44 seconds, without the GameTest init script or test-source exclusions.
  This is focused root coverage, not a full-repository JUnit pass.
- The packaged `wildernessodysseyapi-5.0.0.jar` contains the new common mixin,
  its registration, admission helper and both GameTest classes. Scoped diff
  checks passed; unrelated playtest diagnostics edits were preserved.

## Changed files and remaining work

New production integration:

- `src/main/java/com/thunder/wildernessodysseyapi/mixin/ServerWaterBarrierMixin.java`
- `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/fluid/WaterBarrierUpdates.java`
- existing `WorldFluidMutationReconciler.java` and common mixin registration

New world-backed tests: `WaterBarrierGameTests.java` and
`CanonicalDamGameTests.java` in the existing `watersystem/water/fluid` package.
Existing `water_flow.nbt` is reused unchanged; no new binary fixture is needed.
The main author reviewed authority ownership, loaded-chunk identity, internal
write suppression, server-only admission, exact funding and fixture boundaries.
No independent review or client visual/performance acceptance is claimed.

Remaining: disjoint advanced physical regions and their numerical/canonical flux
bridge; retained topology across unavailable terrain; verified permeability
adapters; actual disk unload/reload and restart acceptance; multiplayer, client
water-level presentation, optional Sable/C2ME profiles and prolonged performance.
Edits can wake only authority state visible in loaded chunks; this patch does
not add a durable notification ledger for water wholly in an unloaded chunk.

The subsequent [reactive shallow-pool checkpoint](reactive-shallow-pools.md)
implements a restricted, default-disabled physical-region/flux path. General
deep/stepped/open reservoirs and the acceptance limits above remain pending.

For manual acceptance, use a disposable world: fill a bounded basin with measured
finite input, remove the retaining block through a normal player action and
through supported automation, inspect exact `/wowater` balances, save/reopen and
compare two clients. Operator GameTests deliberately replace terrain and belong
in their disposable test world.
