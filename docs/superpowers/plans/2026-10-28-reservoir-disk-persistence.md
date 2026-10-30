# Reservoir disk persistence implementation plan

**Goal:** Verify actual chunk eviction/reload and two dedicated-server restarts of a finite physical reservoir.

**Architecture:** Add an explicitly selected, isolated normal dedicated-server validation profile. A test-only event probe builds one bounded fixture, observes real chunk load/unload events and checks exact persisted cells before simulation. Three fresh JVMs share the disposable world: prepare a filled basin; reload and breach, stopping mid-drain; reload and finish drainage. Expected snapshots are assertion data only and never restore gameplay water.

**Tech stack:** Minecraft 1.21.1, NeoForge 21.1.262, JDK 21, existing Gradle/attachment/ticket APIs.

**Spec:** User's approved real save/restart process and `../../watersystem/reactive-reservoir-columns.md`.

## Constraints and decisions

- Use normal dedicated-server startup so `level.dat` is reused. Vanilla GameTestServer constructs fresh level metadata and is insufficient for this proof.
- Use a unique validated run ID and a new `.codex-build/run-water-persistence-<id>` directory. Bind localhost/ephemeral port, flat terrain, no players, and disable the unrelated starter bunker using its existing common config option.
- Harness-only ephemeral region tickets admit the known fixture and its callback halo. Remove tickets and observe a real unload before reloading; production water admission still never loads unknown chunks.
- Observe deserialized attachment state at ChunkEvent.Load before water ticks. Compare quantities, velocity bits, flags and temperature. Require rebuilt awake work and subsequent automatic physical drainage.
- Record checkpoints during orderly server stop, then let normal Minecraft save/close run. A fresh execution nonce and a post-stop receipt make a missing probe, failed assertion or stale report fail Gradle.
- No new gameplay authority, volume format, scheduler, renderer or client packet. The probe is inactive without the dedicated profile property. Do not copy private user configs or edit ordinary run worlds.
- Main agent implements/reviews; one Gradle/Minecraft process at a time. Preserve existing changes; no commits or remote operations.

## Tasks

- [x] Add failing snapshot/protocol tests for exact cell persistence and rejected missing/corrupt/stale inputs.
- [x] Implement bounded expected snapshots and the three-stage dedicated-server probe/profile.
- [x] Run an out-of-order stage in a separate disposable world and confirm an actual failure receipt/Gradle failure.
- [x] Execute prepare, breach and verify serially in three JVMs. Require real fresh chunk objects, exact load snapshots, a mid-drain checkpoint and automatic resumed physical flow.
- [x] Diagnose/fix any actual persistence failures with a regression; distinguish test/environment failures from production defects.
- [x] Run relevant water/weather tests and packaging, review the diff, and record proof and limits.

## Review focus

Expected snapshots must never write canonical data; a fresh world must not pass a resume stage; stale success receipts must not pass; unload must really occur; loaded water must resume without manual scheduling; stage/world/seed/nonce mismatches must fail; failures must stop the isolated server and remain visible to Gradle. This proves orderly saves, not crash-atomic storage.

## Execution notes

The implementation lives beside canonical volume storage so the probe can
inspect the existing package-scoped work queue without widening gameplay APIs.
Seven protocol tests passed, including rejection of ordinary world directories.
The first launch guard exposed ModDev's task working directory still pointing
at the repository before its task action; explicitly aligning task and run-model
directories fixed setup. The actual out-of-order dedicated-server run then
reported a missing `draining.properties` prerequisite, saved/stopped normally,
wrote FAIL, and was rejected by Gradle. This is expected negative evidence.

The first positive cohort used run ID `reservoir-20261029-a`. The prepare stage also flushes
the chunk, changes only canonical temperature, verifies dirty state without a
block change, and checks that metadata through actual disk reload.

Prepare and breach passed in separate JVMs. Verify matched every saved field
and rebuilt pending work, then exposed an incorrect probe assertion: it required
the open gate to remain air even though its saved canonical water occupied
2,522 units. The cell reader had already confirmed the corresponding water
projection. The assertion now accepts air or a plain water projection and still
rejects an obstructed gate. The mid-drain checkpoint explicitly requires water
in that gate so this case remains covered. No production persistence defect or
water correction was identified. After compilation passed, the complete serial
sequence restarted in fresh cohort `reservoir-20261029-b`; failed cohort A was
retained as evidence and never used to restore water.

Cohort B passed all three wrapper receipt gates: prepare PID 49224, breach PID
55472 and verify PID 53768. Saved game ticks were 238, 259 and 513. The final
upstream/opening/downstream ledger was 4,075 / 1,378 / 10,931 = 16,384 units.
Both actual eviction/reload pairs preserved every canonical field. Seven awake
cells rebuilt seven pending entries on the second restart, and physical flow
resumed without manual scheduling. Prepare's metadata-only dirty check passed.

Final focused root tests passed 599 cases across 133 suites; the overlapping
offline water task passed 218 cases across 42 suites. Both had zero failures,
errors or skips. The same wrapper invocation packaged successfully in 43
seconds. JAR contents, diff/new-file whitespace and conflict checks passed.
The full evidence, reproduction commands, harness regression and remaining
limits are recorded in `../../watersystem/reservoir-disk-persistence.md`.
Documentation now separates this bounded orderly-save result from integrated
client, crash recovery, open-world, multiplayer and performance acceptance.
