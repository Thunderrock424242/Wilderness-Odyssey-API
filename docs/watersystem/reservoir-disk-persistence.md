# Reservoir disk persistence checkpoint

Date: 2026-10-29. This follows the bounded, opt-in
[reservoir-column implementation](reactive-reservoir-columns.md).

## Acceptance contract

`runWaterPersistenceServer` uses normal dedicated-server startup and the same
disposable world across three fresh JVMs. GameTestServer creates fresh level
metadata on startup, so an attachment round trip or repeated GameTest run is
insufficient for this particular proof.

The fixture contains one finite 16,384-unit inlet, a closed upper basin, a
one-block breach and a lower receiving channel. It occupies eighteen measured
cells at `(1000008, 128, 1000008)` in the Overworld. The surrounding cage prevents
unmeasured escape. Only `prepare` funds water; `breach` removes the gate; `verify`
does not set water, schedule work or wake cells manually.

For each cell, observations compare exact units, all three velocity float bit
patterns, flags and temperature. Chunk-load observations read the existing
attachment before simulation. Awake saved cells must reconstruct pending work.
Expected checkpoint files are assertion data only and never restore water.

The three stages are:

1. **Prepare:** fill and settle the closed basin through ordinary ticks. Flush
   normal chunk IO, change only canonical temperature, and require the owning
   chunk to become dirty without changing its block state. Remove the fixture's
   ephemeral ticket, observe actual eviction and cache retirement, flush disk IO,
   reload into a different `LevelChunk` object, and compare every saved field.
   Stop normally with checkpoint `filled`.
2. **Breach:** load that world in a fresh JVM and verify the saved world metadata
   and exact attachment fields. Open the gate, require physical flow into the
   lower channel, and stop with awake work while most upstream water remains.
   The checkpoint is `draining`.
3. **Verify:** load `draining` in another JVM and compare exact fields again.
   Require automatic physical flow to move at least another 1,024 units from
   upstream to downstream. After twenty stable quantity samples, repeat actual
   eviction/disk reload and save checkpoint `drained`.

`ReservoirPersistenceProbe` is inactive unless the explicit profile property is
present. It validates its run ID, execution nonce and disposable directory
before editing the fixture. Harness-only tickets admit a known fixture and its
callback halo; they are removed during eviction and never create a persistent
`/forceload` entry. Production water admission remains loaded-only.

Each stage writes its checkpoint during orderly stopping and a receipt after
normal server shutdown. Gradle requires a PASS receipt matching the current
stage, run and fresh execution nonce, with exactly 16,384 units. Prepare and
verify also require real unload events; prepare requires the canonical-only
dirty check, and verify requires automatic resumed physical flow. Server-ready
messages or a stale receipt cannot pass acceptance.

## Reproduction

Use JDK 21 and the checked-in wrapper. Choose a new lowercase alphanumeric or
hyphenated run ID, up to 64 characters. Run these commands serially:

```powershell
.\gradlew.bat runWaterPersistenceServer '-PcodexBuildDir=.codex-build' -PwaterPersistenceRun=reservoir-example-1 -PwaterPersistenceStage=prepare --no-parallel
.\gradlew.bat runWaterPersistenceServer '-PcodexBuildDir=.codex-build' -PwaterPersistenceRun=reservoir-example-1 -PwaterPersistenceStage=breach --no-parallel
.\gradlew.bat runWaterPersistenceServer '-PcodexBuildDir=.codex-build' -PwaterPersistenceRun=reservoir-example-1 -PwaterPersistenceStage=verify --no-parallel
```

The profile uses `.codex-build/run-water-persistence-<runId>/world`, binds only
`127.0.0.1` with an ephemeral port, and allows no players in the probe. It writes
its own disposable EULA, server properties and minimal common config. Terrain
is flat, the seed is `448188211`, natural structures and the unrelated starter
bunker are disabled, random ticks are zero, and weather is clear. It does not
copy user configs or use ordinary run worlds. Prepare rejects an existing
`filled` checkpoint; use a new ID for a new cohort.

For the negative check, run `verify` with a different fresh ID. It must report
a missing `draining.properties`, stop normally, write FAIL and fail Gradle.

## Execution evidence

Positive run ID: `reservoir-20261029-b`. Stage receipts and assertion snapshots
are local output under its `water-persistence/` directory. Logs are
`.codex-build/reservoir-persistence-{prepare,breach,verify}-b.log`.

| Stage | JVM PID | Saved game tick | Upstream | Opening | Downstream | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Prepare / filled | 49224 | 238 | 16,384 | 0 | 0 | 16,384 |
| Breach / draining | 55472 | 259 | 13,293 | 2,522 | 569 | 16,384 |
| Verify / drained | 53768 | 513 | 4,075 | 1,378 | 10,931 | 16,384 |

Prepare observed two loads and one eviction/reload before shutdown; its final
receipt counted a second unload during shutdown. The first restart loaded six
awake cells and rebuilt six pending entries. The second restart loaded seven
awake cells and rebuilt seven pending entries. Verify observed two loads and
one eviction/reload before shutdown, plus the shutdown unload. Its receipt
reported `automaticResume=true`; prepare reported `metadataOnlyDirty=true`.
All three fresh-cohort stages passed their Gradle receipt gates: prepare in
1 minute 48 seconds, breach in 1 minute 30 seconds and verify in 1 minute
32 seconds. The observed conservation residual remained zero.

An earlier cohort, `reservoir-20261029-a`, passed prepare and breach, then failed
an incorrect probe assertion during verify: it expected air in the opening,
despite exact saved water fields and 2,522 units occupying that gate. The cell
reader had already verified its plain water projection. The corrected assertion
accepts air or a water projection and rejects an obstruction. The mid-drain
checkpoint now explicitly requires a water-occupied gate, retaining this
regression. The complete sequence was restarted in fresh cohort B; no gameplay
persistence repair or snapshot-based water restoration was made.

The actual out-of-order run, `missing-20261029`, used PID `43064` and failed with
`Missing prerequisite checkpoint draining.properties`. Its FAIL receipt was
rejected by Gradle, as required. The seven focused snapshot/protocol tests also
passed before server acceptance, including corrupt/missing/stale inputs and
ordinary-world directory rejection.

These dedicated runs use the same session-only Guava compiler exclusion as the
earlier [dam checkpoint](canonical-dam-checkpoint.md). The ignored local init
script adds the following only to `runWaterPersistenceServer`:

```groovy
gradle.projectsEvaluated {
    gradle.rootProject.tasks.named('runWaterPersistenceServer', JavaExec) {
        jvmArgs '-XX:CompileCommand=exclude,com.google.common.cache.LocalCache$Segment::removeValueFromChain'
    }
}
```

The recorded commands append
`--init-script .codex-build/water-persistence-jvm.init.gradle`. This does not
change production JVM defaults or establish a fix for that native JVM issue.
Existing Tick Engine/Biolith launch workarounds also apply to the new profile.

## Final regression and packaging

After all three stage tasks exited successfully, the following completed in
43 seconds without the diagnostic JVM init script or source exclusions:

```powershell
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.simulation.*' waterSimulationTest build
```

- Root focused water/weather: **599 tests across 133 suites**, zero failures,
  errors or skips.
- Offline `waterSimulationTest`: **218 tests across 42 suites**, zero failures,
  errors or skips. This overlaps the root suite; do not add the counts as
  unique tests.
- Normal packaging succeeded. The built `wildernessodysseyapi-5.0.0.jar`
  contains the persistence probe and bounded snapshot classes. Generated
  worlds, logs, receipts and local JVM scripts remain ignored output.
- Main-agent review checked isolation, one-process execution, load-before-tick
  observations, accepted breach states, real object replacement, normal saves,
  automatic wake reconstruction, passive verify behavior and receipt freshness.
  Diff and new-file whitespace/conflict checks passed. Existing reservoir
  gameplay changes were preserved. No independent review, commit, push or
  remote operation was performed.

The prior opt-in and default GameTest results remain recorded in the
[column report](reactive-reservoir-columns.md). This phase added an inactive
acceptance probe rather than modifying gameplay storage or flow, so those
unchanged GameTest profiles were not repeated. This is focused regression
coverage, not a complete-repository JUnit pass.

## Limits and next checks

This is a bounded fixture with orderly saves and dedicated-server restarts. It
does not establish crash-atomic storage, legacy-world migration, integrated
client Save & Quit, multiplayer presentation, arbitrary geometry, optional-mod
compatibility, or long-duration performance. Stable quantity samples do not
mean all numerical motion has ceased. Reactive physical regions remain
default-disabled, with the existing 32-column/eight-slot limits.

The remaining acceptance work includes funded storm/river propagation, SPH and
pressure transitions, client and two-client behavior, and performance under
representative open-world loads. Keep those results separate from this storage
checkpoint.
