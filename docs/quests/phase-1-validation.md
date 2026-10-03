# Phase 1 quest foundation: implementation and validation

Phase 1 production code is implemented in the approved current checkout. The final test-and-package command was declined before it launched; two test additions and final JAR packaging remain unverified. Authentic multiplayer, real player save restoration and controlled process interruptions also remain acceptance gaps; this phase is implemented but not fully validated. Unrelated rendering/structure-viewer changes remain in place and uncommitted. FTB Quests and legacy lore/Codex data were not converted or removed.

## What exists

Production source lives under `src/main/java/com/thunder/wildernessodysseyapi/quest`. It provides strict schema-v1 immutable candidates, graph/content validation, indexed possession/dimension/lore/trusted-custom-event evaluation, per-player versioned NBT, overworld shared facts, compatible publication checks, ordered durable storage, immutable earned values and item/XP/lore delivery. Clients receive bounded filtered views and revisioned projections; they cannot report completion or choose another player.

Existing lifecycle, config, lore, commands and payload registrars own the integration. Quests use the existing server config's `quests` section; defaults enable this optional feature, editor validation requires level 2, publication level 3 and private receipts level 4. The shared network protocol is now 32. Both client and server must use a compatible build. No additional config file or external model service is needed.

`/reload` creates a candidate. An existing world's publication changes only through accepted publication. Validation captures registries and bound tags after the server data-load tag event; an unavailable active definition suspends evaluation and presentation while retaining its durable hash and player history. A valid initial candidate seeds only a genuinely unused publication store, after owner-thread rechecks.

Player storage uses `wildernessodysseyapi_quests`. Overworld SavedData uses `wildernessodysseyapi_quest_world`. Durable files live inside the server-resolved save root at `wildernessodysseyapi/quests`, with provisioned world/ledger UUIDs and a held exclusive write lease. Ordinary Minecraft autosave is not an atomic inventory/reward transaction. Durable reservation precedes a gameplay effect; interrupted/uncertain reservations become `NEEDS_REVIEW` and never receive an automatic retry. A corrupt journal locks the quest service and preserves evidence. Read-only diagnostics do not recover or reissue a receipt.

I/O uses the shared worker admission API, an ordered queue of 128 content jobs, and one reserved terminal lease-release slot. Shared-worker rejection retains work; explicit shared-worker disablement uses one named fallback worker. Mandatory quest tick work is independent of optional DataEngine allowance. Per-player inventory reconciliation is staggered at 20 ticks with a configurable player budget; pickup/crafting/smelting, login, eligibility, lore and dimension boundaries supply current evidence.

Synchronization admits at most eight active buffers and four 32 KiB chunks globally per tick. Up to 4,096 additional authenticated player IDs can wait without allocating a complete view. Smaller progress updates contain a complete filtered projection; larger ones use paced chunks. A client cache retains its previous complete view on malformed/incomplete input and requests resynchronization on delta revision gaps. There is no native quest journal or Workshop screen in Phase 1.

## Reproducible verification

Use the checked-in wrapper, JDK 21 and sequential processes:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat questTest questCompatibilityTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat runGameTestServer -PquestGameTests '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat build '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

`questCompatibilityTest` reuses the existing offline JUnit runtime to run existing config migration/spec and Codex/lore regressions. `build` provides packaging evidence; these tests are explicitly run separately.

| Evidence | Recorded result |
| --- | --- |
| Production compile | Completed successfully in the final normal and disabled-worker GameTest runs; no production source changed afterward |
| Quest JUnit suite | Phase 1 recorded 75 discovered / 74 passed with one filesystem-link skip. The example-pack and unsupported-store-version/manifest-checksum additions subsequently passed in Phase 2's full quest suite; see phase-2-validation.md for current totals |
| Existing config/Codex suite | 21 discovered and passed, zero failures/errors/skips |
| Default focused GameTests | All five required tests passed, including actual worker-mode and resource-reload assertions; Gradle completed successfully in 1m 7s |
| Disabled shared async/DataEngine GameTests | All five required tests passed; Gradle completed successfully in 32s. Both settings and the named fallback I/O worker were asserted |
| Disposable test configuration restoration | Original common/server files restored; SHA-256 hashes match their backups |
| Task-owned tracked integration whitespace | `git diff --check` completed successfully; Git emitted only normal LF/CRLF notices |
| Installable JAR/package inspection | Final build was declined before launch. No current quest JAR has been certified or offered as an installable artifact |

JUnit tests cover canonical/strict bounded definitions, graph validation, indexed transitions, per-application and session replay rejection, fresh possession, cross-player isolation, explicit repeats, NBT preservation, compatible/blocked migrations, shared-worker rejection/fallback ordering, store identity/lease/shutdown, injected publication boundaries, registry-generation rechecks, original rewards, reservation cancellation/uncertainty/restart/compaction, linear history recovery, nested protocol bounds, hidden text, paced ninth-player admission, permission/config/lore mapping and duplicate request admission.

The link test is skipped because this Windows account cannot create a symbolic link. Traversal/absolute-path rejection is a separate passing test. No live junction/reparse-point proof is claimed. Filesystem tests use temporary directories, not a real save.

GameTests use the supported template-namespace filter in a separate `wildernessodysseyapi_quest_tests` namespace and `.codex-build/run-quest-gametest`. The existing authored empty template is copied and gzip-normalized by `processResources`. Fake players exercise real inventories, item insertion/full-capacity refusal, XP, committed lore and the actual registered death/End-style clone event. They are deliberately not admitted as authentic logged-in players. Dedicated startup restores real store files, registers commands and enforces permission predicates. A normal resource reload proves the tag-update candidate boundary executes. These checks do not establish authentic login/network/save/End-return behavior. [NeoForge GameTest documentation](https://docs.neoforged.net/docs/1.21.1/misc/gametest/) and [the server tag-binding hook](https://github.com/neoforged/NeoForge/blob/1.21.1/patches/net/minecraft/server/ReloadableServerResources.java.patch) describe the relevant integration hooks.

For disabled-worker verification, only the disposable run's generated common/server TOMLs were changed: `asyncThreading.enabled=false` and `performance.dataEngine.enabled=false`. The run used `-PquestGameTests -PquestDisabledWorkers`; the GameTest assertion required both settings to actually load and storage to use `WO-Quest-IO-*`. The original disposable configs were restored and their hashes matched the backups. This was not a product config override or a change to the developer's normal `run/`.

The sandbox helper intermittently failed before launching commands with `helper_sandbox_lock_failed` / `SetNamedSecurityInfoW ... failed: 5`. Authorized commands were retried outside that failing helper. The final escalation request for `questTest questCompatibilityTest build` was declined before process creation; it was not retried. No ACL/security/antivirus/cache-JAR changes or manual compiler pipeline were used. The existing StructureGen prerequisite reports a dependency-catalog fingerprint mismatch and falls back to its vanilla catalog; its three generated structures still verify. This is separate from quest validation.

Remaining automated validation uses one sequential Gradle invocation:

```powershell
.\gradlew.bat questTest questCompatibilityTest build '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Read the completed JUnit XML and Gradle result, then inspect the actual regular JAR under `.codex-build/libs` for quest classes, payload registration and metadata. A `-sources.jar` is source material rather than the installable mod. Any resulting JAR contains the entire current checkout, including its unrelated in-progress changes. Do not infer a quest-only release from this checkout's package.

## Disposable-world manual acceptance

The supplied pack at `docs/quests/examples/phase-1-test-pack` contains two optional quests with supported vanilla targets and XP/compass rewards. Copy that directory to a disposable world's `datapacks` directory. It is not packaged or automatically installed into player worlds. After enabling/reloading the pack:

1. Use `/wo quest status`. An editor uses `/wo quest validate` to inspect the candidate and active hashes. In an established world, a publisher uses `/wo quest publish <candidate_hash> <active_hash>`; use `none` only when no active publication exists. Incompatible objective/eligibility/repeat changes require a future explicit migration tool; Phase 1 refuses them.
2. Carry four logs, then run `/wo quest claim wildernessodysseyapi:phase1_test_supplies wildernessodysseyapi:phase1_test_xp`. Confirm five XP points are granted once, including retry/resync. Quest tracking is `/wo quest track wildernessodysseyapi:phase1_test_supplies`; `/wo quest untrack` clears it.
3. Visit the Nether after the supplies quest and claim `wildernessodysseyapi:phase1_test_compass` for `wildernessodysseyapi:phase1_test_travel`. A full inventory must leave the entitlement claimable after room is made. No quest gates dimension travel.
4. Die, return from the End, log out, stop/restart and verify the same progress, original entitlements and receipt identities. Repeat with shared async and DataEngine disabled. Check the operator's status for the actual I/O worker.
5. With a dedicated server and two real clients, verify independent progress, late join, concurrent completion/claims, reconnect/resync and a compatible publication while one player is offline. Repeat with nine simultaneous initial views to exercise paced admission over the eight-buffer limit.
6. In separate copied worlds with controlled test-only interruptions, stop the process before/after reservation, after the effect and before/after final receipt. Record both actual Minecraft saves and receipt state; uncertain outcomes must stay review-locked. Unit-injected publication exceptions and temporary-directory ledger restarts do not prove process-kill or inventory-save atomicity.

These six real-player/network/interruption scenarios have not been completed in this task. Do not use the temporary fake-player test results as their sign-off. Level-four `/wo quest receipts` inspects up to 32 uncertain outcomes without reissuing/resetting them. One deferred copy issue remains: retrying an existing `NEEDS_REVIEW` receipt returns the generic unavailable response; its synchronized state and receipt diagnostics still report `NEEDS_REVIEW`, and no effect is applied.

## Independent review and implementation decisions

One fresh read-only review found eight Important issues. Regressions reproduced duplicate custom-event consumption, stale possession, lost-manifest reseeding, stale repeat recovery and quadratic reconciliation. Additional tests first lacked the new owner-thread seed, available-view and paced-admission seams, then exercised their behavior. All eight were corrected in one fix pass; nested projection caps were also added after an observed failing regression. No second review or unrelated cleanup was performed.

Decisions, in execution order, are recorded here so they remain reviewable outside ignored bookkeeping:

| Decision | Reason | Cost if wrong |
| --- | --- | --- |
| PowerShell plan bookkeeping | Configured Windows shell; no new runtime dependency | Local bookkeeping requires repair |
| Keep implementation uncommitted | Current-checkout authorization and unrelated edits | User must later commit selected reviewed files |
| 512 recent occurrence window | Trusted producers and bounded replay state | A broken producer can replay an older event; permanent reward identities remain protected |
| Runtime-only possession/dimension samples | Old player autosave cannot prove current evidence | Adapters must refresh samples; possession now requires a fresh sample |
| Every goal change requires a mapping | Lowering a goal can also create a completion | Operators must make an extra deliberate migration decision |
| Separate terminal cleanup slot | Full content queue cannot prevent lease release | Backend shutdown can still delay acknowledgement; diagnostics preserve the held lease |
| Whole-service corruption lock | Missing reservations cannot be safely inferred | Unaffected players wait for administrative review |
| Complete small projections, paced large views | Simpler revision/cache contract | More bandwidth than sparse deltas |
| Suspend unavailable active content conservatively | Avoid false completion while preserving identity | One required missing target can suspend more quests than necessary |
| Dedicated GameTest namespace/run directory | Supported namespace filtering keeps tests focused | Discovery/template packaging may need repair |
| Preserve missing manual acceptance as explicit gaps | Pure/fake tests cannot establish real-world durability/network behavior | Undetected runtime/volume races remain possible |
| Workshop/providers/FTB migration stay later phases | Phase 1 approval only | Native editing and replacement campaign coverage are not available yet |
| Validate after tag binding | NeoForge posts the data-load event after binding tags | An absent event may delay candidate availability |
| Retain ignored ledger/review package | Work is uncommitted and preservation was requested | Local bookkeeping remains until deliberate cleanup |

## Phase 2 handoff

Phase 2 now implements the native Quest Workshop and `/wo quest editor`, reusing the active runtime, permissions, immutable codec, ordered I/O and acceptance boundary. See [Phase 2 evidence and usage](phase-2-validation.md). Publishing retains Phase 1 migration and durable reward protections; native draft publication is a later-phase tool. Live migration acceptance, capture/export/rollback tooling, extra providers, player journal/HUD/story/audio and FTB retirement retain their later-phase gates.
