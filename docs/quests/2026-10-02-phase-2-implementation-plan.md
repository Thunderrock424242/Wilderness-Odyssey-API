# Phase 2 Quest Workshop implementation plan

> **For agentic workers:** Use `superpowers:executing-plans` inline. The main agent owns all implementation and Gradle execution; one fresh final review checks the whole increment. Checkboxes record completed evidence.

**Goal:** Deliver the native, revisioned and durably autosaved authoring foundation described by the approved Phase 2 roadmap.

**Architecture:** Pure immutable draft patches and client history feed a server-authorized Workshop service. Existing leased storage and ordered I/O own persistence; bounded payloads connect a client-only native screen to the current quest server session. Editing never changes live quest state.

**Tech stack:** Minecraft 1.21.1, existing NeoForge/JDK 21/Gson/JUnit 5; no new dependencies.

**Spec:** [Phase 2 design](2026-10-02-phase-2-workshop-design.md), extending [the quest specification](2026-09-29-quest-system-design.md).

## Global constraints

- Preserve existing progress, original rewards, lore/Codex/FTB files and unrelated dirty edits.
- Reuse the current store lease, ordered I/O, server session, permissions and payload registrar.
- One Codex Gradle/NeoForm/Minecraft process at a time; wrapper with `'-PcodexBuildDir=.codex-build' --no-parallel`.
- Drafts: 128 chapters, 2,000 quests, 256 KiB/source, 16 MiB aggregate; 16 KiB chunks, eight editor sessions, four outbound chunks/tick.
- No publication/history/export/capture/test-profile GUI, additional providers, gameplay gates, save resets or FTB removal.
- Work remains uncommitted in the user's current checkout.

## Review focus

1. Two operators and delayed I/O cannot silently overwrite a newer acknowledged revision.
2. Demotion, player replacement, disconnect and a new server cannot accept old-session edits or reveal draft bodies.
3. Failed/corrupt/unsupported draft storage preserves the previous file and does not disable ordinary quest rewards.
4. Resize, close while saving, undo/redo and stale notifications retain local work without silently discarding it.
5. Large/invalid drafts and fragmented packets remain bounded and never mutate live campaign/progress state.

## Task 1: Pure draft edits and authoring operations

Create `quest/workshop/QuestWorkshopDraft.java`, `QuestWorkshopEdit.java`, `QuestWorkshopDocuments.java`; tests mirror that package.

Interfaces: immutable draft `(UUID id, long revision, List<QuestSourceDocument> sources, Map<ResourceLocation, Position> positions)`; `apply(QuestWorkshopEdit)`, `withRevision(long)`, `encode()/decode(byte[])`; document operations return a patch rather than modifying their input.

- [x] Write tests for immutable copies, create/update/delete membership, quest/chapter duplication and internal reference remapping, deleted prerequisites, invalid sizes/IDs/coordinates and strict round trips.
- [x] Run focused tests; expected RED is missing new types or a behavior assertion, never a bootstrap failure.
- [x] Implement structural draft validation and pure CRUD/patch helpers; incomplete semantic content remains saveable.
- [x] Run focused tests; expected zero failures.

## Task 2: Leased draft persistence and revision service

Modify `QuestPublicationStore` with bounded ordered draft reads/writes. Create `QuestWorkshopStore` and `QuestWorkshopService`; tests use real temporary files and a controlled authority.

Interfaces: `open(store, seedSources)` restores/provisions the draft; `save(expectedRevision, draft)` forces atomic replacement; service `edit(UUID actor, Object incarnation, long expectedRevision, QuestWorkshopEdit)` returns acknowledged/conflict/denied/busy/invalid/failed results through the owner callback.

- [x] Test restart, stable draft/world identity, checksum/unknown-version preservation, two concurrent operators, rejected permissions/incarnations, accepted shutdown saves and failed-write preservation.
- [x] Run RED, implement through the existing I/O/store lease, then run GREEN.
- [x] Preserve the live publication hash and permanent reward ledger throughout every draft save.

## Task 3: Bounded editor protocol and server adapter

Create `quest/network/QuestWorkshopPayload.java`, `QuestWorkshopTransfers.java`, `QuestWorkshopServer.java`; integrate current session, command, permissions and central payload registration.

Interfaces: authorized editor session; strict frame header `(server, editor, request, kind, revision, index, totalBytes, bytes)`; ordered assembler and paced outgoing snapshots; service acknowledgement contains revision, message and optional findings.

- [x] Test invalid/duplicate/out-of-order/oversized/expired frames, transfer limits, stale revisions and cleanup.
- [x] Run RED, implement bounded authenticated requests and `/wo quest editor`, then run GREEN/compile.
- [x] Recheck permissions and player incarnation for private outgoing data; notify other editors of staleness without replacing local edits.

## Task 4: Client document, save queue and undo/redo

Create `quest/client/workshop/QuestWorkshopModel.java` and pure `QuestWorkshopClientState.java`; tests cover history and client protocol.

Interfaces: model keeps acknowledged revision separate from optimistic edits; one in-flight patch, bounded queue, acknowledgement/conflict handling, undo/redo; pure state receives snapshots/status and dispatches to a client-only handler.

- [x] Test undo/redo across re-init, edits during pending saves, busy retries, failed-save retention, explicit conflict reload and disconnect cleanup.
- [x] Run RED, implement bounded history/save queues and strict complete-snapshot decoding, then run GREEN.

## Task 5: Native Workshop and typed inspectors

Create `QuestWorkshopLayout`, `QuestWorkshopScreen`, `QuestWorkshopPropertiesScreen` and client-only event integration. Geometry tests exercise normal/small GUI sizes and node hit testing.

- [x] Write layout/selection/drag coordinate tests and run RED before implementation.
- [x] Implement sidebar CRUD, search, clipped/pannable canvas, node dragging/link removal, inspector/property forms, typed objective/reward editing, validation display, undo/redo/save and visible conflicts.
- [x] Keep draft/selection/viewport/history in the model across resize; use an alternate properties screen when the inspector collapses.
- [x] Compile and run focused tests; do not claim visual proof from geometry tests.

## Task 6: Integration, review and handoff

- [x] Compile and run complete `questTest` plus `questCompatibilityTest`; include the two outstanding Phase 1 test additions.
- [x] Extend focused dedicated GameTests for editor registration/store restore and run only after source/tests compile.
- [x] Obtain one fresh whole-increment review, fix Important/Critical findings with regressions and run the affected suite.
- [x] Build and inspect the actual regular JAR when authorized; review task-owned diffs/resources and preserve unrelated edits.
- [x] Record real two-operator/restart/resize/screenshots separately from automated evidence. Report remaining acceptance gaps and later phases precisely.
