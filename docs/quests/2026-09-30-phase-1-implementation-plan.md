# Wilderness Odyssey quests: Phase 1 implementation plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task by task after user approval. The main agent owns implementation and all Gradle execution, following the repository's agent-usage rules. Steps use checkboxes for tracking.

**Status:** Phase 1 production implementation is present in the approved current checkout. The most recent completed quest suite has 75 discovered tests, 74 passed, zero failures/errors and one filesystem-link skip; existing config/Codex tests have 21 passed. Five required GameTests passed in both normal and disabled-worker modes. Two later test additions and final JAR packaging remain pending because the final Gradle request was declined before launch. Task 8 real-player/network/interruption acceptance is incomplete. See [the validation report](phase-1-validation.md) for exact evidence.

**Goal:** Deliver a small, testable server-authoritative quest foundation with versioned definitions, preserved progress, limited objective providers, protected basic rewards and bounded client synchronization.

**Architecture:** Immutable campaign snapshots feed a pure indexed objective engine. Feature-owned Minecraft adapters validate events, save player/world state and synchronize filtered views. An ordered durable store protects publication and reward identities; existing Codex, commands, payload registration, configuration and lifecycle own the integration seams.

**Tech stack:** Minecraft Java 1.21.1, the repository's NeoForge version, JDK 21, existing Gson/serialization facilities, NeoForge payloads/events/SavedData and JUnit Jupiter 5. No new external dependency.

**Spec:** [Quest system technical specification](2026-09-29-quest-system-design.md), including the phase 2-6 roadmap and replacement gate.

## Global constraints

- Quests remain optional; ordinary crafting, building, exploration and dimension travel remain independent of quest completion.
- Target Minecraft Java 1.21.1, NeoForge, JDK 21. Preserve current API/mod/network compatibility outside the explicitly added quest contract.
- FTB Quests remains installed; no campaign conversion, FTB save edit, gameplay gate or live progress reset.
- No model service, Ollama, voice synthesis or internet dependency for quests, completion or rewards.
- Server authority for definitions, eligibility, gameplay evidence, editing permissions and rewards; client caches are presentation only.
- New player progress uses `wildernessodysseyapi_quests`; preserve existing Codex/lore/AI/world data roots.
- One Codex-owned Gradle, NeoForm, datagen or Minecraft process at a time; routine output uses `'-PcodexBuildDir=.codex-build'` and `--no-parallel`.
- `build` is packaging evidence in this checkout; run tests explicitly.
- Initial caps: 2,000 quests/campaign, 128 chapters, 32 objectives and 16 rewards/quest, 4,096 description characters, 256 KiB/source JSON, 16 MiB/campaign snapshot and 32 KiB/network chunk.
- Default permissions: player view/track/own claim; editor/test level 2; publish/rollback/export level 3; destructive/recovery administration level 4. Validate on every request and again after I/O.
- Reward identity excludes publication revision; interrupted ambiguous delivery becomes `NEEDS_REVIEW`, with no automatic second grant.
- Preserve unrelated dirty edits, especially the existing rendering/viewer changes and `build.gradle` changes. Review and stage only task-owned files if committing is later requested.

## Review focus

1. A crash after reward effect but before final receipt must never make that identity automatically claimable again: task 5 receipt fault tests and task 8 controlled interruption run.
2. An offline/dead/End-returning player must keep old completion and earned reward values across a changed definition: task 3 migration/clone tests and task 8 restart scenarios.
3. Saturated/disabled shared workers and shutdown callbacks must not move I/O to the tick thread or into a new server: task 4 dispatcher tests, task 5 stale-claim tests and task 8 disabled-feature run.
4. Missing registry targets, unsupported providers and corrupt stored snapshots must preserve the last valid state and never unlock objectives: task 1 validation tests and task 4 activation tests.
5. Double-hand/replayed events, stale client revisions and two clients claiming concurrently must not repeat progress/rewards/toasts: task 2 dispatch tests, task 6 protocol tests and task 8 multiplayer scenarios.

## File and interface conventions

Below, `J` means `src/main/java/com/thunder/wildernessodysseyapi`, `T` means the corresponding root under `src/test/java`, and `F` means `src/test/resources/quests`. All new production files are feature-owned under `J/quest`, apart from narrow edits to existing integration owners. A path containing a listed filename is a concrete planned file, not an instruction to generate every future-phase package.

Use immutable records and defensive copies for model inputs. All namespaced model IDs use `ResourceLocation`; local runtime UUIDs never substitute for portable content IDs. Define supporting records in the named owner file when they are private or small, and separate public API-facing types where required by Java. Public APIs get Javadocs describing side/thread and lifecycle ownership.

## Task 1: Definitions, bounded loading and validation

**Create:**

- `J/quest/definition/QuestDefinition.java` — ID/version, chapter, presentation, eligibility, objectives, repeat policy and rewards.
- `J/quest/definition/ObjectiveDefinition.java` — typed phase 1 objective parameter records: possession, dimension, lore and registered custom event; ID and objective dependencies.
- `J/quest/definition/RewardDefinition.java` — typed item, XP and lore reward parameters and stable ID.
- `J/quest/definition/QuestCampaignSnapshot.java` — immutable chapters/quests, canonical hash, schema/publication generation and source descriptors.
- `J/quest/definition/QuestSourceDocument.java` — source kind (`CAMPAIGN`, `CHAPTER`, `QUEST`), namespaced resource ID, bounded parsed content and source-pack provenance. Different kinds can use the same resource ID without a map collision.
- `J/quest/definition/QuestDefinitionCodec.java`, `QuestReloadListener.java` — strict schema parsing and bounded resource preparation.
- `J/quest/validation/QuestContentLookup.java`, `QuestValidator.java`, `QuestValidationReport.java` — minimal server availability lookup and field-addressed findings.
- `T/quest/definition/QuestDefinitionCodecTest.java`, `T/quest/validation/QuestValidatorTest.java`.
- `F/valid_campaign/`, `F/invalid_campaign/` — small JSON examples for all four phase 1 objective kinds; fixtures contain no reward commands or world-specific coordinates.

**Modify:** `build.gradle` only to add `questTest` using the already existing `structureGenOfflineRuntime`, `compileTestJava`, `testClassesDirs` and JUnit setup. Filter `com.thunder.wildernessodysseyapi.quest.*`. A focused task still compiles the shared test source set; it cannot conceal unrelated test compilation failures.

**Interfaces produced:**

- `QuestDefinitionCodec.decode(List<QuestSourceDocument> resources, QuestContentLookup lookup): QuestValidationReport` — report contains an optional immutable candidate and all validation findings; records are defensive copies of parsed input.
- `QuestValidator.validate(QuestCampaignSnapshot candidate, QuestContentLookup lookup): QuestValidationReport`.
- `QuestValidationReport.accepted(): boolean`, `snapshot(): Optional<QuestCampaignSnapshot>`, `findings(): List<Finding>`; `Finding` includes severity, content ID, field path and player-safe message.
- `QuestContentLookup` exposes `contains(ContentKind kind, ResourceLocation id)`, `tagMembers(ResourceLocation tag)`, `supportsEvent(ResourceLocation producer)`, and `generation(): long`. Phase 1 supplies only validation lookup, not the future searchable catalog.

- [x] Write behavior tests for valid decode/round trip; missing and duplicate IDs; unknown schema/provider/reward; invalid and overflowing goals; quest/objective cycles; dangling references; empty tags; missing optional vs required mods; deterministic hash despite map order.
- [x] Add boundary tests that reject a 256 KiB + 1 byte source before Gson parsing and a 16 MiB + 1 byte assembled snapshot before activation, including deeply nested input. Bound the resource reader in reload preparation, not only the parsed model.
- [x] Add the focused Gradle task without altering unrelated build configuration; run `questTest --tests '*QuestDefinitionCodecTest'` and confirm the new missing model fails for the expected reason. Environment/bootstrap failures do not count as a red test.
- [x] Implement immutable records, codecs, typed validation, canonical serialization and the read-only candidate reload listener. Resource preparation may use Minecraft's reload workers; live registry snapshots are captured at the proper lifecycle boundary.
- [x] Run `compileJava` and the two targeted tests. Expected evidence: successful compile and tests with zero failed cases.

**Deliverable:** a validated, immutable candidate that cannot alter gameplay. Document schema v1 with one real example generated from a passing fixture. No production story content yet.

## Task 2: Pure progression engine and event replay protection

**Create:**

- `J/quest/objective/QuestEvent.java`, `ItemObservation.java`, `QuestObjectiveHandler.java` — immutable verified event DTOs, item/tag observations and typed provider behavior.
- `J/quest/runtime/QuestObjectiveEngine.java`, `QuestTransitionBatch.java` — compiled target index, eligibility and transition evaluation.
- `J/quest/progress/QuestPlayerProgress.java` — immutable per-player quest/run/count/completion/tracking model, including original earned entitlement values.
- `J/quest/world/QuestWorldState.java` — immutable shared facts and world identity.
- `T/quest/runtime/QuestObjectiveEngineTest.java`.

**Interfaces produced:**

- `QuestObjectiveEngine.compile(QuestCampaignSnapshot snapshot): QuestObjectiveEngine`.
- `QuestObjectiveEngine.apply(QuestPlayerProgress player, QuestWorldState world, QuestEvent event): QuestTransitionBatch`.
- `QuestTransitionBatch` contains replacement individual/shared state plus objective/quest completion transitions and earned reward descriptors. It has no `ServerPlayer`, world mutations, filesystem operations or client classes.
- `QuestEvent` includes typed evidence, trusted producer ID, server session UUID and stable occurrence ID. Possession supplies a complete immutable inventory observation, dimension supplies the confirmed dimension key, and lore supplies the mapped legacy unlock ID.

- [x] Test that prerequisites control quest eligibility without affecting gameplay mechanics; all/any prerequisites, unordered objectives and explicit sequential dependencies produce the expected state.
- [x] Test possession across overlapping item IDs/tags without double count; a completed possession objective remains completed after the item is spent. Current possession is seeded when a quest becomes eligible.
- [x] Test replaying an occurrence changes counts once, distinct occurrences in the same tick count separately, different players remain independent, and counter arithmetic saturates at its goal.
- [x] Test repeat run identity changes only through explicit reset/cooldown policy; publication version/text changes cannot start another run. Shared facts never choose an arbitrary credited player.
- [x] Run the failing engine tests, implement the indexed evaluator and a bounded occurrence cache, then run the tests again. No all-campaign tick scan and no discarded objective events through optional DataEngine queues.

**Deliverable:** deterministic transitions tested without a loaded Minecraft world.

## Task 3: Persistence and compatible progress migration

**Create:**

- `J/quest/progress/QuestProgressCodec.java`, `QuestPlayerProgressStore.java`, `QuestProgressMigration.java`.
- `J/quest/world/QuestWorldSavedData.java`.
- `T/quest/progress/QuestProgressCodecTest.java`, `QuestProgressMigrationTest.java`.

**Interfaces produced:**

- `QuestProgressCodec.encode(QuestPlayerProgress progress): CompoundTag` and `decode(CompoundTag tag): DecodeResult`.
- `QuestPlayerProgressStore.load(ServerPlayer player): QuestPlayerProgress`, `save(ServerPlayer player, QuestPlayerProgress progress): void`, `copy(Player original, Player replacement): void` — server-only, new NBT root only.
- `QuestProgressMigration.check(QuestCampaignSnapshot previous, QuestCampaignSnapshot candidate): MigrationReport`.
- `QuestProgressMigration.apply(QuestPlayerProgress progress, MigrationPlan plan): MigrationResult` — immutable input/output; no inventory/reward effects.
- `QuestWorldSavedData.get(MinecraftServer server): QuestWorldSavedData` — overworld-owned 1.21.1 `SavedData.Factory`; new data file name `wildernessodysseyapi_quest_world`.

- [x] Test round trips for counts, completion, tracking, repeat runs, archived entries and immutable earned reward values. Unknown future schemas are preserved/reported and never overwritten with an empty model.
- [x] Test text/layout changes preserve state; removed quests/objectives retain archives; changed meanings/types and raised targets require explicit mapping; completed runs and claim identity remain protected.
- [x] Test the pure clone-copy operation makes a deep copy, preserves unrelated NBT and does not add counts twice on End return. Actual entity replacement is exercised in task 8.
- [x] Implement the NBT adapters and dirty-marked shared state using existing Minecraft persistence patterns. Do not alter `ModAttachments`, lore storage or the global world upgrade version for this model.
- [x] Run compile and targeted persistence/migration tests. Document that normal Minecraft autosave is not an atomic inventory/reward transaction.

**Deliverable:** versioned preserved state and a non-destructive compatibility report, including offline progress migration by stored version.

## Task 4: Durable I/O lane and atomic publication foundation

**Create:**

- `J/quest/workshop/QuestIoDispatcher.java`, `QuestPublicationStore.java`, `QuestPublicationService.java` — ordered immutable writes, world-contained storage and manifest activation.
- `J/quest/workshop/QuestStoreMetadata.java` — durable provisioned-world UUID, store format version and ledger presence/identity contract, established before any gameplay reward effect.
- `J/quest/runtime/QuestRuntime.java` — server session, active/candidate snapshot and compiled engine; no editor UI.
- `T/quest/workshop/QuestIoDispatcherTest.java`, `QuestPublicationStoreTest.java`, `QuestPublicationServiceTest.java`.

**Interfaces produced:**

- `QuestIoDispatcher.trySubmit(Runnable immutableIoWork): boolean`; `close(): void`. Use shared bounded I/O workers; when explicitly disabled, use one named lifecycle-owned fallback worker with a queue capped at 128. Shared saturation retains pending work and never starts an extra fallback or uses caller-runs.
- `QuestPublicationStore.prepare(QuestCampaignSnapshot snapshot): CompletionStage<StoredSnapshot>` and `activate(StoredSnapshot stored, CommitToken token): CompletionStage<PublicationCommit>`. `CommitToken` is issued on the server thread after preparation and contains the accepted base hash, target hash, world/server identity and serialized commit revision; clients cannot construct it.
- `QuestPublicationService.publish(ServerPlayer operator, String candidateHash, String expectedBaseHash): CompletionStage<PublishResult>` — permission, generation and compatibility checked before preparation and again before commit-token issuance. An accepted durable commit still restores on restart if its initiating operator disconnects.
- `QuestRuntime.acceptCandidate(QuestValidationReport report): void`, `activeSnapshot(): Optional<QuestCampaignSnapshot>`, `activate(PublicationCommit commit): void`, `onEvent(ServerPlayer player, QuestEvent event): void`, `close(): void`; game-thread ownership.

- [x] Test normalized containment, traversal/absolute-path rejection, immutable snapshots, corrupt snapshot checksums and temp-file recovery in temporary directories. No real save is used.
- [ ] Execute the newly added unsupported-store-version/manifest-checksum preservation test. The symbolic-directory test was skipped because this account cannot create a link; live junction/reparse-point acceptance remains separate.
- [x] Test durable world-UUID provisioning before first claim and reconciliation with an older SavedData snapshot; identity mismatch or a missing ledger in a provisioned store prevents claims. Hold an exclusive quest-store write lease until outstanding I/O completes; an old worker cannot write after a new server obtains ownership.
- [x] Inject failures before snapshot write, after snapshot write, before manifest replacement and after manifest replacement. Assert restart chooses the durable complete publication and never a partial graph or blank fallback.
- [x] Test changed draft/base/registry generation rejects publication, failed validation retains old active hash, and `/reload` only changes the candidate in an existing world. Demotion/disconnect before token acceptance aborts; after acceptance it prevents new requests without corrupting or undoing the committed manifest.
- [x] Test shared-worker rejection and disabled-mode fallback bounds, write ordering, no game-thread disk I/O, shutdown cancellation and callbacks belonging to an old server generation.
- [x] Implement storage under the server-resolved world root, immutable hashed snapshots, atomic active manifest replacement and retained old snapshots. An initial valid datapack candidate seeds publication only when there is no existing manifest/state.
- [x] Run compile and targeted store/publication/dispatcher tests. Leave full drafts, autosave, multi-operator editing UI, history UI, export and rollback UI to phases 2/4.

**Deliverable:** explicit validated activation with non-destructive migrations and durable restart behavior, available through the diagnostic command added in task 7.

## Task 5: Reward entitlements, durable claims and safe basic delivery

**Create:**

- `J/quest/reward/QuestRewardKey.java`, `QuestRewardLedger.java`, `QuestRewardService.java`, `QuestRewardDelivery.java`.
- `T/quest/reward/QuestRewardLedgerTest.java`, `QuestRewardServiceTest.java`.

**Interfaces produced:**

- `QuestRewardKey(UUID worldId, UUID playerId, ResourceLocation questId, long runNumber, ResourceLocation rewardId)`.
- `QuestRewardLedger.recordEarned(QuestRewardKey key, RewardDefinition originalValue, String definitionHash): CompletionStage<EarnResult>` — duplicate identical earned writes are idempotent; changed value under an earned identity is rejected.
- `QuestRewardLedger.reserve(QuestRewardKey key, UUID requestId): CompletionStage<ReservationResult>` — completion is successful only after durable acknowledgement.
- `QuestRewardLedger.recordDelivery(UUID reservationId, DeliveryOutcome result): CompletionStage<Void>`.
- `QuestRewardService.claim(ServerPlayer player, ResourceLocation questId, long runNumber, ResourceLocation rewardId, UUID requestId): ClaimResult`; the requesting player is always derived from the authenticated sender.
- `QuestRewardDelivery` exposes an injectable test sink and server-only item/XP/lore delivery; no command executor.

- [x] Test request retries, simultaneous reservations, restart/replayed journal records, publication/reward edits, deleted quests, repeat runs and full inventories. Assert one automatic effect per reward identity.
- [x] Test earned value remains the original value after a publication and can be recovered from a durable entitlement even if the latest player autosave is older; reconcile completion for that earned run rather than reopening its reward.
- [x] Test truncated tail/corrupt records lock affected claims; recovery never starts an empty ledger. Compaction preserves terminal identity protection.
- [x] Inject interruption after durable reservation and after the delivery effect. Assert unknown outcomes become `NEEDS_REVIEW`, not `EARNED` or automatically `DELIVERED`.
- [x] Test offline/dead/demoted/stale-session callbacks re-resolve and validate state; no grant happens through an old `ServerPlayer` reference. Saturation retains earned descriptors for retry and reports pending status.
- [x] Implement ordered checksummed transaction records with durable flush, typed delivery, immutable original values, and a server-only diagnostic for uncertain outcomes. Before-effect cancellation requires durable proof before returning an identity to claimable state.
- [x] Run compile and the reward tests. Live inventory/XP effects and controlled process interruptions remain task 8 gates. Phase 1 administrative diagnostics can inspect uncertain outcomes; automatic or destructive recovery is not exposed.

**Deliverable:** real basic rewards with at-most-once automatic issuance and visible ambiguous-outcome handling. Submission/consumption transactions are phase 3 and reuse this contract.

## Task 6: Bounded player synchronization

**Create:**

- `J/quest/network/QuestPayloads.java`, `QuestViewPayload.java`, `QuestProgressDeltaPayload.java`, `QuestPlayerRequestPayload.java`, `QuestRequestResultPayload.java`.
- `J/quest/network/QuestViewService.java` — authorized view projection, chunking and dirty synchronization.
- `J/quest/client/QuestClientState.java` — read-only revisioned cache and logout/reset handling; no journal/editor screen in this phase.
- `T/quest/network/QuestProtocolTest.java`, `T/quest/client/QuestClientStateTest.java`.

**Modify:** `J/network/ModPayloads.java` to delegate to `QuestPayloads.register(registrar)` and bump shared `NETWORK_VERSION` once when introducing the quest wire contract. Resolve the current version at implementation time; do not overwrite another task's version change.

**Interfaces produced:**

- `QuestPayloads.register(PayloadRegistrar registrar): void`.
- `QuestViewService.sendInitial(ServerPlayer player): void`, `sendDirty(ServerPlayer player): void`, `clear(ServerPlayer player): void`.
- Player request operations in this phase are `VIEW`, `TRACK`, `UNTRACK`, `CLAIM` and bounded `RESYNC`; no serverbound objective-event operation. Every request carries request ID and expected publication/player revision where relevant.
- `QuestClientState.accept(QuestViewPayload payload): void`, `accept(QuestProgressDeltaPayload payload): ApplyResult`, `clear(): void`.

- [x] Test codec text/list/count caps, 32 KiB chunks, bounded advertised totals, duplicate request IDs, out-of-order/replayed deltas and session/publication changes.
- [x] Test player view excludes drafts, hidden text, unpublished bindings and private metadata; tracking cannot select an undisclosed/unavailable quest.
- [x] Test stale deltas request a complete view; disconnect destroys previous server state; replayed transitions do not trigger duplicate presentation notifications.
- [x] Implement DTOs, safe decode, authenticated request handling, filtered projections and revision-aware caches. Restrict client-only handlers to the existing client registration pattern so dedicated-server classloading is tested rather than assumed.
- [x] Run compile and protocol/client cache tests. Continue using direct feature payloads for correctness when DataEngine is disabled; optional display coalescing is an optimization only.

**Deliverable:** safe player state synchronization with no quest UI or client authority over completion.

## Task 7: Minecraft providers, lifecycle, permissions and diagnostics

**Create:**

- `J/quest/runtime/QuestServerEvents.java`, `QuestObjectiveEvents.java` — startup/reload/login/logout/clone/dimension and bounded interested-player inventory reconciliation.
- `J/quest/command/QuestCommand.java`, `QuestPermissions.java`, `QuestOperation.java` — typed operation/permission policy shared by commands and requests.
- `J/quest/config/QuestConfig.java` — feature-owned server permissions and admission/check budgets composed into the existing spec.
- `J/quest/integration/LoreQuestBridge.java`, `QuestCustomEventRegistry.java` — legacy lore mapping and trusted server producer registration.
- `T/quest/command/QuestPermissionsTest.java`, `T/quest/integration/LoreQuestBridgeTest.java`.

**Modify narrowly:**

- `J/core/WildernessOdysseyAPIMainModClass.java` — register feature event owner once.
- `J/server/ServerLifecycleEvents.java` — add quest reload listener and explicitly order quest start/stop around shared workers as required by the implementation. Avoid duplicate annotation/manual registration.
- `J/command/ModCommands.java` — register `/wo quest` as a sibling to `/wo sequence`.
- `J/config/WildernessConfigSpecs.java`, `ModConfigRegistration.java` — compose/reload feature settings; no fourth config file.
- `J/lorebook/LoreBookManager.java` — notify the bridge only after a new lore discovery is committed. Preserve existing collection behavior and serialized legacy ID.

**Interfaces produced:**

- `QuestCommand.register(CommandDispatcher<CommandSourceStack> dispatcher): void`.
- `QuestPermissions.mayPerform(ServerPlayer player, QuestOperation operation): boolean`, with all levels and hierarchy specified in the design.
- `LoreQuestBridge.onCollected(ServerPlayer player, String legacyLoreId): void`; map legacy `lore_001` to `wildernessodysseyapi:lore_001` while preserving the stored old ID.
- `QuestCustomEventRegistry.register(ResourceLocation producer, EventSchema schema): void`, `emit(ServerPlayer actor, VerifiedCustomEvent event): void`; only registered trusted server code can emit, never arbitrary chat or player payloads.

- [x] Test permission boundaries and config hierarchy, arbitrary player UUID rejection, lore mapping/idempotence and cancellation of stale producer sessions.
- [x] Implement level-0 `/wo quest status`, `/wo quest track <id>`, `/wo quest untrack` and `/wo quest claim <quest> <reward>` using authenticated player context.
- [x] Implement operator `/wo quest validate`, `/wo quest publish <candidate_hash> <base_hash>` and level-4 read-only `/wo quest receipts` diagnostics. Report expected/current hash and validation field paths. No progress reset, arbitrary reward grant or pretend editor command.
- [x] Wire interested-player possession checks at login, eligibility and relevant events with a staggered 20-tick fallback. Wire dimension transitions/presence checks, already collected lore seeding and new lore notifications.
- [x] Wire startup candidate loading, durable manifest/receipt restoration, logout cache cleanup and stop order. Async callbacks check the quest server identity; close the ordered I/O lane before shared workers stop. Bounded failure leaves diagnostics and preserves files.
- [x] Compile the integrated production source and run the completed 75-case quest suite plus 21 existing config/Codex tests through the repository's focused runtime. Minecraft launches followed compile/logic verification.
- [ ] Run the two later test additions and final package build. The final Gradle request was declined before launch.

**Deliverable:** usable foundation through diagnostics and a small test campaign; `/wo quest editor` becomes available only with the actual screen in phase 2.

## Task 8: Runtime evidence and phase handoff

**Create:** `J/quest/testing/QuestFoundationGameTests.java` for relevant loaded-world contracts; `docs/quests/phase-1-validation.md` recording commands, outputs, manual scenarios, artifacts and remaining gaps. Use the project's existing GameTest discovery/template conventions.

- [x] Complete isolated production compilation and the 75-case quest suite with zero failures/errors and one filesystem-link skip. Read the completed outputs and XML.
- [ ] Run the two test additions made after the last completed JUnit invocation; they have no execution evidence yet.
- [x] Run affected Codex/lore/config regressions with the existing focused/offline setup and explicit filters; broaden to `test` only when useful. `questTest` includes quest tests, not all existing subsystem regressions.
- [ ] Run `build '-PcodexBuildDir=.codex-build' --no-parallel --console=plain` for packaging. Inspect the exact produced JAR for expected assets/metadata and exclusion of test fixtures. Record packaging separately from test results.
- [x] Run the smallest GameTest server environment for actual possession, lore, dimension, clone and inventory-grant contracts. Verify event registration and dedicated-server startup without client class requirements.
- [ ] In a disposable/copied world, complete the test campaign, die, return from the End, log out, stop/restart and confirm progress and claims persist. Repeat with shared async/DataEngine settings disabled and record which work path runs.
- [ ] In dedicated-server/two-client testing, verify individual progress, late join, simultaneous completion/claim, reconnect and definition publish while another player is offline. Full inventory retains entitlement; no duplicate grant occurs on resync/retry.
- [ ] With controlled test-only fault injection and separate copied worlds, interrupt before/after reservation and before/after final receipt; demonstrate restart/reconciliation behavior. A unit mock alone does not prove Minecraft save durability.
- [x] Review task-owned diffs for generated output, secrets, unrelated edits and temporary fault hooks. Preserve the unrelated dirty worktree. Fault hooks must not be enabled in production.
- [x] Report completed scope, exact validation evidence, environment blocks and unrun manual checks. A phase with missing runtime/fault evidence is implemented but not fully validated. Present the focused phase 2 Workshop plan as the next reviewable increment.

## Validation command template

Use the checked-in wrapper and configured JDK 21. For example, after the new task exists:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat questTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat build '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Run these sequentially only as required by each task; do not repeat unchanged successful checks. `questTest` is proposed and does not exist yet. Build failures before Java compilation, including generated-JAR access denied, are environment evidence and use the repository's bounded recovery workflow. Never modify ACL/security settings, generated dependency JARs or construct an alternate `javac` pipeline.

## Next phases

The design's phase table defines acceptance for the complete system. After phase 1, phase 2 adds the actual native Workshop and revisioned drafts. Phase 3 adds the live content browser and additional validated providers/adapters. Phase 4 completes scoped capture, isolated tests and safe publishing/export/rollback tools. Phase 5 completes the Codex journal, authored story, original sound assets and optional HUD. Phase 6 establishes actual pack coverage, save migration and replacement readiness before FTB retirement is considered.

## Approval and execution

Review the specification and this plan together. Proposed execution is native/main-agent implementation in this task, consistent with the repository guidelines. Approval starts phase 1 in small increments; it does not authorize all future phases or removal of FTB Quests. No product code, dependencies, world edits, builds, migrations or Minecraft launches have occurred as part of this planning pass.
