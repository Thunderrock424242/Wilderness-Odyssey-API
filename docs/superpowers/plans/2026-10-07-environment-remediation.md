# Environmental system remediation

The user approved fixing every finding, behavior gap, and performance concern in the environmental audit. Water and weather authorities remain outside the change scope. Existing owner APIs, saved records, resource IDs, and unrelated worktree edits must remain compatible.

## Design and constraints

- Natural 60–125 block meteor craters become deterministic structures whose pieces write only inside the chunk supplied by Minecraft. Registration uses a completed server chunk's structure start, never background SavedData mutation. Retain the legacy feature ID as a safe compatibility entry point.
- Nearest meteor metadata and strongest radiation exposure remain separate concepts. Regional influence and synchronized exposure use the same strongest-exposure authority as player radiation.
- Wildlife restores manager-owned AI before applying profile/participation filters and shares safety protections between suspension and abstraction. Loaded wildlife discovery and metrics receive explicit work bounds or dirty-state updates.
- Ecosystem and vegetation producers honor existing dimension capabilities; The Before remains inert. Stored historical records are preserved.
- Vegetation rebuilds relevant occupied render sections, limits stale-queue work, honors the master toggle at public mutation APIs, and keeps diagnostic-only work from forcing chunk saves. Preserve the current loaded-chunk update/time contract.
- Late glacial writes protect structures, including neighboring starts, and newly arriving players receive presentation state immediately. Queue cleanup remains bounded.
- Only the root agent runs Gradle or Minecraft. Use the checked-in wrapper with `'-PcodexBuildDir=.codex-build' --no-parallel`. No generated JAR editing, permission changes, world deletion, broad cleanup, or new dependency.

## Tasks and ownership

1. Root: meteor structure/legacy feature, server registration, deterministic bounded generation, overlap exposure and regional/network consumers; production and meaningful persistence/generation regressions.
2. Wildlife workstream: ecosystem ownership/restoration/safety, dimension participation, incremental candidate work and metrics, mirrored regressions.
3. Vegetation workstream: API and scheduler gates, persistence, client invalidation/queues, bounded sampling/unload scheduling, mirrored regressions.
4. Glacial workstream: structure/write protection, initial player synchronization, client queue bounds, mirrored regressions.
5. Root: integration, compilation, focused subsystem tests, appropriate broader regression coverage and packaging, narrowly scoped GameTests where practical, final diff review and acceptance documentation.

## Shared interfaces and review focus

- All producers consume existing `EnvironmentDimensionProfile`; no competing dimension policy is introduced.
- Root meteor registration uses `MeteorSiteServices`, which keeps index deduplication and existing SavedData compatibility.
- Root radiation changes must retain the existing packet wire shape and compatible public snapshot constructors.
- Worker file ownership is disjoint; root owns `core/ModRegistries`, shared `environment/api`, `environment/simulation`, and `environment/network` integration.
- Tests should exercise behavior and boundaries. Loaded-world behavior needs GameTests or clearly documented manual validation; source checks alone cannot prove gameplay or performance.
- Existing environmental baseline: 140 tests across 49 classes passed during the preceding audit. Unrelated dirty files are the loading stall detector and its performance documentation.

## Progress

- Planning: approved audit recommendations carried into this implementation plan; independent workstreams dispatched, all validation assigned to root.
- Workspace: current `in-dev` checkout; existing unrelated edits remain untouched. No branch publication or world migration is part of the task.
- Implementation: wildlife candidate lifecycle, owned AI restoration, shared safety vetoes, ecosystem participation, cached population/cell metrics, glacial structure guards, initial player synchronization and bounded client cleanup are implemented.
- Implementation: vegetation APIs/scheduling/synchronization honor dimension policy; mutations honor the master toggle. Diagnostic/timestamp-only changes no longer dirty a chunk, scheduling removal is logarithmic, client dirty requests are deduplicated and removable, and occupied render sections refresh under the existing two-chunk cap. Alternating bounded ground probes reach plants under leaves; surface probes also correct the ChunkAccess occupied-height off-by-one.
- Implementation: natural crater structure/piece registries and resources replace unsafe feature injection. Core radii remain 60–125; the complete write footprint is capped at 127 blocks to fit Minecraft's eight-chunk start-reference radius. The largest crater's outer rim/ejecta is consequently clipped. Legacy feature/configured/placed IDs remain, with preflight refusal rather than partial writes.
- Implementation: persisted starts are handed to the server SavedData index after promotion. Regional radiation and unchanged-format packets use strongest exposure separately from nearest-site metadata, including legacy saved radii. Independent review caught stale generation-only heightmaps and chunk-dependent fragment geometry; both were corrected and regression-tested.
- Integration: the existing ChunkMap structure-policy seam filters the natural crater set from every dimension whose environmental profile disables natural meteors, including the Anomaly dimension's shared Overworld biome. The Before generator consumes the supported HolderLookup interface without assuming a concrete registry lookup.
- Validation: production compile and focused validation passed. The initial regression runs reproduced vegetation persistence/queue, unsafe meteor bounds, overlap exposure and six glacial failures before their fixes. Expanded focused coverage passed 209 tests across 74 classes with no failures/errors/skips.
- Runtime setup: the isolated environmental namespace has its own copy of the existing empty template, packaged by Gradle. NeoForge filters tests by template namespace; tests use an unqualified template name. Two setup attempts identified and corrected namespace handling before gameplay assertions could run.
- Concurrent work: another chat is modifying water/weather and shared configuration. Those changes remain outside this remediation; only root runs this task's Gradle commands. Shared generated reports can be overwritten by other work, so retain command results and task-specific console logs.
- Review: resolved source confirmed that Join precedes accepted insertion and Leave includes temporary tracking loss. Regression runs reproduced cancelled joins, tracking-only removal, and profile changes while hidden. Candidate ownership now checks canonical server identity, retains physical lifetime independently of profile eligibility, installs controllers after first-time eligibility, and runs bounded cleanup while disabled. The final independent applied-source review reported no remaining Critical or Important issue in those changes.
- Loaded-world validation: all 10 environmental GameTests passed. Fixtures cover real cancelled/duplicate joins, hidden-section transitions without chunk loading, disabled physical-removal cleanup, controller/profile/NoAI behavior, vegetation toggle mutations, crater start indexing, and regional overlap exposure. Third-party handshake assumptions and unsupported custom-dimension flat-preset fixtures were removed from the tests; their actual runtime limits are documented.
- Acceptance: `docs/environmental-system-remediation.md` records changes, compatibility, performance bounds, and remaining regular-world/client/multiplayer/profiling checks. Final root JUnit passed 1,431 tests with four existing skips (1,435 total), zero failures/errors. The aggregate build packaged the root mod then failed in the unchanged Aether executable test's incomplete-header socket assertion (Connection reset). Isolated root `:build` passed, and read-only JAR inspection confirmed crater resources/classes, legacy feature compatibility, unsafe injection removal, and the compressed GameTest template. Implementation and automated environmental verification are complete; no commit or publication was requested.
