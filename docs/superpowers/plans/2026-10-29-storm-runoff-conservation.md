# Storm runoff conservation implementation plan

> Execute inline using the existing workspace and normal wrapper. Main agent owns implementation, review and serial Gradle execution; repository instructions override routine delegation/commit prompts.

**Goal:** Verify measured rain reaches a routed river and finite receiving lake, funds canonical flooding, and recedes without duplicate credits through an active-flood dedicated-server restart.

**Architecture:** Reuse `AtmosphericWaterExchange.ReceiptBook`, `RegionalHydrologyManager`, `RegionalDrainageGraph`, `RegionalWaterProjection`, `TemporaryFloodManager` and the existing dedicated persistence profile. A controlled 128 mm rainfall pulse over one 256 m2 catchment is an explicit finite test input (134,217,728 milli-canonical units), not synthesized weather intensity. All stored water remains in existing owners. A two-stage storm scenario saves active regional stores, atmospheric receipts, canonical attachments and funding claims, then verifies exact native reload before ordinary flow resumes. Expected NBT is assertion data only.

**Tech stack:** JDK 21, Minecraft 1.21.1 / NeoForge 21.1.262, existing SavedData, chunk attachments and Gradle JavaExec profile.

**Spec:** The user's approved storm/river sequence in chat: measured rainfall/runoff, rise/overflow/downstream/recession, exact accounting, restart during active flooding.

## Decisions and scope

- Preserve all existing reservoir changes. Work in the current checkout; no commits, pushes, dependencies, client work or new gameplay authority.
- Use a known disposable loaded fixture, with a closed canonical flood tray and a lower coarse receiving lake. Cached terrain is controlled and the existing drainage graph must choose the downstream node. No production force-loading.
- Disable ambient weather coupling in the isolated scenario and publish only the measured pulse through the existing saved receipt book. This verifies hydrologic transport, not atmospheric storm formation or visual weather.
- Enable normal watershed scheduling and flood expansion/recession. Bootstrap one detailed cell only through the existing funded placement transaction; further placements must come from ordinary expansion. Keep temporary funding flags intact; physical reactive regions continue to exclude these claims.
- Use an increased test-server tick target to observe the normal long recession; never alter world time or replay water from expected snapshots. Record tick target and elapsed simulation separately from performance claims.
- Extend `runWaterPersistenceServer` with an explicit `storm` scenario using prepare/verify. Preserve default reservoir profile behavior and require matching nonce/scenario receipts and exact combined inventory.

## Tasks

- [x] Add focused model regressions for a finite pulse, downstream lake rise, exact projection reserve/return, persisted receipts and final recession; include the last milli-unit floodplain tail.
- [x] Run the focused tests before repairs. Diagnose any observed failure and make the smallest conservative correction, then rerun the hydrology tests.
- [x] Add the inactive storm acceptance probe and scenario gate. Save during active flooding, reject a fresh-world verify, then execute prepare/verify in separate normal server JVMs.
- [x] Require exact saved region/receipt/claim/attachment fields, resumed downstream progress, ordinary additional flood expansion, zero per-region residual, matching projection ownership, protected terrain and complete funded recession.
- [x] Run focused water/weather and offline tests plus packaging. Review diff and new files, document actual evidence and limits, and update the main checkpoint/testing guides.

## Files and interfaces

- `src/test/java/.../water/hydrology/StormRunoffConservationTest.java`: behavior-focused model/receipt regressions using real owners.
- `src/main/java/.../water/hydrology/RegionalHydrologyModel.java`: change only if a reproduced recession/accounting failure requires it.
- `src/main/java/.../water/hydrology/StormRunoffPersistenceProbe.java`: explicitly selected world-backed acceptance scenario, no runtime work during ordinary launches.
- `src/main/java/.../water/volume/ReservoirPersistenceProbe.java`: scenario selection guard only.
- `build.gradle`: scenario validation, system property and receipt gates within the existing dedicated profile.
- `docs/watersystem/storm-runoff-checkpoint.md` and existing index/testing docs: actual results, reproduction and remaining atmospheric/client/performance acceptance.

## Review focus

Check that precipitation clocks cannot replay a pulse; canonical claims are not added to regional inventory twice; returned claims cannot credit twice; small positive storage cannot hold recession forever; unknown frontiers stay closed; expected snapshots never become water sources; restart does not rebuild terrain or manually wake/re-fund water; stale/missing receipts fail Gradle; protected solids remain unchanged; scenario flags do not change ordinary launch defaults.

## Validation commands

Start with `waterSimulationTest --tests '*StormRunoffConservationTest'`, then relevant hydrology tests, always with `'-PcodexBuildDir=.codex-build' --no-parallel`. Compile before server runs. Run the existing persistence task with `-PwaterPersistenceScenario=storm`, a fresh run ID, and prepare/verify serially. Retain the session-only JVM compiler exclusion already documented for that task. Finish with root focused water/weather tests, `waterSimulationTest` and `build`; do not claim a whole-repository JUnit pass.

## Execution ledger

The initial test-source compile caught a static enum-name collision; qualifying
the water-feature enum fixed the fixture. A 64 mm pulse did not reliably retain
a whole funded overbank parcel at the chosen checkpoint, so the measured pulse
is 128 mm and the save waits for actual funded flooding rather than a fixed
sample number. The behavioral red run then had two failures: the full pulse
retained 60 milli-units forever, and the isolated last milli-unit never moved.
The unknown-frontier case passed. `RegionalHydrologyModel` now rounds the
floodplain-to-river return upward by at most one milli-unit, still clamped by
channel room and exact source storage. All 74 hydrology tests across 16 suites
passed, including full-channel rejection and exact combined inventory.

The first server attempt rejected its controlled config before loading a
checkpoint. Inspection confirmed `ModConfigRegistration` uses
`config/wildernessodysseyapi/wildernessodysseyapi-server.toml`, rather than the
ordinary world `defaultconfigs` path. Only the isolated profile's settings path
was corrected. This attempt is setup evidence, not the missing-save negative
proof. The fresh negative run is `storm-missing-20261029-b`.

The corrected negative run failed as intended in PID 20924 with
`Missing prerequisite storm-active.nbt`; its FAIL receipt was rejected by
Gradle. Cohort A then timed out before receiving rain. Read-only inspection of
the normally saved regional NBT showed region clocks near tick 100,000, but
both fixture nodes had spill elevation 140 and routed through the surrounding
terrain. Native admission extends beyond the ticket footprint, so the expected
two-node topology did not describe that catalogue. This was a fixture assumption,
not evidence of a stalled scheduler or lost water.

Ruling: bound this fresh dry test catalogue using the existing minimum cap of
64. Retain 62 actual, disconnected native spawn nodes and admit the two loaded
fixture nodes through the normal manager; refuse any prior inventory, regional
receipt, atmospheric receipt or pending precipitation before doing so. Normal
SavedData remains the sole owner. This is test-only setup, never migration or
resume logic. The graph now has an explicit admission frontier. A new cohort B
starts from prepare; cohort A's stopped world is retained as failure evidence.

Cohort B stopped before rain when terrain callbacks admitted other native
chunks into the two available slots. Its saved catalogue contained 64 dry
regions and no fixture nodes. The harness now admits the already-loaded source
and lake immediately after the dry catalogue is installed, before constructing
terrain. Compilation passed; the fresh positive cohort is `storm-20261029-c`.

Cohort C received the full pulse and completed its bootstrap parcel's recession
with zero sampled residual, but never met the additional-expansion gate. Native
ChunkAccess source confirmed that its height method returns the occupied voxel
(`getFirstAvailable - 1`). A focused GameTest reproduced expansion sampling
solid ground. `TemporaryFloodManager` now targets height plus one; the sole
required test then passed. Its initial template namespace setup was corrected
using the existing shared-template copy pattern before the behavioral red run.
The strengthened pure test funds two parcels and passes. Bootstrap now waits
for at least two available parcels before placing its first cell.

Cohort D crashed before probe startup in Temurin's native C2 compiler while
compiling WorldEdit `BlockState::generateStateMap` (PID 55424). A session-only
ignored init script uses `-XX:TieredStopAtLevel=1` on the dedicated task. E was
interrupted overnight before startup evidence; no process remained on resume.

Cohort F prepare passed at tick 740, PID 41668, with 117,833,728 regional plus
16,384,000 detailed milli-units. Its restart matched regional, atmosphere and
claim data but reported a late chunk-attachment comparison mismatch without
field details. Diagnostics were added; the full fresh cohort G then passed both
stages (PIDs 34636 / 42812), including exact late attachment fields, resumed
flow, full projection return and a 134,217,728-unit combined ledger. Verify
ended at tick 35,660 with no detailed water, 28,672,000 milli-units returned and
zero residual.

Ruling: observe canonical persistence at `ChunkDataEvent.Load` as well, before
main-thread activation can mutate awake cells. This supported async event
exposes native disk NBT after normal attachment decoding. Copy both disk NBT
and the decoded worker-owned attachment; publish observations through volatile
fields and assert only on the server thread. Keep normal ChunkEvent.Load for
activation readiness, and keep live exact conservation/ownership audits.
No expected data is restored into water. Compilation passed; cohort H now
executes both stages with those stronger pre-activation boundary checks. The
older F mismatch cannot be reconstructed from its uncaptured observation.

Cohort H prepare passed in PID 44148 at tick 700: 121,929,728 regional plus
12,288,000 detailed milli-units. The original verify invocation ended before
server startup/receipt evidence; read-only process inventory found no remaining
test JVM. The unchanged saved world was verified in PID 8748. Both native disk
and decoded 48-cell snapshots exactly matched before activation. Ordinary
downstream progress and full funded recession passed at tick 35,620, with
134,217,728 regional milli-units, zero detailed cells/claims and 28,672,000
milli-units returned. All sampled residuals were zero. Matching post-stop PASS
receipts were accepted by Gradle (prepare 1m21s, verify 1m49s).

Final `:test` scoped to water/weather, `waterSimulationTest` and `build` passed
together in 38s without the native-server diagnostic init script. Fresh XML
reports show 603 root tests / 134 suites and 222 offline tests / 43 suites, all
passing with no skips. The suites overlap. Source/diff and whitespace review
passed. Updated the checkpoint, overview, testing and watershed guides; client,
natural-storm/open-world and performance acceptance remain explicitly separate.
