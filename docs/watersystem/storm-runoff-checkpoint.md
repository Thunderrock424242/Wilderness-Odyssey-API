# Storm runoff conservation checkpoint

Date: 2026-10-29. This follows the [reservoir disk/restart checkpoint](reservoir-disk-persistence.md).

## Scope and ownership

This checkpoint exercises a measured 128 mm pulse over one 256 m2 catchment:
32.768 m3, or **134,217,728 milli-canonical units**. The pulse enters the existing
saved atmospheric receipt book once. Normal regional scheduling moves runoff
into a river, routes accepted discharge to a lower finite lake, reserves exact
parcels for canonical floodwater, and returns those parcels during recession.

The combined ledger is regional storage plus detailed canonical volume. Claims
are provenance for that same detailed water, not another reservoir. Each audit
requires zero regional residuals, matching upstream/downstream receipts, exact
per-cell funding and projection, and a combined total equal to the measured
input. Regional projection-out minus projection-in must equal detailed claims.
The solid protected sample must remain intact.

Ambient weather coupling is disabled in this disposable fixture so unrelated
rain cannot change the measured input. The test injects an explicit finite
precipitation receipt; it does not prove atmospheric storm formation. River and
lake evolution, flood expansion/recession and persistence use existing owners.
No new gameplay scheduler, reservoir, renderer, packet or storage schema was
introduced. Temporary funding flags remain intact, including their exclusion
from the opt-in reactive height-field solver.

## Repairs reproduced by tests

`RegionalHydrologyModel` rounded floodplain-to-river drainage down. At the
two-second interval, a positive tail of up to 60 milli-units could remain
forever. `TemporaryFloodManager` requires an empty floodplain before returning
funded cells, so that tail could hold detailed flooding indefinitely. Both a
full storm and an isolated final milli-unit reproduced this failure.

The outward floodplain parcel now rounds up by at most one milli-unit. Exact
source storage and available channel room still clamp the transfer. No water
is discarded or created, and a full channel retains the rejected tail. The
existing durable format is unchanged.

The world-backed expansion path also used `ChunkAccess.getHeight()` as an air
height. That API returns the occupied surface voxel. On ordinary ground the
candidate was therefore solid and rejected. Expansion now adds one to select
the first voxel above the surface, retaining all normal safety/connectivity
and funding checks. A focused GameTest reproduced the solid-ground failure.

## Dedicated acceptance profile

Use a new run ID and run commands serially with JDK 21:

```powershell
.\gradlew.bat runWaterPersistenceServer '-PcodexBuildDir=.codex-build' -PwaterPersistenceScenario=storm -PwaterPersistenceRun=storm-example-1 -PwaterPersistenceStage=prepare --no-parallel
.\gradlew.bat runWaterPersistenceServer '-PcodexBuildDir=.codex-build' -PwaterPersistenceScenario=storm -PwaterPersistenceRun=storm-example-1 -PwaterPersistenceStage=verify --no-parallel
```

The scenario uses the existing isolated normal dedicated-server profile at
`.codex-build/run-water-persistence-<id>/`. It binds localhost with an ephemeral
port. Its regional config uses a 0.25 m bankfull depth, zero ET, disabled thermal
exchange and the existing minimum admission cap of 64. A known dry catalogue
retains 62 actual disconnected native spawn nodes and admits the two loaded
catchment nodes before construction. Any prior inventory, regional receipts,
atmospheric receipts or pending rain reject this test-only setup. The ordinary
drainage graph must establish the source-to-lake route before rain is injected.
The known chunk tickets are harness-only and are not persistent force-loads.

Prepare waits for two available parcels, bootstraps one exactly funded detailed
cell, then requires additional funded expansion, river discharge, lake rise and
active remaining floodplain water. It stops normally. Verify uses a new JVM, compares exact persisted
region fields, receipt-book/clocks and claim fields. At the supported async
`ChunkDataEvent.Load` boundary it copies native disk NBT and the normally decoded
attachment before main-thread activation, comparing every persisted field in
both. All assertions and world operations stay on the server thread. It then
requires additional downstream progress and
complete funded recession. Verify does not rebuild terrain, re-inject rain or
manually wake/schedule water. Expected `storm-active.nbt` is assertion data only
and is never deserialized into gameplay owners.

The server target is 500 TPS to shorten the long recession experiment. World
time is not jumped, and model steps still come from the normal scheduler and
elapsed game ticks. This target is a test setting, not a measured performance
result. Each task has an eight-minute timeout. A matching post-stop PASS
receipt must include the current execution nonce and exact combined inventory;
verify must report automatic resumed flow and completed recession.

Early native runs retained the session-only Guava exclusion from the
[persistence report](reservoir-disk-persistence.md). Cohort D then crashed in
Temurin 21.0.6's native C2 compiler while compiling WorldEdit
`BlockState::generateStateMap`, before the probe started. Later cohorts use a
session-only ignored init script, appended as
`--init-script .codex-build/storm-water-jvm.init.gradle`:

```groovy
gradle.projectsEvaluated {
    gradle.rootProject.tasks.named('runWaterPersistenceServer', JavaExec) {
        jvmArgs '-XX:TieredStopAtLevel=1'
    }
}
```

Production JVM defaults are unchanged. Results with this diagnostic setting
do not certify normal-tier compiler stability or performance on this machine.

## Validation evidence

The initial hydrology red run reproduced the stuck 60/1 milli-unit tails.
After the correction, all 74 focused hydrology tests across 16 suites passed.
The storm test also checks a second funded parcel, saved receipt-clock replay
rejection, exact reserve/return accounting, a rising/falling hydrograph, lake
storage, a full-channel rejection and an unknown closed frontier.

The focused heightmap GameTest uses namespace
`wildernessodysseyapi_storm_runoff_tests` and the shared `water_flow` template:

```powershell
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PauditGameTestRun=storm-heightmap-example '-Dforge.enabledGameTestNamespaces=wildernessodysseyapi_storm_runoff_tests' --no-parallel --init-script .codex-build/hybrid-water-barrier-jvm.init.gradle
```

The actual red run failed its sole required test with “Flood expansion sampled
solid ground instead of connected air above it.” Initial template setup was
corrected to use the existing Gradle template-copy pattern before that red run.
The green run passed its sole required test and exited successfully in 1m27s.

The actual missing-save run `storm-missing-20261029-b` (PID 20924) wrote FAIL for
`Missing prerequisite storm-active.nbt`, and Gradle rejected it. An earlier
attempt caught the profile using the wrong config location; it was corrected
to the repository's registered `config/wildernessodysseyapi/` path.

Early positive fixtures are retained locally as failed evidence: cohort A
waited on a topology assumption about the native admission ring; B allowed
construction callbacks to consume its admission slots; C ran the finite pulse
and completed the bootstrap parcel's recession with zero sampled residual,
but never met the additional-expansion requirement. Source inspection and the
separate heightmap regression isolated that last problem before repair.

The complete cohort G passed prepare and verify in separate JVMs (34636 /
42812). It finished at tick 35,660 with all 134,217,728 milli-units in regional
storage, no detailed claims, 28,672,000 milli-units returned, automatic resumed
flow and zero residual. One earlier cohort F matched region/receipt/claim
fields but reported a late attachment mismatch without field-level capture.
G did not reproduce it. The older uncaptured mismatch is not evidence of a
proven storage repair.

The final cohort H (`storm-20261029-h`) passed the stronger pre-activation
boundary checks and complete recession. Prepare completed in 1m21s. Its first
verify invocation ended without startup/receipt evidence; process inventory
confirmed no test JVM remained, and verify was resumed against the untouched
saved world. That invocation completed successfully in 1m49s.

| Observation | Prepare | Verify |
| --- | ---: | ---: |
| Dedicated JVM PID | 44148 | 8748 |
| Checkpoint game tick | 700 | 35,620 |
| Regional inventory, milli-units | 121,929,728 | 134,217,728 |
| Detailed canonical inventory, milli-units | 12,288,000 | 0 |
| Lake storage, milli-units | 3,436,044 | 116,350,338 |
| Cumulative projection returns, milli-units | 0 | 28,672,000 |
| Combined inventory, milli-units | 134,217,728 | 134,217,728 |

Verify matched every saved region, receipt-book, clock and claim field before
normal ticks. Native disk NBT and the decoded attachment both exactly matched
the saved 48-cell canonical snapshot, including its revision and all persisted
cell fields, before activation. Normal simulation then increased downstream
delivery and lake storage. Peak river stage was 0.4140714111 m and peak discharge
1.208818604 m3/s. Floodplain storage, detailed cells and funding claims reached
zero; projection-out equaled projection-in at 28,672,000 milli-units. The
hydrograph fell and remained in the required stable recession state. All
sampled regional residuals and the combined inventory residual were zero.
Both execution-specific post-stop receipts were PASS, and Gradle accepted them.

Final regression and packaging used the normal JVM configuration:

```powershell
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.simulation.*' waterSimulationTest build
```

The command completed successfully in 38s. Fresh XML reports contain 603 root
water/weather tests across 134 suites and 222 offline water tests across 43
suites, with zero failures, errors or skips. These suites overlap; they are not
825 unique tests or a full-repository JUnit pass. Compilation and mod packaging
passed. The working diff and new source files were reviewed for conservation,
async observation safety, scenario/lifecycle guards, isolated output,
unrelated changes, secrets and whitespace; `git diff --check` passed.

Local evidence is retained under `.codex-build/`: `storm-runoff-prepare-h.log`,
`storm-runoff-verify-h-resume.log`, `storm-runoff-final-validation.log`, the
cohort world's two post-stop receipts and its assertion-only
`storm-active.nbt` / `storm-receded.nbt` checkpoints. The failed earlier cohorts
remain separate evidence. No generated output is part of the source change.

## Limits and next checks

This is a controlled coarse catchment and detailed flood tray with orderly
saves. It does not certify natural storm meteorology, arbitrary river networks,
open-world spill geometry, ocean surge receipts, pressure/SPH transitions,
legacy migration, integrated-client Save & Quit, multiplayer, visuals or soak
performance. The reactive physical-region option remains default-disabled.
Client/two-client behavior and representative open-world performance remain
separate acceptance work.
