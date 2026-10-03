# Phase 2: native Quest Workshop

The user requested Phase 2 on 2026-10-02 after reviewing the agenda and existing written roadmap. This increment implements the Phase 2 row of the quest specification. The main agent works inline in the existing checkout, preserving unrelated edits. Phase 1's remaining real-player and interruption acceptance is still outstanding.

## Outcome

An authorized operator opens `/wo quest editor` in an ordinary integrated or dedicated-server world. A native screen provides campaign/chapter/quest creation, title editing, deletion and duplication; a pannable quest canvas with node movement and prerequisite links; typed properties for the Phase 1 providers/rewards; undo/redo, explicit save and debounced autosave; current-server validation findings; and visible revision conflicts between operators. Draft editing never activates content or modifies player progress/reward receipts.

The comprehensive catalog, capture, simulation/live-test profiles, publish/history/rollback/export screens, player journal/HUD and FTB retirement remain later phases. Existing publication commands continue to work on datapack candidates. No Workshop publish button is introduced in this increment.

## Ownership and alternatives

Use feature-owned draft/document classes, the existing leased publication store and ordered I/O lane, a server-session-owned Workshop adapter, bounded feature payloads, and client-only native screens. A client-only editor cannot provide shared durable drafts; a separate authoring service would duplicate lifecycle and permissions. Both alternatives are rejected in favor of extending the existing owners.

One world has one shared Workshop draft with a stable UUID and monotonically increasing acknowledged revision. Its definition sources are detached copies. Node positions are draft-only metadata and do not alter schema-v1 publication identity. Missing draft storage initializes from the active publication, otherwise the current valid datapack candidate, otherwise an empty authoring workspace. A damaged/unsupported draft is preserved and disables authoring only; quest progress and rewards retain their existing owners.

## Editing and persistence

Edits contain bounded source replacements/deletions and position changes. They cannot supply a world path, player identity, live publication, progress, reward state or accepted revision. The server applies a patch against the exact acknowledged revision. CRUD operations update reciprocal campaign/chapter membership and remove references to deleted quests; duplication creates new stable IDs and remaps references within a duplicated chapter. Stable IDs remain read-only after creation; advanced ID migration is later work.

Definition JSON may be invalid while authored, so saving a draft does not require publication validation. Structural storage bounds still apply: one campaign, 128 chapters, 2,000 quests, 256 KiB per source, 16 MiB draft/patch, finite node coordinates within 100,000 units, and unique namespaced source identities. Existing bounded parsing rejects duplicate keys and excessive nesting. Validation uses the current captured registry/tag generation and exposes field paths without affecting the live hash.

Draft files use format version 1, world/draft identities, revision and checksum at `drafts/workshop.json`, under the existing write lease. Reads and forced atomic replacement run in the existing ordered I/O lane. A successful reply means the draft is durable. Failed writes retain the previous acknowledged revision and local edits. Only one save is accepted at a time; busy requests remain locally queued. A stale expected revision produces a visible conflict, preserves local work and requires an explicit reload instead of silently overwriting another operator.

Permission, feature enablement, authenticated player incarnation and editor session are checked at admission. Acceptance on the server thread is the write boundary: an already accepted save can finish after disconnect/demotion, while further writes and private responses are denied. Shutdown drains accepted writes before the existing store releases its lease.

## Protocol and lifecycle

Every editor opening receives a random operator session bound to that authenticated player and the current server-session UUID. Payloads use 16 KiB chunks, bounded totals and strict sequencing. At most eight editor sessions hold transfers; global outbound pacing is four chunks per tick. Partial uploads expire, duplicate requests are rejected, and logout/permission loss/stop removes buffers. Source bodies are sent only to authorized editors. Ordinary player projections continue to exclude drafts.

Small acknowledgements include request identity and accepted revision. Other editors receive a stale-revision notification without automatic replacement of unsaved work. A client sends one save at a time and queues bounded later edits. The client model persists across screen resize/re-init, supports up to 64 undo/redo entries under a combined byte budget, and survives closing the screen while a save finishes. Disconnect clears server-specific draft caches.

## Native screen

Use vanilla buttons/text editors and readable paper/leather colors. A chapter sidebar, clipped canvas, inspector and status/actions share one tested geometry model for drawing and hit testing. At small widths, collapse the inspector into a properties screen; do not shrink text controls. Search, tooltips, keyboard selection, Ctrl+Z/Y/S, and readable validation/conflict messages are required. Objective/reward forms expose supported types and fields rather than raw JSON. Resizing retains draft, selection, viewport and history.

## Evidence

Pure tests cover CRUD/reference repair, chapter duplication, patch bounds, defensive copies, persistence checksums/unknown versions, failed-save preservation, two-operator conflicts, permission/incarnation rejection, chunk ordering/expiry, local queue/history, and layout/hit testing. Compile through the normal JDK 21 wrapper; run quest and affected config/Codex tests sequentially. Dedicated GameTests verify command registration and draft store restore without client classloading. Live two-operator, restart and small-window/high-GUI-scale screenshots remain separate acceptance evidence unless actually exercised.
