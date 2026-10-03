# Phase 2 Quest Workshop implementation and validation

Implemented in the existing checkout on 2026-10-02 for Minecraft 1.21.1 / effective NeoForge 21.1.250 / mod version 5.0.0. `build.gradle` explicitly owns those effective versions despite older values in `gradle.properties`; no release/dependency version was changed for this task. The implementation is uncommitted and preserves unrelated rendering/viewer edits. This file records source/build evidence separately from live native-client acceptance.

## Use the Workshop

Open `/wo quest editor` as an authorized player in an ordinary integrated or dedicated-server world. The existing quest edit permission applies (default operator level two), and the server checks permission, feature enablement, authenticated player incarnation and current editor session again on requests and private transfers.

- Create a Chapter, then a Quest. Choose a namespaced ID and title before creation. IDs remain stable after creation; Properties edits the title and other fields. Copy assigns new IDs and remaps references inside copied chapters.
- Select a chapter in the sidebar and search its quest titles/IDs. Drag a node to move it; middle-drag the canvas to pan; scroll to zoom. Arrow keys select quests when the canvas has focus. Enter opens Properties. At narrow GUI widths, use Properties instead of the collapsed inspector.
- Select the prerequisite quest, press Link, then left-click a dependent quest to add a prerequisite or right-click to remove it. Escape exits linking. Rules exposes the full prerequisite list and all/any mode.
- Properties contains Basics, Rules, Objectives and Rewards. Supported objective forms are possession (items and `#tags`), dimension, lore and custom event. Rewards are item, XP and lore. Objective IDs remain stable, and removing an objective repairs its local `dependsOn` references. Chapter properties expose required/optional mod IDs; campaign properties expose title/description. Apply creates one undoable draft edit; Cancel asks before discarding unsaved form work.
- Undo/Redo or Ctrl+Z/Y (Ctrl+Shift+Z also redoes) operate on draft edits, including positions and links. The model survives screen resize/re-init. Ctrl+S or Save requests queued saves immediately; otherwise edits autosave after a 750 ms idle debounce.
- Check first saves pending edits, then requests current server validation. Findings shows up to 32 findings with content/field paths in a scrollable screen. Validation does not activate content.
- If another operator saves a newer revision, the editor visibly blocks further saves and retains local changes. Reload explicitly discards local changes after confirmation and loads the latest acknowledged draft. There is no automatic merge.
- Closing the Workshop lets queued saves finish, then releases its operator slot. A failed or conflicted save retains local work in the client cache until explicit reload/disconnect; reopening does not silently overwrite it. Disconnect clears server-specific client caches. Only acknowledged revisions are promised to restore after a server restart.

## Ownership and bounds

The server session owns authoring admission and revision checks. The existing leased publication store and ordered I/O lane force and atomically replace `wildernessodysseyapi/quests/drafts/workshop.json` inside that world's save directory. The draft envelope contains format, world/draft identity, revision and checksums. Corrupt/future files disable authoring only and are preserved for review.

One shared world draft supports 128 chapters, 2,000 quests, 256 KiB per source and a 16 MiB aggregate bound. Payloads use 16 KiB chunks, strict ordering/replay/expiry checks, eight operator sessions, bounded transfer memory and four outbound chunks per tick. Client pending edits/history are also bounded. The canvas draws at most 1,024 prerequisite connections per frame; Rules retains every stored prerequisite. Draft bodies are private to authorized editor sessions. Ordinary player projections, live campaign identity, progress and the permanent reward ledger retain their Phase 1 owners.

Native draft publication/history/rollback/export, complete registry catalog/capture/testing tools, extra providers, journal/HUD/story/audio and FTB retirement remain later phases. Existing `/wo quest publish` continues to publish validated datapack candidates; it does not publish this Workshop draft. The networking version is 33, so client/server builds must match.

## Final completed evidence

| Validation | Result |
| --- | --- |
| Production `compileJava` through JDK 21 wrapper / isolated output | Passed, including native screen/event adapters |
| `questTest` | 110 discovered; 109 passed, zero failures/errors, one existing filesystem-link skip |
| `questCompatibilityTest` | All 21 passed (config migration/spec and existing Codex/lore logic) |
| Focused dedicated `runGameTestServer -PquestGameTests` | All six required tests passed before and after the four review fixes; Gradle exited zero |
| Root Minecraft mod `:build` plus final focused GameTests | Passed after fixes; normal Gradle pipeline, no test exclusion |
| Latest all-project `build` | Failed four assertions in the separately edited Structure Viewer tests; see below. An earlier pre-review build passed |
| Workshop language JSON parse | Passed |

Pure tests exercise actual CRUD membership/reference repair, copying, draft serialization, real temporary-file restore, failed-write retention, competing revisions, permission/incarnation checks, accepted shutdown writes, packet ordering/expiry/bounds/pacing, local history and save queues, rejected uploads/deferred close, malformed snapshots, stale-local-work retention, small-window geometry and typed forms compatible with the existing definition codec. Dedicated GameTests exercise loaded-world foundation adapters, real fake-player inventory/XP/lore delivery, mandatory storage startup, command permission registration and Workshop restore. They do not establish authentic client networking, visual behavior or process-kill durability.

Commands (run sequentially):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat questTest questCompatibilityTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat runGameTestServer '-PquestGameTests' '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

The recurring `helper_sandbox_lock_failed / SetNamedSecurityInfoW ... failed: 5` occurred before process creation; focused approved retries ran successfully. No ACL, antivirus, cache or generated-JAR modifications were used. StructureGen reports an existing dependency-catalog fingerprint mismatch and uses its fallback while verifying three structures; this is separate from quest validation. The GameTest startup reported a temporary tick-lag warning; this task makes no performance claim.

## Remaining manual acceptance

1. Two real operator clients: concurrent drafts, delayed uploads, visible stale/conflict handling, demotion while a save is accepted, reconnect/new-session late frames, and repeated open/close beyond eight users. Confirm ordinary players never receive draft bodies.
2. Integrated and dedicated worlds: edit and wait for the saved revision; close/reopen; restart and compare the same acknowledged draft, node positions and live hash. Exercise a save failure and explicit conflict reload without silent local loss. Repeat Workshop saving with shared async/DataEngine disabled; the default dedicated profile passed here.
3. Native screenshots and interaction at small windows/high GUI scales: sidebar/search, node dragging, panning/zoom, focus/keyboard shortcuts, link removal, long IDs/text, validation scrolling, forms, resize, and conflict messages. Geometry/unit checks and compilation are not visual sign-off.
4. Phase 1's real multiplayer progression, reward claim/reconnect and controlled process-interruption/save tests remain outstanding as documented in `phase-1-validation.md`.

## Fresh review and regressions

One fresh read-only review found four Important issues, no Critical issues and no deferred Minors. They were corrected in one pass; no second review was substituted for regression evidence.

| Finding | Fix and regression |
| --- | --- |
| An old editor/server/incarnation frame could remove the new editor session | Admission now ignores stale identities before touching buffers; matching unauthorized sessions revoke with an empty private-data-free response. `delayedOldEditorServerAndPlayerFramesCannotRevokeTheCurrentSession` passes |
| A transport-valid boundary-depth draft could be ACKed but fail wrapped-file restore | The exact restart decoder validates the complete envelope before atomic replacement. Rejection preserves preceding bytes/revision and permits a normal retry. `storageEnvelopeBoundsCannotReplaceTheLastRestorableDraft` reproduced the old acknowledgement and now passes |
| Null/primitive/array objective or reward entries crashed native forms | Typed views guard malformed members and offer explicit removal/add repair; copies keep the model unchanged until Apply and tolerate malformed dependency members. `malformedEntriesRemainVisibleAndCanBeRemovedWithoutChangingTheModel` passes |
| CLOSE could be rate-limited after saves and leave its operator slot occupied | A matching authorized empty close releases immediately without consuming work admission. `authenticatedEmptyCloseReleasesEvenWhenTheWorkAdmissionBucketIsExhausted` passes; the existing deferred-close client test also passes |

The store regression failed an actual assertion with the old implementation. The new defensive session/form tests first failed at compilation because their required guard interfaces were absent. After implementation, all 110 quest tests and 21 compatibility tests had zero failures/errors. The final dedicated run passed all six required tests.

The latest broad `questTest questCompatibilityTest build` reached four failures under `:tools:structure-viewer:test`: `retainsSavedDarkAppearanceAlongsideExistingPreferences`, `repeatedBoundaryQuadsCannotOccludeAWholeNeighbor`, `solidCubeHasNoInteriorPixelHolesAcrossAnglesAndResolutions`, and `keepsInsetFacesWhenAnOpaqueNeighborDoesNotTouchThem`. Those sources/tests were already separately edited and are outside the quest work; this task did not alter or diagnose them. The entire checkout's latest build is therefore not green. The normal root `:build` for the Minecraft mod and the focused dedicated quest tests completed successfully afterward. This scopes the verified artifact without suppressing tests or reconstructing the build pipeline.

```powershell
.\gradlew.bat questTest questCompatibilityTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat :build runGameTestServer '-PquestGameTests' '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

## Built artifact

Installable regular JAR: `.codex-build/libs/wildernessodysseyapi-5.0.0.jar` (27,751,245 bytes). It contains the updated native screens, defensive session admission, draft store and Workshop translations; the mod metadata declares version 5.0.0. Client/server builds must use the same networking version 33. It contains the current checkout, including the user's unrelated source edits; it is not a release sign-off. The September `wildernessodysseyapi-4.2.0.jar` is old output, was left intact, and does not contain this Workshop.

SHA-256: `CF05F3F6EF4F819CA6F19619EFF93939924061ECC3BA8F5EFD04096EA4EA03A7`.

The plan, implementation and review notes remain uncommitted. Decisions, in execution order:

| Decision | Reason | Cost if wrong |
| --- | --- | --- |
| Execute the already written Phase 2 scope without repeating authorization | User explicitly requested implementation in the approved checkout | Misunderstood scope requires a focused correction |
| One shared world draft; positions remain draft-only | Minimal concrete multi-operator revision contract | Separate draft branches need a later extension |
| Native draft publication GUI stays Phase 4 | Written roadmap assigns it there | Authors wait for activation tooling |
| Keep work uncommitted and retain local bookkeeping | No commit/integration request; unrelated edits must remain intact | Later deliberate commit/cleanup is required |
| Draw at most 1,024 connections per frame | Bound dense-graph render work; retain underlying references | Dense graphs require the Rules list to inspect all references |
| Review explicit current-file manifest | Approved work includes untracked files that a HEAD diff omits | An omitted file could weaken review |
| Keep capabilities the reviewer set aside in later phases | Approved publishing/catalog/provider/FTB scope | Those capabilities wait for their scheduled phase |
| Keep unrelated rendering/viewer changes outside review | Preserve the user's other work | Their cross-system/tool issues need separate review |
| Keep real-client/native screenshots as manual gaps | Geometry/source cannot establish authentic runtime behavior | Runtime/visual defects may remain until acceptance |
| Retain existing atomic-file contract without claiming crash proof | Hardware/process-kill testing was not performed | Abrupt-failure filesystem/Minecraft-save outcomes remain unproven |
| Verify root mod packaging and report separate viewer failures | Narrow requested scope and preserve unrelated source | The entire checkout remains non-green until that tool is diagnosed |
