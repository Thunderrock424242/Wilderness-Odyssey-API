# Executive Summary

**Audit date:** 2026-08-18<br>
**Scope:** Repository-wide, read-only pre-release audit of production Java, tests, build logic, mod metadata, mixin configuration, assets/data, documentation, generated-resource conventions, and the pre-existing packaged JAR.<br>
**Target:** Minecraft 1.21.1, NeoForge 21.1.248, Java 21, Parchment 2024.11.13, ModDevGradle 2.0.143, mod version 4.2.0.<br>
**Inventory:** 955 production Java files, 211 test Java files, 118 source-resource files, 67 statically configured mixins plus 5 dynamically selected Embeddium mixins.

**Release assessment: not ready to ship.** The codebase contains several unusually ambitious and well-separated systems, but five confirmed or high-confidence critical paths should block a release:

1. generated mod metadata does not follow the NeoForge 1.21.1 dependency/range schema and does not constrain Minecraft or NeoForge;
2. an unguarded required mixin targets WorldEdit even though WorldEdit is neither bundled nor declared required;
3. Riftfall counts entities through a world-sized `AABB` on recurring server ticks;
4. the SPH singleton can let a client tick drain and execute queued server-world settlement work;
5. natural meteor placement performs a very large synchronous vertical/block search and can touch unloaded chunks.

The largest server-performance concerns are the Riftfall world-sized entity queries, meteor landing search, the expanded structure-block save/detection scans, a global `Level#setBlock` reconciliation hook, and repeated full loaded-entity enumeration by the ecosystem manager. These are **static-analysis findings, not measured profiler results**. The first three have enough bounded-loop evidence to justify action before profiling; the latter two should be profiled in a representative 200–500-mod pack before redesign.

The largest compatibility concerns are the required WorldEdit target, global Create schematic-directory mutation, global entity-copy behavior in WorldEdit operations, 72 mixin classes touching water, weather, world generation, rendering, structure blocks, commands, Create, WorldEdit, Sodium, Embeddium, and Iris, and two pairs of overlapping weather/render injections. The broad water integration is coherent, but its many hot-path injections materially increase the integration surface.

The largest architectural concern is competing runtime orchestration. The established `AsyncTaskManager`, the new Performance engine, and the new Data Engine can all own queues, budgets, or worker execution. Data Engine correctly reuses the established executor, while the Performance engine creates another pool and is enabled before production subsystems use its opt-in helpers. Authority should be consolidated incrementally, not through a rewrite.

The audit found a small but real obsolete/incomplete set: a no-op and unreferenced meteor biome-modifier class, an orphan impact-zone tag route, a legacy wave consumer that is not connected to rendering, several unreferenced utility/opt-in framework classes, and placeholder Rift entity textures. These are not numerous enough to define the codebase, and indirect Minecraft entry points were considered before assigning dead-code confidence.

Important strengths should be preserved:

- water and weather have explicit server-authoritative service boundaries;
- water/weather network payloads are bounded and validated;
- temporary flooding and projection code generally checks loaded chunks;
- renderer compatibility mixins for Sodium, Embeddium, and Iris are selected by a mixin plugin;
- the replacement water mesh path uses budgets and stable buffers instead of unbounded rebuilds;
- Data Engine submissions are bounded and non-blocking;
- configuration values are generally range-validated and published as immutable snapshots;
- the StructureGen route validates source JSON and publishes generated NBT rather than hand-editing `bunker.nbt`.

The recommended order is correctness and startup safety first, then catastrophic tick paths, then migration/async lifecycle correctness, then resource packaging and compatibility hardening, and finally measured optimization and conservative cleanup. Do not replace real water/weather ownership with fake simulations or ordinary block placement, and do not remove API-looking or data-driven classes solely from reference counts.

## Audit Method and Confidence

The audit traced systems from registration to runtime triggers, persistence, network consumers, resources, and tests. Searches covered event subscribers, explicit bus registration, mixin/plugin loading, registry/codecs, commands, packets, JSON identifiers, static state, executors, scheduled work, TODO/stub markers, placeholders, and potentially unbounded iteration.

Confidence labels mean:

- **High:** the behavior follows directly from a reachable code path, metadata/resource path, or packaged artifact;
- **Medium:** the path is reachable but the practical impact depends on runtime scale, another mod, or configuration;
- **Low:** a conservative candidate requiring live or integration verification.

Performance estimates are operation-count approximations from defaults and loop bounds. No Spark/JFR/GPU capture was obtained during this audit, so this report does not present any static estimate as measured MSPT, TPS, FPS, heap, allocation rate, or network throughput.

## Finding Index and Release Gate

| Severity | Count | Release meaning |
| --- | ---: | --- |
| Critical | 5 | Block release until fixed and integration-tested |
| High | 7 | Resolve before a broad public/modpack release |
| Medium | 11 | Schedule after blockers; profile where marked suspected |
| Low | 6 | Conservative cleanup or hardening |

## Critical Findings

- [AUDIT-META-001](#audit-meta-001--generated-mod-metadata-is-not-validly-constrained-for-neoforge-1211): release metadata schema/range drift.
- [AUDIT-MIX-001](#audit-mix-001--required-worldedit-mixin-is-not-optional-or-declared-required): missing optional-mod guard can prevent startup.
- [AUDIT-PERF-001](#audit-perf-001--riftfall-counts-entities-through-a-world-sized-aabb): recurring world-sized entity query.
- [AUDIT-CONC-001](#audit-conc-001--client-tick-can-drain-server-sph-settlement-work): cross-side/server-world mutation risk.
- [AUDIT-PERF-002](#audit-perf-002--meteor-landing-search-can-perform-millions-of-synchronous-block-reads): synchronous, potentially chunk-loading meteor search.

## High Priority Findings

- [AUDIT-COMPAT-001](#audit-compat-001--create-schematic-directories-are-globally-replaced)
- [AUDIT-COMPAT-002](#audit-compat-002--worldedit-entity-copying-is-enabled-for-unrelated-pastes)
- [AUDIT-PERF-003](#audit-perf-003--expanded-structure-block-operations-can-freeze-a-server)
- [AUDIT-MIG-001](#audit-mig-001--world-upgrade-progress-is-committed-before-migration-completes)
- [AUDIT-CONC-002](#audit-conc-002--async-overload-fallback-runs-io-on-the-calling-thread)
- [AUDIT-RES-001](#audit-res-001--recipes-and-vanilla-tags-use-pre-121-directory-names)
- [AUDIT-LIFE-001](#audit-life-001--riftfall-static-state-survives-world-and-server-lifecycles)

## Medium Priority Findings

- [AUDIT-CONC-003](#audit-conc-003--async-config-reload-can-race-and-drop-work)
- [AUDIT-TEL-001](#audit-tel-001--telemetry-retains-server-and-player-lifecycle-state)
- [AUDIT-TEL-002](#audit-tel-002--telemetry-enqueue-can-create-quadratic-write-amplification)
- [AUDIT-TEL-003](#audit-tel-003--telemetry-reads-live-player-state-off-thread-and-can-block-logout)
- [AUDIT-PERF-004](#audit-perf-004--every-server-block-mutation-enters-water-reconciliation)
- [AUDIT-PERF-005](#audit-perf-005--ecosystem-updates-enumerate-all-loaded-entities)
- [AUDIT-PERF-006](#audit-perf-006--weather-publication-rebuilds-large-view-collections)
- [AUDIT-PERF-007](#audit-perf-007--new-shoreline-regions-cause-bathymetry-scan-bursts)
- [AUDIT-ARCH-001](#audit-arch-001--runtime-budget-and-worker-ownership-is-duplicated)
- [AUDIT-VIS-001](#audit-vis-001--two-rift-entity-renderers-still-use-placeholder-textures)
- [AUDIT-LIFE-002](#audit-life-002--meteor-cadence-is-global-static-state)

## Low Priority Findings

- [AUDIT-CONF-001](#audit-conf-001--meteor-minimum-and-maximum-values-are-not-cross-validated)
- [AUDIT-DEAD-001](#audit-dead-001--legacy-wave-consumer-path-is-disconnected)
- [AUDIT-DEAD-002](#audit-dead-002--meteor-biome-modifier-and-impact-zone-data-are-orphaned)
- [AUDIT-PERF-008](#audit-perf-008--assigned-players-still-enter-the-cryo-spawn-handler-every-tick)
- [AUDIT-LIFE-003](#audit-life-003--small-static-clientserver-sets-lack-explicit-stop-cleanup)
- [AUDIT-PERF-009](#audit-perf-009--gpu-diagnostic-hooks-wrap-every-instrumented-draw)

## Detailed Evidence Catalog

### AUDIT-META-001 — Generated mod metadata is not validly constrained for NeoForge 1.21.1

**Severity:** Critical<br>
**Confidence:** High<br>
**Category:** Build / Dependency / Startup<br>
**Location:** `build.gradle:300-313`; `gradle.properties:18-31`; `src/main/templates/META-INF/neoforge.mods.toml:7-127`<br>
**System:** Gradle metadata generation

**What happens**

The build hardcodes metadata values instead of using the corresponding Gradle properties. The resulting JAR uses `loaderVersion="5.0.3"` rather than a Maven range, uses the obsolete/unsupported `mandatory` dependency key for several dependencies, intends Spark to be optional through `mandatory=false` rather than `type="optional"`, and declares no Minecraft or NeoForge dependency ranges. It also emits license `APR` and a placeholder-quality description while `gradle.properties` contains different release values.

**Evidence**

`build.gradle` defines `minecraft_version_range "1.21,1.21.1"`, `neo_version_range "21.1.248,"`, `loader_version_range "5.0.3"`, and metadata strings, but the template does not consume the Minecraft/NeoForge range values. Inspection of the pre-existing `build/libs/wildernessodysseyapi-4.2.0.jar` confirmed the bad values and `mandatory` keys in the packaged `META-INF/neoforge.mods.toml`. NeoForge 1.21.1 documentation defines `loaderVersion` and `versionRange` as Maven ranges and defines optional dependencies with `type="optional"`; `type` otherwise defaults to required: https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles

**Why it matters**

This can cause metadata parse/startup failure, make an intended optional dependency required, or permit unsupported Minecraft/NeoForge versions. Even if one loader build tolerates part of the schema drift, the artifact does not express the compatibility contract needed by a public modpack.

**Scaling**

One-time startup impact; blast radius is every installation and every dependency-resolution environment.

**Recommended fix**

Make `gradle.properties` the single source of truth, use valid bracketed Maven ranges, add explicit required `minecraft` and `neoforge` dependency blocks, replace all `mandatory` entries with current `type` values, and add a test that opens the built JAR and asserts the processed metadata fields.

**Risk of changing:** Medium — metadata mistakes can make previously tolerated pack combinations fail fast, so test several intended dependency matrices.

### AUDIT-MIX-001 — Required WorldEdit mixin is not optional or declared required

**Severity:** Critical<br>
**Confidence:** High<br>
**Category:** Mixin / Dedicated-server startup / Compatibility<br>
**Location:** `src/main/resources/mixins.wildernessodysseyapi.json:3,27`; `src/main/java/com/thunder/wildernessodysseyapi/mixin/ForwardExtentCopyMixin.java:27-41`; `build.gradle:142-154`<br>
**System:** WorldEdit compatibility

**What happens**

The required mixin configuration always lists `ForwardExtentCopyMixin`, whose target and imports directly reference WorldEdit. The mixin is not `@Pseudo`-guarded, the mixin plugin does not conditionally select it, WorldEdit is not bundled, and mod metadata does not declare WorldEdit required.

**Evidence**

The mixin JSON has `"required": true` and names the mixin unconditionally. `WildernessMixinConfigPlugin` has class-resource checks for Iris, Sodium, and Embeddium only. The pre-existing JAR contains the mixin but no WorldEdit classes.

**Why it matters**

A pack without WorldEdit can fail during classloading or mixin target resolution before the mod reaches normal initialization. That violates the apparent compile-only/optional intent and is especially serious for dedicated servers.

**Scaling**

One-time startup failure affecting every installation without the target mod.

**Recommended fix**

Either declare WorldEdit as truly required in valid mod metadata or select this mixin only when its exact target class is present. Keep the guard inside the existing mixin plugin and test both with-WorldEdit and without-WorldEdit client and dedicated-server matrices.

**Risk of changing:** Low for adding a correct guard; High if changing the functional WorldEdit behavior at the same time.

### AUDIT-PERF-001 — Riftfall counts entities through a world-sized AABB

**Severity:** Critical<br>
**Confidence:** High<br>
**Category:** Server TPS / Entity / Large-modpack compatibility<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/riftfall/RiftfallSystem.java:305-387`<br>
**System:** Riftfall spawn caps

**What happens**

Recurring Riftfall phases count matching entities with `ServerLevel#getEntities` over an `AABB` spanning approximately ±30,000,000 blocks. This happens separately for the ambient, elite, and boss categories.

**Evidence**

Calls at approximately lines 310, 349, and 382 construct the coordinate-spanning box and call `.size()`. Default intervals are 80, 360, and 520 ticks with caps of 60, 8, and 4.

**Why it matters**

Entity lookup structures are designed for local spatial queries. A world-sized query can traverse enormous section ranges or otherwise stress the entity index, producing severe tick stalls or watchdog failure. Modded dimensions and higher entity counts increase the risk.

**Scaling**

`dimensions × Riftfall phases × spatial-section span`, recurring every 4, 18, and 26 seconds by default. This is not a safe `O(loaded entities)` count.

**Recommended fix**

Maintain server/dimension-scoped counts from entity add/remove/unload/death lifecycle hooks, or iterate an already-loaded entity index exactly once with a hard budget. Reconcile periodically from loaded entities without coordinate-spanning spatial queries.

**Risk of changing:** Medium — counts must remain correct across chunk unload, conversion, death, dimension transfer, and server restart.

### AUDIT-CONC-001 — Client tick can drain server SPH settlement work

**Severity:** Critical<br>
**Confidence:** High<br>
**Category:** Thread safety / Client-server separation / World mutation<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/SPHSimulationManager.java:37,439-480,651-664`; `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/sph/ServerTickHandler.java:46-53`; `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/render/ClientTickHandler.java:43`<br>
**System:** SPH simulation settlement

**What happens**

`SPHSimulationManager` is a process-wide singleton with one `pendingSettlements` queue. Both server and client tick handlers call `tickLevel`. That method drains the queue before applying level/side filtering, and a queued callback can retain a `ServerLevel` and materialize canonical water.

**Evidence**

`runPendingSettleCallbacks` removes all pending callbacks. `applyPendingSettlement` can call `materializeCanonicalVolume(ServerLevel, ...)`. The client tick reaches the same singleton in integrated-client processes, so it can win the queue race.

**Why it matters**

Minecraft world mutation must occur on the owning server thread. Executing settlement from the render/client thread risks races, corrupt or stale chunk access, hard-to-reproduce crashes, and cross-world application.

**Scaling**

Probability rises with SPH workload, integrated-server use, and client/server tick interleaving; impact is correctness rather than a linear cost.

**Recommended fix**

Partition pending work by server/level and logical side, carry only immutable computation results across workers, and drain server mutations exclusively from the owning server thread after verifying the level is still active and the chunk is loaded. Client ticks must never apply server callbacks.

**Risk of changing:** High — preserve conservation, persistence, projection, and render handoff semantics with focused tests plus integrated-client validation.

### AUDIT-PERF-002 — Meteor landing search can perform millions of synchronous block reads

**Severity:** Critical<br>
**Confidence:** High<br>
**Category:** Server TPS / Chunk lifecycle / World search<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/meteor/event/MeteorImpactEvent.java:123-275`<br>
**System:** Natural and command-triggered meteor placement

**What happens**

Each meteor may attempt up to 20 candidate landings. Each candidate performs a radius search for crying obsidian with a default radius of 64 and step 2, and each sample calls a vertical ground scan from around Y=320 to the minimum build height. Candidate positions can extend hundreds of blocks from spawn and are read without a loaded-chunk rejection.

**Evidence**

The default grid is roughly 65×65/4, or about 3,200 sampled columns. A roughly 384-block vertical scan with repeated `getBlockState` calls puts a worst candidate near a million block reads before the next attempt. Natural count defaults to 2–5, while the command path can request more.

**Why it matters**

This is synchronous server-thread work and can create single-tick spikes. Accessing unloaded positions can also synchronously load/generate chunks, multiplying the worldgen and I/O cost.

**Scaling**

`meteors × up to 20 attempts × sampled columns × vertical depth`; static upper-bound estimate, not measured MSPT.

**Recommended fix**

Reject unloaded chunks, use heightmaps for surface Y, bound a small randomized sample set, index rare anchors through a POI/tag-aware loaded-region cache when appropriate, and spread multi-meteor commands across a server-owned budgeted queue.

**Risk of changing:** Medium — preserve landing exclusions, anchor semantics, crater placement, and deterministic/manual-command expectations.

### AUDIT-COMPAT-001 — Create schematic directories are globally replaced

**Severity:** High<br>
**Confidence:** High<br>
**Category:** Mod compatibility / User data visibility<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/mixin/CreatePathsMixin.java:21-40`<br>
**System:** Create integration

**What happens**

At the end of Create's static path initialization, the mixin replaces Create's global schematic and uploaded-schematic directories with `gameDir/data/wildernessodysseyapi/schematics`.

**Evidence**

The `<clinit>` tail injection mutates `CreatePaths.SCHEMATICS_DIR` and `UPLOADED_SCHEMATICS_DIR`. No source schematic asset exists at that location, and this is a filesystem directory, not a built-in datapack path as the comment suggests.

**Why it matters**

All normal Create schematics and uploads become invisible for the whole game instance, and every other mod/user expecting Create's standard directories observes changed global behavior.

**Scaling**

One-time global mutation; impact applies to all Create schematic users and integrations.

**Recommended fix**

Remove the global replacement and use an explicit import/copy/registration workflow supported by Create. Before changing it, detect and safely migrate any user files already written into the custom directory; never silently strand or overwrite them.

**Risk of changing:** High — user-created schematic data may already exist in either path.

### AUDIT-COMPAT-002 — WorldEdit entity copying is enabled for unrelated pastes

**Severity:** High<br>
**Confidence:** High<br>
**Category:** Compatibility / Gameplay correctness<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/mixin/ForwardExtentCopyMixin.java:41-77`<br>
**System:** WorldEdit and Create contraption paste compatibility

**What happens**

When Create is loaded, the mixin sets WorldEdit's `copyingEntities` flag before confirming that the paste contains a relevant Create entity. The flag is left enabled when the scan finds nothing and is not restored in the exception fallback.

**Evidence**

The assignment occurs before the scan at approximately line 54; the loop returns only after finding a Create entity, but there is no false/reset path for an ordinary paste.

**Why it matters**

Unrelated WorldEdit operations can copy mobs, items, and other entities that the operator did not request, causing duplication and surprising behavior. It modifies a core operation for every WorldEdit user.

**Scaling**

Every WorldEdit copy/paste while Create is loaded; effect grows with entity-rich selections.

**Recommended fix**

Scan first, change the flag only when a target Create entity is actually found, preserve the caller's original option, and restore it on all failure paths. Add an integration matrix for no entities, vanilla entities, Create contraptions, and thrown scan exceptions.

**Risk of changing:** Medium — ensure Create contraption support is retained without changing ordinary WorldEdit semantics.

### AUDIT-PERF-003 — Expanded structure-block operations can freeze a server

**Severity:** High<br>
**Confidence:** High<br>
**Category:** Server TPS / Mixin / Filesystem and NBT<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/mixin/StructureBlockEntityMixin.java:52-1055`; `src/main/java/com/thunder/wildernessodysseyapi/structureblock/StructureBlockSettings.java:13-27`<br>
**System:** Expanded structure-block size, detection, save, and post-processing

**What happens**

A 1,000-line mixin raises structure size/offset limits to 512, replaces size detection, synchronously warms chunks, scans the declared structure volume before save, then reads and may rewrite the generated NBT to strip entities.

**Evidence**

`saveStructure` HEAD iterates the volume around lines 559–590. A 512³ declaration is 134,217,728 block positions. `detectSize` can synchronously touch up to 256 chunks around lines 903–925. The NBT pass uses `NbtAccounter.unlimitedHeap()` around lines 969–997.

**Why it matters**

Although operator-triggered rather than per tick, one action can freeze the server, trigger watchdog termination, or allocate/read hostile amounts of NBT. The broad redirect/injections also conflict easily with protection, structure, and admin-tool mods.

**Scaling**

`sizeX × sizeY × sizeZ` block reads, plus up to 256 synchronous chunk touches and structure-file size.

**Recommended fix**

Introduce hard volume/file bounds, loaded-chunk and permission checks, incremental server-budgeted scanning, bounded NBT accounting, and atomic rewrite. Keep the vanilla-sized path as close to vanilla as possible.

**Risk of changing:** Critical — this touches operator workflows, packet extensions, saved structure files, client rendering, and possibly existing oversized structures.

### AUDIT-MIG-001 — World-upgrade progress is committed before migration completes

**Severity:** High<br>
**Confidence:** High<br>
**Category:** Persistence / World migration<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/worldupgrade/WorldUpgradeManager.java:38-78`; `src/main/java/com/thunder/wildernessodysseyapi/worldupgrade/WorldUpgradeSavedData.java:load`<br>
**System:** Automatic world upgrade

**What happens**

Server start marks the current pack version as processed before the queued chunk migrations finish. The queue is memory-only and cleared on restart. Existing saved data can also retain an older positive target version instead of advancing to a newer code-defined target.

**Evidence**

`onServerStarting` sets processed/version state while initializing work. The legacy migration then scans blocks in queued loaded chunks, one chunk per tick. There is no durable per-phase cursor or completion commit.

**Why it matters**

A crash or normal shutdown mid-upgrade can leave a partially migrated world that will not resume for the same pack version. A later migration version may also be skipped. Destructive migrations must never advertise completion before placement/mutation success.

**Scaling**

`queued chunks × non-empty sections × blocks`; correctness risk persists across restarts.

**Recommended fix**

Persist an explicit state machine (`pending/running/complete`), migration target, durable progress or idempotent rediscovery, and errors. Set the processed pack version only after successful completion; compute `target=max(savedTarget,currentCodeTarget)`. Add operator-visible status/resume controls and backup guidance.

**Risk of changing:** High — preserve existing worlds and make every migration idempotent before changing metadata semantics.

### AUDIT-CONC-002 — Async overload fallback runs I/O on the calling thread

**Severity:** High<br>
**Confidence:** High<br>
**Category:** Server TPS / Concurrency / External I/O<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/async/AsyncTaskManager.java:245-286`; callers in `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIChatListener.java`, `feedback/FeedbackCommand.java`, and `telemetry/**`<br>
**System:** Shared asynchronous execution

**What happens**

The rejection handler sleeps the submitting thread for 5 ms and then executes `task.run()`. With `CompletableFuture.supplyAsync`, that means rejected HTTP, telemetry, file, or Spark work can run inline on the server/event/network caller.

**Evidence**

The executor handler directly invokes the rejected runnable. The task manager is used by network/external-service and persistence paths. By contrast, Data Engine's `trySubmitCpuWork` path rejects non-blockingly, showing the safer pattern already exists.

**Why it matters**

The moment the pool is saturated—precisely when the server is under pressure—the fallback moves slow work onto the main thread and adds a deliberate sleep, converting backpressure into MSPT spikes.

**Scaling**

`rejected tasks × (5 ms + task latency)` on the submitting thread; external latency can be seconds.

**Recommended fix**

Use explicit bounded rejection outcomes: drop/coalesce nonessential telemetry, persist a small retry record, or fail the future. Never caller-run external I/O. Expose rejection counters and give critical tasks a separately bounded policy.

**Risk of changing:** Medium — callers must handle rejected futures and define data-loss/retry policy.

### AUDIT-RES-001 — Recipes and vanilla tags use pre-1.21 directory names

**Severity:** High<br>
**Confidence:** High<br>
**Category:** Resources / Player-facing functionality<br>
**Location:** `src/main/resources/data/wildernessodysseyapi/recipes/*.json`; `src/main/resources/data/minecraft/tags/blocks/**`; `src/main/resources/data/minecraft/tags/items/music_discs.json`<br>
**System:** Datapack recipes and tags

**What happens**

Three recipes are under `recipes` instead of the 1.21 singular `recipe` directory. Mining tags use `tags/blocks` and the music-disc tag uses `tags/items` instead of singular `tags/block` and `tags/item`.

**Evidence**

The source and pre-existing packaged JAR contain only the plural paths. The repository's working `data/minecraft/tags/block/water.json` demonstrates the correct 1.21 convention.

**Why it matters**

The breathing mask, inhaler, and anomaly gateway recipes will not load through the 1.21 resource paths. Mining requirements and the vanilla music-disc membership likewise do not apply, producing missing recipes and incorrect harvesting/tag behavior without a Java exception.

**Scaling**

Deterministic player-facing failure for every pack; no runtime scaling.

**Recommended fix**

Move the files to singular directories, run data/resource validation, inspect the built JAR, and verify `/recipe` and tag membership in a client. Do not move `structures/bunker.nbt`: that plural path is an intentional StructureGen regression fixture, not a runtime structure resource.

**Risk of changing:** Low — resource-pack overrides of the wrong paths are unlikely, but verify pack conventions.

### AUDIT-LIFE-001 — Riftfall static state survives world and server lifecycles

**Severity:** High<br>
**Confidence:** High<br>
**Category:** Lifecycle / Memory / Cross-world correctness<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/riftfall/RiftfallSystem.java:39-40,256-258`<br>
**System:** Riftfall states and player exposure

**What happens**

Per-dimension state and per-player exposure are stored in static maps. They are cleared only by `resetToClear()` when the feature is disabled; no logout, level-unload, or server-stop path clears them.

**Evidence**

Lifecycle registration calls the Riftfall ticker but does not own cleanup. The maps retain dimension keys and UUIDs across an integrated server returning to menu and opening another world in the same process.

**Why it matters**

New worlds can inherit phase/exposure state, and player UUID entries remain retained. Cross-world state is a gameplay correctness defect, not only a small memory leak.

**Scaling**

`servers/worlds opened per process + unique players + dimensions`.

**Recommended fix**

Move state under server/dimension-owned SavedData or a lifecycle object, remove player entries on logout where persistence is not intended, and clear all process state on server stop. Document which values are supposed to persist.

**Risk of changing:** Medium — decide and migrate intentional persistence semantics first.

### AUDIT-CONC-003 — Async config reload can race and drop work

**Severity:** Medium<br>
**Confidence:** High<br>
**Category:** Lifecycle / Concurrency / Configuration<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/config/ModConfigRegistration.java:123-129`; `async/AsyncTaskManager.java:50-75`<br>
**System:** Live config reload

**What happens**

Async configuration reload directly reinitializes the task manager, unlike other runtime reloads that marshal onto the server thread. Reinitialization shuts down pools and clears the main-thread callback queue.

**Evidence**

The async branch calls `AsyncTaskManager.initialize` directly from the reload event. Initialization clears queued callbacks and replaces executors.

**Why it matters**

Reload can race active submissions, cancel in-flight work, and silently discard callbacks that were intended to update server state. The behavior depends on the thread that fires the config event.

**Scaling**

Reload-time race; impact grows with queued and long-running tasks.

**Recommended fix**

Apply the transition on the owning server thread, stop new submissions, drain or explicitly fail queued work, swap pools, and report dropped/cancelled counts. If graceful live replacement is not reliable, mark these settings restart-required.

**Risk of changing:** Medium.

### AUDIT-TEL-001 — Telemetry retains server and player lifecycle state

**Severity:** Medium<br>
**Confidence:** High<br>
**Category:** Memory / Lifecycle / Privacy controls<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryQueue.java:33-45`; `src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryReporter.java:46-47`; `src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryQueueProcessor.java`<br>
**System:** Telemetry

**What happens**

`TelemetryQueue` stores queues in a static `Map<MinecraftServer,...>` without a remove-on-stop path. Player telemetry keeps static UUID caches whose TTL cleanup occurs only when the same UUID is looked up. The processor still obtains a queue periodically while the master queue is enabled even when event/player exporters are disabled.

**Evidence**

No lifecycle removal was found for `QUEUES`, `GEO_CACHE`, or `ACCOUNT_CACHE`. Player/event reporting defaults are disabled, which lowers default exposure but does not eliminate the lifecycle defect.

**Why it matters**

Integrated-server restarts retain server objects, spool state, and stale player data. Repeated worlds or many unique players produce gradual heap growth and make privacy/config behavior harder to reason about.

**Scaling**

`server instances + unique UUIDs + queued entries`.

**Recommended fix**

Own the queue from server lifecycle, flush and remove it on stop, run bounded periodic TTL eviction independent of lookup, and do not instantiate/process exporters when all relevant features are disabled.

**Risk of changing:** Low to Medium — preserve crash-safe spool semantics.

### AUDIT-TEL-002 — Telemetry enqueue can create quadratic write amplification

**Severity:** Medium<br>
**Confidence:** High<br>
**Category:** Disk I/O / Async pressure<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryQueue.java:59-71,150-160`<br>
**System:** Telemetry persistence

**What happens**

Every enqueue schedules persistence, and persistence rewrites the entire queue. A burst therefore queues many full snapshots instead of one coalesced flush.

**Evidence**

The default queue limit is 512. Enqueuing `n` events can schedule snapshots whose combined serialized entry count is proportional to `1+2+...+n`.

**Why it matters**

Failure bursts can create `O(n²)` serialization and write work, saturate the shared I/O executor, and trigger AUDIT-CONC-002's caller-thread fallback.

**Scaling**

Approximately `O(queueSize²)` serialized entries per uncoalesced burst; queue size is bounded but the amplification is still substantial.

**Recommended fix**

Coalesce to one pending flush, debounce safely, or append to a bounded journal followed by atomic compaction. Keep a dirty flag and preserve crash recovery.

**Risk of changing:** Medium — validate durability under abrupt shutdown and partial writes.

### AUDIT-TEL-003 — Telemetry reads live player state off-thread and can block logout

**Severity:** Medium<br>
**Confidence:** High<br>
**Category:** Thread safety / External I/O / Configuration<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryReporter.java:77-151,263-390`<br>
**System:** Player telemetry and Spark integration

**What happens**

Async lambdas capture `ServerPlayer` and later read server/profile/game state and invoke reflective Spark collection away from the server thread. An optional configuration can block logout waiting for Spark/network completion, with a large configurable timeout.

**Evidence**

Payload construction is inside asynchronous work rather than from an immutable snapshot. `blockLogoutUntilSparkSent` defaults false, but when enabled can wait up to the configured timeout (default 15 seconds, allowed much higher).

**Why it matters**

Minecraft objects are not generally thread-safe, and blocking logout handlers can stall the server. The default reduces immediate risk, so this is not classified Critical.

**Scaling**

`concurrent joins/logouts × external latency`.

**Recommended fix**

Snapshot primitive/immutable player data on the server thread, pass only that snapshot to workers, isolate reflective monitoring calls behind a thread-safe adapter, and remove or tightly cap any synchronous logout wait.

**Risk of changing:** Medium — preserve payload meaning and opt-in privacy behavior.

### AUDIT-PERF-004 — Every server block mutation enters water reconciliation

**Severity:** Medium<br>
**Confidence:** High for execution; Medium for practical impact<br>
**Category:** Suspected server TPS / Mixin compatibility<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/mixin/WaterProjectionMutationMixin.java:19-36`; `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/projection/WorldFluidMutationReconciler.java:72-109`<br>
**System:** Canonical water projection reconciliation

**What happens**

A HEAD injection on `Level#setBlock` forwards every server-side block mutation into water reconciliation. After feature/activity checks, the reconciler performs chunk/state checks before determining that most mutations are irrelevant.

**Evidence**

`Level#setBlock` is a central path used by players, machines, worldgen, structures, and other mods. The reconciler calls `hasChunkAt` and reads the prior block state for active server levels.

**Why it matters**

Small constant overhead on one of Minecraft's hottest mutation methods can become material in automation-heavy or worldgen-heavy 200–500-mod packs. This is a profiling hypothesis; no measured MSPT attribution is available.

**Scaling**

`all server setBlock calls × reconciliation prefilter cost`.

**Recommended fix**

First profile with the feature active and inactive. If material, move the cheapest state/flag predicates ahead of world lookups, cache only lifecycle-safe projection membership, and add narrower integration hooks where available. Do not bypass canonical water authority or replace it with ordinary block placement.

**Risk of changing:** High — missed reconciliation can desynchronize canonical water and its projection.

### AUDIT-PERF-005 — Ecosystem updates enumerate all loaded entities

**Severity:** Medium<br>
**Confidence:** High for execution; Medium for practical impact<br>
**Category:** Suspected server TPS / Entity AI<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/ecosystem/EcosystemSimulationManager.java:328-369`<br>
**System:** Ecosystem regional simulation

**What happens**

Regional updates call `level.getAllEntities()`, filter `PathfinderMob` instances, resolve profiles, and suspend/resume AI. The default regional interval is 40 ticks, with additional activity when players move between cells.

**Evidence**

The manager has bounded regional concepts but pays a global loaded-entity enumeration cost for the work.

**Why it matters**

Large modpacks can have many modded mobs, farms, chunk loaders, and dimensions. Multiple regional updates can repeat the same global enumeration and profile resolution.

**Scaling**

`regional updates × all loaded entities`, approximately every two seconds by default; static analysis only.

**Recommended fix**

Measure first. If material, maintain a lifecycle-aware index of eligible entities or use bounded section queries for the affected regions, with unload/removal cleanup and periodic reconciliation.

**Risk of changing:** Medium — incorrect indexing can leave AI permanently suspended or exclude modded mobs.

### AUDIT-PERF-006 — Weather publication rebuilds large view collections

**Severity:** Medium<br>
**Confidence:** Medium<br>
**Category:** Suspected server allocations / Networking preparation<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/weather/WeatherAuthority.java:619-642`<br>
**System:** Weather grid publication

**What happens**

Each simulation publication copies retained grid views into a new map and constructs new sets/lists. Simulation defaults to every 60 ticks, and configured retained-cell limits can be much larger than the default 4,096.

**Evidence**

The publication path allocates whole-view collections rather than only changed cells. Player overlap is already shared rather than simulated per player, which is a positive design choice.

**Why it matters**

At large retained-grid limits, periodic copy bursts can create GC/frame-time pressure and delay subsequent server work. This is not a demonstrated bottleneck.

**Scaling**

`dimensions × retained weather cells per publication`.

**Recommended fix**

Capture allocation profiles before changing it. If confirmed, use immutable retained snapshots, copy-on-write changed regions, or bounded neighborhood publication while preserving a single server authority.

**Risk of changing:** High — weather clients and persistence rely on coherent snapshots.

### AUDIT-PERF-007 — New shoreline regions cause bathymetry scan bursts

**Severity:** Medium<br>
**Confidence:** Medium<br>
**Category:** Suspected server TPS / Block scanning<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/watersystem/water/shoreline/ShorelineWaterManager.java:199-223`<br>
**System:** Shoreline shallow-water simulation

**What happens**

The manager updates at most six of up to 18 regions per level each tick, which is a useful bound. Newly created or expired regions refresh a 33×33 bathymetry grid, sampling up to about 11 vertical block states per cell.

**Evidence**

A single refresh is roughly 12,000 block reads; six new regions in one tick can approach 72,000 loaded-world reads before simulation work.

**Why it matters**

Rapid player movement or first activation can bunch refresh costs into one tick. Loaded-only behavior and region caps limit the blast radius, so this is a suspected spike rather than a release blocker.

**Scaling**

`new/expired regions per tick × 1,089 cells × vertical samples`.

**Recommended fix**

Stagger initial bathymetry construction through the existing per-tick budget, cache by lifecycle-owned region with invalidation, and profile fast travel/coastline cases.

**Risk of changing:** Medium — stale bathymetry can produce visibly or physically incorrect water.

### AUDIT-ARCH-001 — Runtime budget and worker ownership is duplicated

**Severity:** Medium<br>
**Confidence:** High<br>
**Category:** Architecture / Performance infrastructure<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/server/ServerLifecycleEvents.java:42-85`; `async/AsyncTaskManager.java`; `performance/**`; `dataengine/**`<br>
**System:** Async, Performance engine, Data Engine

**What happens**

Server start initializes the established async manager, the new Performance engine, Tick Engine, and Data Engine. The Performance engine owns another compute pool and several opt-in scheduling/throttling abstractions, but production feature callers do not yet use key helpers such as `AdaptiveBlockEntityTicker`, `AdaptiveEntityWork`, or `ElapsedTimeSimulation`. Data Engine instead reuses the established executor with bounded rejection.

**Evidence**

Reference searches find those helpers only in their declarations/tests, while the Performance server event still starts and ticks the framework by default. Project documentation states default subsystem registrations do not automatically alter feature systems.

**Why it matters**

Idle framework cost may be small, but duplicated worker/budget authorities complicate overload behavior, configuration, metrics, and shutdown. Activating both independently later can oversubscribe CPUs and defeat global backpressure.

**Scaling**

`executor pools + registered schedulers + future feature integrations`; practical cost depends on adoption.

**Recommended fix**

Keep the new work, but make one component the authoritative budget/executor layer. Leave opt-in infrastructure off by default until a production subsystem uses it, reuse the safe non-blocking submission route, and integrate one system at a time with benchmarks.

**Risk of changing:** Medium — this is current uncommitted user work and may be intentionally staged; do not delete it as dead code.

### AUDIT-VIS-001 — Two Rift entity renderers still use placeholder textures

**Severity:** Medium<br>
**Confidence:** High<br>
**Category:** Incomplete feature / Client visuals<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/entity/client/RiftMawRenderer.java`; `src/main/java/com/thunder/wildernessodysseyapi/entity/client/RiftListenerRenderer.java`<br>
**System:** Rift entities

**What happens**

Both renderers explicitly select constants named `PLACEHOLDER_TEXTURE` rather than final entity-specific assets.

**Evidence**

The placeholder markers are in reachable registered renderer classes, not comments in an abandoned helper.

**Why it matters**

This is a visible unfinished release surface. It is not a crash or performance issue, but it lowers player-facing quality and can hide missing animation/UV validation.

**Scaling**

Every rendered instance of the affected entities.

**Recommended fix**

Add final namespaced textures/models, verify missing-texture logs and resource reload, then capture client screenshots across lighting and animation states.

**Risk of changing:** Low.

### AUDIT-LIFE-002 — Meteor cadence is global static state

**Severity:** Medium<br>
**Confidence:** High<br>
**Category:** Lifecycle / Cross-world correctness<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/meteor/event/MeteorImpactEvent.java:28,78-121`<br>
**System:** Natural meteor scheduler

**What happens**

`lastCheckTime` is static and shared across servers/worlds, with no stop reset. The code handles game time moving backwards, but a new world with a later time can inherit the previous world's cadence anchor.

**Evidence**

Only the Overworld path updates the field; no lifecycle owner or SavedData stores it.

**Why it matters**

Integrated-server world changes and config toggles can delay or accelerate the next meteor unexpectedly. It also makes restart behavior non-explicit.

**Scaling**

One global value; correctness across each world/server transition.

**Recommended fix**

Store cadence per server/level in lifecycle state or SavedData depending on intended persistence, and define config-disable/re-enable behavior.

**Risk of changing:** Low to Medium.

### AUDIT-CONF-001 — Meteor minimum and maximum values are not cross-validated

**Severity:** Low<br>
**Confidence:** High<br>
**Category:** Configuration<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/meteor/config/MeteorConfig.java`; `src/main/java/com/thunder/wildernessodysseyapi/meteor/event/MeteorImpactEvent.java`<br>
**System:** Meteor balancing

**What happens**

Minimum and maximum counts/radii are individually range-validated but not ordered. If minimum exceeds maximum, selection collapses to the minimum through a defensive `Math.max` expression rather than rejecting or normalizing the invalid configuration.

**Evidence**

The config validators do not compare the paired values.

**Why it matters**

Administrators receive surprising behavior instead of a readable configuration error.

**Scaling**

Configuration-time only.

**Recommended fix**

Normalize and warn at snapshot publication or validate the pair as one value object; document which value wins.

**Risk of changing:** Low.

### AUDIT-DEAD-001 — Legacy wave consumer path is disconnected

**Severity:** Low<br>
**Confidence:** High for `WaveVertexConsumer`; Medium for removing the whole legacy route<br>
**Category:** Dead / Obsolete rendering code<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/mixin/WaveRenderMixin.java:22-41`; `watersystem/water/render/WaveVertexConsumer.java`; `WaveAnimator.java`<br>
**System:** Legacy water waves

**What happens**

`WaveRenderMixin` claims it wraps the render `VertexConsumer` but only calls `WaveAnimator.updateIfNeeded()`. `WaveVertexConsumer` is referenced only by its own Javadoc and implementation; the active Gerstner/replacement-mesh stack performs current deformation.

**Evidence**

Repository-wide reference search found no construction of `WaveVertexConsumer`. `WaveAnimator` is reached only through the otherwise non-transforming mixin and the unused consumer.

**Why it matters**

The mixin still hooks every vanilla liquid tessellation and creates a false maintenance path. It also overlaps `GerstnerWaveRenderMixin` on the same method.

**Scaling**

One extra call per liquid tessellation; likely small, not measured.

**Recommended fix**

Confirm no external API promises or reflection depend on the classes, compare client visuals with the mixin disabled, then remove the disconnected consumer/mixin route while retaining any intentionally deprecated public fields through a compatibility period.

**Risk of changing:** Medium — live rendering and third-party API usage require verification.

### AUDIT-DEAD-002 — Meteor biome modifier and impact-zone data are orphaned

**Severity:** Low<br>
**Confidence:** High<br>
**Category:** Dead resources / Documentation drift<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/meteor/worldgen/MeteorBiomeModifier.java:5-18`; `src/main/resources/data/c/tags/worldgen/structure/impact_site.json`; `data/wildernessodysseyapi/tags/worldgen/biome/has_structure/impact_zone.json`<br>
**System:** Meteor worldgen

**What happens**

`MeteorBiomeModifier` is an unreferenced no-op logger that documents the wrong namespace/path. The impact-site tag optionally references nonexistent `wildernessodysseyapi:impact_zone`, and the biome tag has no code/data consumer. The actual meteor feature is a registered configured/placed feature with JSON biome injection.

**Evidence**

Reference search found only the class declaration. The active registrations are in `ModRegistries` and current biome-modifier JSON; no structure registry entry named `impact_zone` exists.

**Why it matters**

These files mislead future maintainers and pack authors about a second structure-based meteor path.

**Scaling**

No runtime cost beyond resource parsing/logging if manually called; maintenance cost only.

**Recommended fix**

After confirming no published datapack contract uses the optional tags, remove the no-op class and orphan data or replace them with explicit compatibility documentation pointing to the active configured feature.

**Risk of changing:** Medium — optional common tags can be external integration points.

### AUDIT-PERF-008 — Assigned players still enter the cryo spawn handler every tick

**Severity:** Low<br>
**Confidence:** High<br>
**Category:** Server tick / Event breadth<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/worldgen/spawn/PlayerSpawnHandler.java:29-54`<br>
**System:** Spawn bunker assignment

**What happens**

Every server player invokes `tryAssignSpawn` on every post-player tick forever. Assigned players return after one persistent-tag read; unassigned players with no discovered tubes also retry every tick.

**Evidence**

The class handles login and `PlayerTickEvent.Post`. The retry exists only to bridge delayed cryo-tube discovery.

**Why it matters**

The assigned fast path is cheap, but it is unnecessary per-player event traffic at 20 Hz, and the unassigned retry frequency is much higher than the discovery requirement.

**Scaling**

`players × 20 calls/sec`.

**Recommended fix**

Track only pending players, retry on bunker discovery and at a low bounded interval, and clear pending state on logout/assignment.

**Risk of changing:** Low — test players joining before and after bunker placement.

### AUDIT-LIFE-003 — Small static client/server sets lack explicit stop cleanup

**Severity:** Low<br>
**Confidence:** High<br>
**Category:** Lifecycle / UX<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/worldversion/client/WorldVersionChecker.java`; `src/main/java/com/thunder/wildernessodysseyapi/server/PartnerAdHandler.java`<br>
**System:** Version notices and partner advertisement

**What happens**

`WorldVersionChecker` retains notified world identities, while `PartnerAdHandler` retains opt-out UUIDs and pending state without an explicit process/server cleanup contract. The partner handler's “hourly” commentary does not match its delayed-login behavior.

**Evidence**

The static collections have no server-stop clear path. They are bounded by worlds/players encountered rather than an infinite per-tick append.

**Why it matters**

Long desktop sessions can retain stale identities and produce confusing cross-server UI behavior. The commercial link also lacks a server-wide disable in the inspected path.

**Scaling**

`worlds and unique players encountered per process`; expected memory impact is small.

**Recommended fix**

Define client versus server ownership, clear per-server state on disconnect/stop, align comments with behavior, and offer an administrator-level disable if this is intended for modpack distribution.

**Risk of changing:** Low.

### AUDIT-PERF-009 — GPU diagnostic hooks wrap every instrumented draw

**Severity:** Low<br>
**Confidence:** Medium<br>
**Category:** Suspected client FPS / Diagnostics<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/mixin/RenderSystemGpuDiagnosticsMixin.java:17-34`; `GlStateManagerGpuProfilerMixin.java`; `TextureAtlasGpuProfilerMixin.java`; `SimpleTextureGpuProfilerMixin.java`<br>
**System:** GPU diagnostics

**What happens**

HEAD/RETURN hooks surround each `RenderSystem.drawElements` call, with additional hooks on allocation/deletion/upload APIs. The profiler has inactive fast paths, sampling, and caps.

**Evidence**

The draw hook performs two Java calls and an active-state read per instrumented draw even when diagnostics are inactive.

**Why it matters**

The inactive cost is probably small, but draw-call instrumentation belongs in a frame profiler measurement because high-end modpacks can issue many draw calls. No FPS/frame-time regression was measured.

**Scaling**

`render draw calls per frame × two hook calls`.

**Recommended fix**

Keep the diagnostics, measure inactive and active frame time, and consider selecting the mixin only when diagnostics are enabled at startup if the inactive overhead is measurable.

**Risk of changing:** Low.

## Duplicate Systems

The following overlaps are materially relevant. They are not all defects:

| Systems | Overlap | Assessment | Preferred direction | Consolidation risk |
| --- | --- | --- | --- | --- |
| `WaveRenderMixin` / `WaveAnimator` / `WaveVertexConsumer`; `GerstnerWaveRenderMixin`; replacement water meshes | Three generations of water-surface/render integration; two target `LiquidBlockRenderer#tesselate` | The legacy consumer is disconnected ([AUDIT-DEAD-001](#audit-dead-001--legacy-wave-consumer-path-is-disconnected)). Gerstner and replacement meshes still require live visual comparison before any consolidation. | Treat the replacement mesh/canonical renderer as the active authority; retire only proven no-op legacy hooks. | High: shader/modded-renderer compatibility and public render API may be involved. |
| `ClientLevelLocalizedRainMixin` and `LevelLocalizedRainMixin` | Both inject `Level#isRainingAt` at HEAD | Deliberate side partition: one only acts for `ClientLevel` with a client snapshot; the other only for `ServerLevel` with server authority. Both are necessary, but same-target ordering should be tested. | Keep side-specific ownership; document it as one client/server pair and add mixin tests. | High if merged carelessly due dedicated-server classloading. |
| `ClientLevelLocalizedWeatherMixin` and `ClientLevelWeatherColorMixin` | Both affect sky/cloud calculations | Complementary: one substitutes rain/thunder inputs; the other applies Echo/Riftfall tint to returned colors. Order is observable. | Keep separate responsibilities but test combined output and other sky mods. | Medium to High. |
| `BucketItemWaterloggingMixin`, `BucketPlaceMixin`, `CanonicalWaterBucketPickupMixin`, and `BucketableWaterBucketMixin` | Multiple hooks cover bucket placement, waterlogging, pickup, and bucketable mobs | Functional overlap is mostly intentional coverage of distinct vanilla routes. Multiple redirects/injections on `BucketItem` increase conflict risk. | Build a shared policy layer while leaving narrow route adapters. Do not delete a route without scenario tests. | High. |
| `AsyncTaskManager`, Performance engine compute/scheduler infrastructure, and Data Engine work queues | Multiple workload/budget/executor abstractions | Real authority overlap; production adoption is uneven ([AUDIT-ARCH-001](#audit-arch-001--runtime-budget-and-worker-ownership-is-duplicated)). | One global execution/backpressure authority; feature-specific queues may remain bounded adapters. | Medium. |
| Weather server authority, client coordinator, and vanilla-facing mixins | Several views of the same weather state | This is not duplicate simulation. The server is authoritative, networking publishes bounded views, and client/render layers consume them. | Preserve this boundary; improve naming/docs rather than collapsing it. | Critical if authority is blurred. |
| Canonical water, temporary shoreline flooding, SPH, compatibility projection, and renderer | Several water representations | Mostly distinct roles with explicit services. The confirmed SPH queue issue is an ownership violation, not evidence that the entire system should be replaced. | Keep canonical persistence authoritative and make transient/simulation/render ownership explicit. | Critical. |

No duplicate registry entry or duplicate event-bus registration was proven. The entrypoint combines annotation subscribers and explicit runtime registration, but the inspected classes did not show the same handler being registered twice.

## Incomplete Features

### Definitely incomplete

- [AUDIT-VIS-001](#audit-vis-001--two-rift-entity-renderers-still-use-placeholder-textures): two reachable renderers use explicit placeholder assets.
- [AUDIT-ARCH-001](#audit-arch-001--runtime-budget-and-worker-ownership-is-duplicated): major Performance engine helpers are implemented and tested but are not yet adopted by production block entities/entities/background systems. This is a staged subsystem, not safe deletion material.

### Probably incomplete

- [AUDIT-DEAD-002](#audit-dead-002--meteor-biome-modifier-and-impact-zone-data-are-orphaned): comments describe a nonexistent `meteormod` path and a fallback that never registers.

### Potentially intentional placeholders

- Voice integration is intentionally an offline/no-service stub and should not be treated as a broken network feature without a product requirement.
- Deprecated Gerstner/water API surfaces may exist for external compatibility even when internal callers moved. Retain them until an API compatibility scan and deprecation window.
- `ElapsedTimeSimulation`, `AdaptiveBlockEntityTicker`, and `AdaptiveEntityWork` have no production callers, but current documentation and tests identify them as opt-in framework contracts. Label them “not yet integrated,” not dead.

No missing packet handler was found among registered payloads. Server-bound Codex, weather/water debug, and Data Engine routes have receivers and direction-appropriate registration in the inspected payload registrar.

## Potential Bugs

Confirmed correctness defects are [AUDIT-CONC-001](#audit-conc-001--client-tick-can-drain-server-sph-settlement-work), [AUDIT-COMPAT-002](#audit-compat-002--worldedit-entity-copying-is-enabled-for-unrelated-pastes), [AUDIT-MIG-001](#audit-mig-001--world-upgrade-progress-is-committed-before-migration-completes), [AUDIT-RES-001](#audit-res-001--recipes-and-vanilla-tags-use-pre-121-directory-names), [AUDIT-LIFE-001](#audit-life-001--riftfall-static-state-survives-world-and-server-lifecycles), and [AUDIT-LIFE-002](#audit-life-002--meteor-cadence-is-global-static-state).

Suspected/runtime-dependent bugs are:

- without-WorldEdit startup failure from [AUDIT-MIX-001](#audit-mix-001--required-worldedit-mixin-is-not-optional-or-declared-required), pending an actual no-WorldEdit launch;
- metadata loader rejection or wrong optional-dependency behavior from [AUDIT-META-001](#audit-meta-001--generated-mod-metadata-is-not-validly-constrained-for-neoforge-1211), pending startup on the intended NeoForge distribution;
- async config reload cancellation/race from [AUDIT-CONC-003](#audit-conc-003--async-config-reload-can-race-and-drop-work), pending a reload-under-load test;
- off-thread player access from [AUDIT-TEL-003](#audit-tel-003--telemetry-reads-live-player-state-off-thread-and-can-block-logout), whose exact failure mode depends on the invoked fields/services.

No evidence was found of common/server code directly initializing the ordinary client renderer registry on a dedicated server. Client subscribers and client-only classes are generally separated. The SPH issue is logical-side/thread ownership through a common singleton, which static package separation alone does not prevent.

## Performance Findings

### Server/TPS

Highest priority:

1. [AUDIT-PERF-001](#audit-perf-001--riftfall-counts-entities-through-a-world-sized-aabb) — recurring global spatial query; fix before profiling.
2. [AUDIT-PERF-002](#audit-perf-002--meteor-landing-search-can-perform-millions-of-synchronous-block-reads) — large synchronous search and possible chunk loads; fix before profiling.
3. [AUDIT-PERF-003](#audit-perf-003--expanded-structure-block-operations-can-freeze-a-server) — operator-triggered but exceptionally high worst-case volume.
4. [AUDIT-CONC-002](#audit-conc-002--async-overload-fallback-runs-io-on-the-calling-thread) — overload turns external work into main-thread work.

Profile next:

- [AUDIT-PERF-004](#audit-perf-004--every-server-block-mutation-enters-water-reconciliation), because it sits on a global block-mutation hook;
- [AUDIT-PERF-005](#audit-perf-005--ecosystem-updates-enumerate-all-loaded-entities), especially with mob-heavy mods and chunk loaders;
- [AUDIT-PERF-006](#audit-perf-006--weather-publication-rebuilds-large-view-collections), focusing on allocation rate/GC;
- [AUDIT-PERF-007](#audit-perf-007--new-shoreline-regions-cause-bathymetry-scan-bursts), focusing on fast travel/first activation;
- [AUDIT-PERF-008](#audit-perf-008--assigned-players-still-enter-the-cryo-spawn-handler-every-tick), a low-risk frequency reduction.

Positive bounds include staggered environment sync, once-per-second radiation work, an internal two-second lore scan interval, capped shoreline regions, loaded-only shoreline sampling, bounded Data Engine queues, and weather simulation shared across overlapping players.

### Client/FPS

- [AUDIT-PERF-009](#audit-perf-009--gpu-diagnostic-hooks-wrap-every-instrumented-draw) is a measurement candidate, not a proven regression.
- The water renderer has multiple compatibility handoffs and tessellation hooks. Replacement mesh rebuilds are budgeted and VBOs are retained/reused, which is preferable to per-frame resource recreation.
- `ClientLevelChunkWaterMeshMixin` marks affected meshes dirty on chunk block-state change. Validate rebuild coalescing during fluid-heavy updates.
- `LevelRendererLocalizedWeatherMixin` wraps clouds, rain rendering, and rain ticking; the client weather coordinator prevents independent per-player simulation, but particle/cloud cost still needs representative weather captures.
- No unbounded client world/entity scan was identified in the main render-frame paths.

Recommended measurement set: RenderDoc or built-in frame profiling for water-heavy shoreline, Sodium and Embeddium separately, Iris modern and legacy target matrices, active/inactive GPU diagnostics, maximum local rain/hail, and high GUI scale Codex screens.

### Memory

- [AUDIT-LIFE-001](#audit-life-001--riftfall-static-state-survives-world-and-server-lifecycles) and [AUDIT-TEL-001](#audit-tel-001--telemetry-retains-server-and-player-lifecycle-state) retain world/server/player identities.
- [AUDIT-LIFE-003](#audit-life-003--small-static-clientserver-sets-lack-explicit-stop-cleanup) is a smaller process-lifetime accumulation.
- Weather grids are bounded by configuration; defaults are reasonable, but the permitted maximum can make full-view copies large.
- Water/weather synchronization has explicit count/radius limits and did not expose an unbounded client-driven collection.
- No confirmed unreleased VBO/texture ownership leak was found statically. Live resource-reload/disconnect testing remains required.

### Networking

Positive findings:

- Data packet batches cap entry count and total encoded body size.
- Codex journal text is sanitized and size-bounded server-side.
- Water, watershed, and weather snapshots have explicit region/cell bounds; the regular sea-water handoff is a bounded 9×9 area rather than a whole-world dump.
- Debug/control payloads require appropriate server permissions in the inspected handlers.

Risks:

- the structure-block packet mixin appends custom fields to a vanilla packet and requires an exactly matched client/server mod version; see the mixin table;
- telemetry uses external HTTP and shared queues, so overload policy—not packet size—is the main network-related server risk;
- cloak activation is server-authoritative and cooldown-limited, but an explicit packet rate limiter would add defense in depth.

### Worldgen

- [AUDIT-PERF-002](#audit-perf-002--meteor-landing-search-can-perform-millions-of-synchronous-block-reads) can cross into chunk generation.
- `NoiseBasedChunkGeneratorWaterMixin` and `ProtoChunkWaterMixin` modify hot generation writes. Their behavior is central to canonical-water representation and should be compatibility-tested against alternate generators, not removed for speed without evidence.
- [AUDIT-MIG-001](#audit-mig-001--world-upgrade-progress-is-committed-before-migration-completes) scans loaded legacy chunks and must be resumable/idempotent.
- StructureGen's source JSON → validation → generated NBT → semantic re-read route is well designed. Preserve `src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt` byte-for-byte as its regression fixture.

### Entity/AI

- [AUDIT-PERF-001](#audit-perf-001--riftfall-counts-entities-through-a-world-sized-aabb) is the dominant entity risk.
- [AUDIT-PERF-005](#audit-perf-005--ecosystem-updates-enumerate-all-loaded-entities) repeats a full loaded-entity enumeration.
- Water-parity mixins alter entity wetness, eye-fluid, navigation, fishing, aquatic spawns, visibility, and boats. These are cohesive but have broad compatibility impact on modded entities and AI.
- The ecosystem profile fallback is preferable to hardcoding only vanilla mobs, but profile resolution/cache behavior should be measured with a large mod registry.

### Rendering

- [AUDIT-DEAD-001](#audit-dead-001--legacy-wave-consumer-path-is-disconnected) is obsolete hot-path surface.
- [AUDIT-VIS-001](#audit-vis-001--two-rift-entity-renderers-still-use-placeholder-textures) is player-visible incompleteness.
- [AUDIT-PERF-009](#audit-perf-009--gpu-diagnostic-hooks-wrap-every-instrumented-draw) should be benchmarked.
- Sodium, Embeddium, and Iris paths are conditionally selected, a strong design. Their reliance on private external renderer internals still requires exact-version smoke tests.

## Tick Hotspots

| Repeating system | Trigger / default cadence | Main work and scaling | Bounds / gating | Risk |
| --- | --- | --- | --- | --- |
| Riftfall | Server lifecycle tick; spawn phases every 80/360/520 ticks | Three world-sized entity queries plus spawning | Entity caps exist, query geometry is not safe | **Critical** |
| SPH server/client managers | Server and client ticks | Advance simulation, drain settlements, render handoff | Budgets exist; queue lacks side/level ownership | **Critical correctness** |
| Meteor impact | Level tick with configured cadence; command-triggerable | Candidate search, vertical block scan, crater/feature work | Attempts/counts bounded, but bound is too large and chunks are not rejected | **Critical spike** |
| World upgrade | Server tick while running | One queued chunk, then section/block migration | One chunk/tick; progress is not durably resumable | **High correctness / spike** |
| Ecosystem | Player/manager regional updates, default 40 ticks | Enumerates all loaded entities and updates regional AI policy | Regional limits; global enumeration remains | **Medium suspected** |
| Water projection reconciliation | Every server `Level#setBlock` | Activity/chunk/state checks and canonical reconciliation | Feature/activity guards | **Medium suspected hot hook** |
| Shoreline water | Level/server tick; up to 6 region updates | 33×33 simulation; periodic bathymetry scans | 18 regions/level, loaded-only | **Medium suspected burst** |
| Weather authority | Server tick, simulation default 60 ticks | Grid advance, publication copies, bounded sync | Retained-cell and payload limits | **Medium allocation candidate** |
| Telemetry queue processor | Server tick, periodic dispatch | Queue acquisition, HTTP/persist/retry | Feature flags and queue cap; lifecycle/write issues remain | **Medium** |
| Async main callbacks | Server tick | Drains worker completions | Queue/pool bounds; reload/caller-run issues | **High under overload** |
| Environment sync | Server tick | One staggered player phase per tick | Each player approximately once/sec; cleanup present | Low |
| Radiation | Player tick with internal interval | Exposure/state update | Approximately once/sec/player | Low |
| Lore book | Player tick with internal 2-second gate | Scans ~41 inventory slots | Hard interval and small inventory | Low |
| Cryo spawn assignment | Every server player post tick | Persistent tag check; possible teleport | Cheap fast path, unnecessary 20 Hz | Low |
| Shore-wave spawning | Level tick, about every 160 ticks | Small loaded-only samples and cooldown maintenance | Cooldowns and samples bounded | Low |
| Client water/weather | Client tick + render | State interpolation, mesh invalidation/draw, particles/clouds | Renderer budgets and bounded snapshots | Medium; profile visually |
| GPU diagnostics | Every selected GL allocation and draw | Counters/timestamps when active | Inactive fast path; active sampling/caps | Low suspected |

Other repeating subscribers found include distant wildlife, radiation, partner advertising, temporal-rift events, reactive vegetation, tides, registered wilderness fluids, cloak state/input/rendering, donation/loading/codex clients, impact music, and debug overlays. None displaced the listed top hotspots after caller and internal-cadence inspection.

Periodic executors/workers include `AsyncTaskManager` CPU and I/O pools, Performance engine compute/background components, Data Engine queues using the shared executor, telemetry dispatch/persistence, SPH computation, and client render work. Shutdown exists for the principal managers, but telemetry and some static feature state are not fully lifecycle-owned.

## Event-System Audit

- The mod uses annotation-based `@EventBusSubscriber` handlers plus explicit registration from the entrypoint. No proven duplicate registration of the same object/listener was found.
- Several broad events do internal gating correctly: lore scans every two seconds despite a player-tick subscription; radiation is interval-limited; environment synchronization staggers players.
- `PlayerSpawnHandler` is broader than necessary ([AUDIT-PERF-008](#audit-perf-008--assigned-players-still-enter-the-cryo-spawn-handler-every-tick)).
- `TelemetryQueueProcessor` still enters queue processing while specific reporters are disabled; make the master/specific flags stop work consistently ([AUDIT-TEL-001](#audit-tel-001--telemetry-retains-server-and-player-lifecycle-state)).
- Server lifecycle ordering is important: async/background/tick/data systems start together, then server tick drains async callbacks and invokes Data Engine/Riftfall. Any future reordering must retain server-thread mutation and shutdown-before-world-disposal guarantees.
- Water/weather client events and common events are side-checked; the SPH singleton is the important exception.

## Mixin Risks

The configuration is `required=true` with `defaultRequire=1`. No `@Overwrite` was found. That is preferable, but redirects, operation wrappers, private external targets, exact invocation counts, and packet extensions remain version- and mod-sensitive.

### Common/server mixins

| Mixin | Target and injection point | Reason / invasiveness | Compatibility risk |
| --- | --- | --- | --- |
| `BlockEntityMixin` | `BlockEntity#setLevel/setRemoved/clearRemoved`, TAIL | Synchronizes structure-corner cache lifecycle; globally hot lifecycle hook but narrow body | Medium |
| `BubbleColumnWaterMixin` | `BubbleColumnBlock#canExistIn`, RETURN cancellable | Accepts tagged/canonical water host | Low |
| `BucketableWaterBucketMixin` | `Bucketable#bucketMobPickup`, wraps `ItemStack#getItem` | Preserves the correct bucket for canonical water | Medium: exact invocation |
| `BucketItemWaterloggingMixin` | `BucketItem#canBlockContainFluid` and `emptyContents`, three operation wrappers | Extends waterlogging/container paths | High: two exact invocation counts and overlaps `BucketPlaceMixin` |
| `BucketPlaceMixin` | `BucketItem#emptyContents`, HEAD cancellable and RETURN | Transfers canonical volume and handles placement outcome | High: central gameplay path |
| `CanonicalWaterBucketPickupMixin` | `LiquidBlock#pickupBlock`, HEAD cancellable | Server-authoritative canonical pickup | High: replaces result |
| `CanonicalWaterFlowMixin` | `FlowingFluid#tick`, HEAD cancellable | Stops vanilla propagation only for replacement-owned cells | High: fluid-tick hot path |
| `CanonicalWaterProjectionGuardMixin` | `CanonicalWater#projectCompatibility`, redirects two `ServerLevel#setBlock` calls | Marks internal projection writes and prevents recursive reconciliation | Medium/High: exact call count |
| `ChunkMapGenerationErrorMixin` | `ChunkMap#applyStep`, RETURN cancellable | Decorates failed generation futures with diagnostics | Medium: central generation future |
| `CoralFeatureWaterMixin` | `CoralFeature#placeCoralBlock`, redirects block predicate | Allows canonical/tagged water in coral placement | Medium |
| `CreatePathsMixin` | `CreatePaths#<clinit>`, TAIL | Globally replaces schematic directories | **High**; confirmed harmful overlap |
| `CreateWaterPredicateMixin` | `FluidHelper#isWater`, HEAD cancellable, remap false | Teaches Create the canonical/tagged water policy | Medium: external API signature |
| `DayNightCycleMixin` | `ServerLevel#getDayTimePerTick`, HEAD cancellable | Applies TickTok-driven custom day duration | High: global time ownership and time-mod overlap |
| `EntityAccessor` | `Entity.level` accessor | Internal level access | Low |
| `EntityWaterParityMixin` | `Entity#isInWater/isEyeInFluid/getEyeInFluidType`, RETURN cancellable | Aligns movement/eye fluid semantics with animated surface | High: all entities and modded fluid logic |
| `FishingHookWaterMixin` | `FishingHook#catchingFish`, two redirected water predicates | Makes fishing recognize canonical water | Medium: exact call count |
| `FlyNodeEvaluatorWaterMixin` | `FlyNodeEvaluator#getStart`, redirected water predicate | Navigation parity | Medium |
| `ForwardExtentCopyMixin` | WorldEdit `ForwardExtentCopy#resume`, HEAD | Enables entity copying for Create contraptions | **Critical** absent-target risk; High behavioral risk |
| `GlowSquidSpawnMixin` | `GlowSquid#checkGlowSquidSpawnRules`, redirected water predicate | Spawn parity | Medium |
| `GroundPathNavigationWaterMixin` | `GroundPathNavigation#getSurfaceY`, redirected predicate | Surface/navigation parity | Medium |
| `LevelLocalizedRainMixin` | `Level#isRainingAt`, HEAD cancellable; acts only on `ServerLevel` | Server-authoritative localized rain for vanilla gameplay | High: shared vanilla weather choke point |
| `LivingEntityMixin` | `LivingEntity#getVisibilityPercent`, RETURN cancellable | Adjusts visibility under custom conditions | Medium: affects AI targeting semantics |
| `MixinWeatherCommand` | `WeatherCommand#setClear/setRain/setThunder`, wraps `setWeatherParameters`; `register` TAIL | Routes vanilla commands to weather authority and adds meteor command | High: command-tree and weather-mod overlap |
| `NaturalAquaticFeatureWaterMixin` | Kelp, seagrass, sea-pickle `place`, redirected water predicate | Worldgen parity for aquatic features | Medium |
| `NoiseBasedChunkGeneratorWaterMixin` | `NoiseBasedChunkGenerator#doFill`, wraps `LevelChunkSection#setBlockState` | Canonicalizes generated water at the write boundary | High: hot worldgen/private call |
| `ProtoChunkWaterMixin` | `ProtoChunk#setBlockState`, argument modify at HEAD and RETURN injection | Canonicalizes generation writes and records projection | High: all proto-chunk writes |
| `ServerboundSetStructureBlockPacketMixin` | packet `write` TAIL and decode constructor TAIL | Appends expanded structure-block fields | High: wire-format/client-server version coupling |
| `ServerGamePacketListenerImplMixin` | `handleSetStructureBlock`, HEAD and RETURN | Installs/clears extended packet context | Medium/High: server packet path and cleanup ordering |
| `ServerLevelLightningAccessor` | invokes private `findLightningTargetAround` | Reuses vanilla final lightning targeting | Low/Medium: mapped private method |
| `ServerLevelLocalizedLightningMixin` | `ServerLevel#tickChunk`, wraps `isThundering` | Applies localized storm eligibility to natural lightning | High: central weather tick |
| `ServerLevelLocalizedPrecipitationMixin` | `tickPrecipitation`, wraps `isRaining`, `Biome#shouldSnow`, and `getPrecipitationAt` | Local rain/snow/hail gameplay | High: three weather decision points |
| `SodiumChunkBuildOutputHandoffMixin` | Sodium `ChunkBuildOutput` interface bridge | Associates generated-water mesh output | Medium: external private implementation |
| `SodiumChunkMeshingHandoffMixin` | Sodium `ChunkBuilderMeshingTask#<init>/execute`, RETURN and HEAD/RETURN | Captures section context and mesh-build lifecycle | High: external meshing hot path |
| `SodiumRenderSectionCoordinatesMixin` | Sodium `RenderSection` interface bridge | Exposes section coordinates | Medium |
| `SodiumSectionUploadHandoffMixin` | Sodium `RenderSectionManager#uploadChunks`, injection around upload | Transfers water mesh to GPU-side section owner | High: external upload internals |
| `SpringFeatureWaterMixin` | `SpringFeature#place`, redirected configured-state reads | Canonicalizes spring water | Medium: two exact invocations |
| `StructureBlockEntityMixin` | `loadAdditional` clamp redirect; `detectSize` HEAD cancellable; save HEAD/RETURN; mode/name/init tails | Expanded 512 structures, detection, scan, file rewrite | **Critical** invasiveness/performance/compatibility |
| `StructureTemplateAccessor` | `StructureTemplate` palettes/entities/size accessors | Structure processing internals | Medium: private fields |
| `StructureTemplatePaletteAccessor` | `StructureTemplate.Palette#<init>` invoker | Rebuilds palettes after marker processing | Medium |
| `StructureTemplateWaterMarkerMixin` | `StructureTemplate#processBlockInfos`, RETURN cancellable | Converts structure markers/canonical water data | Medium/High: structure placement result |
| `SurfaceWaterAnimalSpawnMixin` | `WaterAnimal#checkSurfaceWaterAnimalSpawnRules`, redirected predicate | Modded surface-water spawn parity | Medium |
| `TropicalFishSpawnMixin` | `TropicalFish#checkTropicalFishSpawnRules`, redirected predicate | Spawn parity | Medium |
| `WalkNodeEvaluatorWaterMixin` | `WalkNodeEvaluator#getStart`, redirected predicate | Ground pathfinding parity | Medium |
| `WaterProjectionMutationMixin` | `Level#setBlock`, HEAD | Reconciles external world mutations with canonical water | High: global block-mutation hot path |

### Client mixins

| Mixin | Target and injection point | Reason / invasiveness | Compatibility risk |
| --- | --- | --- | --- |
| `BoatRenderMixin` | `BoatRenderer#render`, injection before pose rotation | Adds local-water boat tilt | Medium/High: renderer local/order assumptions |
| `ClientLevelChunkWaterMeshMixin` | `LevelChunk#setBlockState`, RETURN | Marks replacement water mesh dirty | Medium: hot client update path |
| `ClientLevelLocalizedRainMixin` | `Level#isRainingAt`, HEAD cancellable; acts only on `ClientLevel` | Client prediction/visual rain parity | High: same target as server rain mixin |
| `ClientLevelLocalizedWeatherMixin` | `ClientLevel#getSkyDarken/getSkyColor/getCloudColor`, redirects rain/thunder reads | Local sky/cloud darkening | High: three renderer calculations and redirect conflicts |
| `ClientLevelWeatherColorMixin` | `ClientLevel#getSkyColor/getCloudColor`, RETURN cancellable | Echo and Riftfall tint | Medium/High: composes after other color mods |
| `DebugScreenOverlayAccessor` | debug overlay block/liquid hit fields | Extends debug display | Low |
| `GerstnerWaveRenderMixin` | `LiquidBlockRenderer#tesselate` HEAD/TAIL, argument `ModifyVariable`, overlay redirect | Vanilla-path wave deformation and state | High: hot method, argument/ordinal assumptions, overlaps other liquid mixins |
| `GlStateManagerGpuProfilerMixin` | texture/buffer/renderbuffer allocate/delete methods, HEAD | GPU allocation diagnostics | Medium: low inactive work but native/mapped API surface |
| `IrisWaterMaterialBridgeMixin` | modern Iris `WorldRenderingSettings#setBlockStateIds` overloads, RETURN, require 0 | Publishes water material IDs | Medium: external private API; plugin-guarded |
| `LegacyIrisWaterMaterialBridgeMixin` | legacy Iris `BlockRenderingSettings#setBlockStateIds` overloads, RETURN, require 0 | Legacy shader material bridge | Medium: external private API; plugin-guarded |
| `LevelRendererLocalizedWeatherMixin` | `LevelRenderer#renderClouds/renderSnowAndRain/tickRain` operation wrappers | Replaces global weather rendering with local view | High: major renderer/weather compatibility surface |
| `RenderChunkRegionAccessor` | `RenderChunkRegion.level` accessor | Gives mesh compiler the client level | Low/Medium |
| `RenderSystemGpuDiagnosticsMixin` | `RenderSystem#drawElements` HEAD/RETURN | Bounded GPU draw timing | Low/Medium: every draw |
| `SimpleTextureGpuProfilerMixin` | texture preparation call redirect | Tracks simple-texture allocations | Medium: exact mapped call |
| `SodiumBlockOcclusionCacheMixin` | Sodium `BlockOcclusionCache#shouldDrawSide` operation wrapper | Tagged-water face culling | Medium/High: external hot path |
| `SodiumFluidRenderMixin` | Sodium `DefaultFluidRenderer` fluid-height wrappers and occlusion HEAD | Canonical surface height/culling | High: external hot renderer internals and invocation count |
| `StructureBlockRendererMixin` | structure renderer `render` HEAD/TAIL and `getViewDistance` HEAD cancellable | Extended selection bounds/view distance | High: state cleanup and other admin-render mods |
| `TaggedWaterFaceCullingMixin` | `LiquidBlockRenderer#shouldRenderFace` overloads, HEAD, require 0 | Avoids internal faces for tagged water | Medium/High: two overloads and renderer hot path |
| `TextureAtlasGpuProfilerMixin` | atlas image preparation redirect and `upload` TAIL | Tracks atlas allocation/upload | Medium |
| `VanillaCompiledSectionHandoffMixin` | `CompiledSection#<init>` RETURN | Adds water-mesh ownership bridge | Medium |
| `VanillaSectionCompilerHandoffMixin` | `SectionCompiler#compile` HEAD/RETURN | Captures build context/output | High: vanilla chunk-meshing hot path |
| `VanillaSectionUploadHandoffMixin` | `RenderSection#setCompiled` TAIL | Uploads/transfers water mesh | High: renderer state lifecycle |
| `WaveRenderMixin` | `LiquidBlockRenderer#tesselate` HEAD | Updates legacy animator only | Low runtime / Medium cleanup; obsolete overlap |

### Dynamically selected Embeddium mixins

These five source mixins are not listed in JSON; `WildernessMixinConfigPlugin` selects them when the exact Embeddium target class exists.

| Mixin | Target and injection point | Reason / invasiveness | Compatibility risk |
| --- | --- | --- | --- |
| `EmbeddiumChunkBuildOutputHandoffMixin` | Embeddium `ChunkBuildOutput` interface bridge | Carries water mesh output | Medium |
| `EmbeddiumChunkMeshingHandoffMixin` | Embeddium `ChunkBuilderMeshingTask#<init>/execute`, lifecycle injections | Captures build context and hands off output | High |
| `EmbeddiumRenderSectionCoordinatesMixin` | Embeddium `RenderSection` interface bridge | Exposes section coordinates | Medium |
| `EmbeddiumSectionUploadHandoffMixin` | Embeddium `RenderSectionManager` upload path | Uploads/transfers water mesh | High |
| `EmbeddiumWaterRenderMixin` | Embeddium `FluidRenderer` height/occlusion operation wrappers and injection | Canonical surface height and face culling | High |

### Mixin conflict clusters requiring explicit matrices

1. **Liquid rendering:** `GerstnerWaveRenderMixin`, `WaveRenderMixin`, `TaggedWaterFaceCullingMixin`, Sodium/Embeddium fluid mixins, Iris bridges, and vanilla/Sodium/Embeddium handoff mixins.
2. **Bucket/fluid behavior:** two `BucketItem` mixins plus canonical flow, pickup, bubble, entity, pathfinding, spawning, and worldgen predicates.
3. **Localized weather:** two `Level#isRainingAt` mixins, three `ServerLevel` weather hooks, `WeatherCommand`, two `ClientLevel` color/input mixins, and `LevelRenderer`.
4. **Structure blocks:** entity, packet encode/decode, listener context, renderer, template accessors, and template marker processing.
5. **World generation:** `NoiseBasedChunkGenerator`, `ProtoChunk`, spring/coral/aquatic feature predicates, and generation-error decoration.

At minimum, run: vanilla renderer; Sodium; Embeddium; Iris+Sodium; Iris+Embeddium; Create present; WorldEdit absent/present; alternate world generator; localized weather mod; and structure/protection tool combinations.

## Modpack Compatibility

The following are the most likely “works alone, fails at 200–500 mods” paths:

- [AUDIT-PERF-001](#audit-perf-001--riftfall-counts-entities-through-a-world-sized-aabb) and [AUDIT-PERF-005](#audit-perf-005--ecosystem-updates-enumerate-all-loaded-entities) scale with modded entity volume/chunk loaders.
- [AUDIT-PERF-004](#audit-perf-004--every-server-block-mutation-enters-water-reconciliation) scales with every automation/worldgen mod's block mutation rate.
- [AUDIT-MIX-001](#audit-mix-001--required-worldedit-mixin-is-not-optional-or-declared-required) assumes an undeclared target exists.
- [AUDIT-COMPAT-001](#audit-compat-001--create-schematic-directories-are-globally-replaced) and [AUDIT-COMPAT-002](#audit-compat-002--worldedit-entity-copying-is-enabled-for-unrelated-pastes) globally change other mods' user-facing behavior.
- Renderer mixins depend on exact Sodium 6/Embeddium 5/Iris internal class layouts. Guards prevent absent-class loading but cannot prove method compatibility.
- Global weather/time command hooks can conflict with seasons, alternate time, localized weather, or dimension-environment mods. Ownership is explicit, but priority/fallback behavior must be documented.
- Worldgen hooks assume the inspected 1.21.1 generation write paths. Test Lithostitched, Biolith, TerraBlender, Regions Unexplored, and at least one alternate generator together.
- The water policy uses tags/services rather than only vanilla IDs, which is a compatibility strength. Preserve data-driven recognition for modded aquatic mobs and features.

## Dead/Obsolete Code

| Candidate | Classification | Evidence | Action |
| --- | --- | --- | --- |
| `WaveVertexConsumer` | **Confirmed disconnected internally** | No construction/reference outside itself and Javadoc | Client/API verification, then remove with legacy mixin; see AUDIT-DEAD-001 |
| `WaveAnimator` / `WaveRenderMixin` | **Very likely obsolete** | Only update call from a mixin that does not deform vertices; newer render stacks exist | Visual/API verification required |
| `MeteorBiomeModifier` | **Confirmed unreferenced no-op** | Declaration-only reference; active JSON path elsewhere | Remove after datapack compatibility review |
| impact-zone common/biome tags | **Very likely orphaned** | No registered `impact_zone` structure or consumer | Check published datapack contract, then remove/document |
| `BiomeUtils` | **Very likely dead internally** | Declaration-only reference; not registered/mixin/codec | Manual external API scan before removal |
| `LightweightMath` | **Very likely dead internally** | Declaration-only reference | Remove only after external API scan |
| `NbtDataCompactor` | **Very likely dead internally** | Declaration-only reference; no serialization hook | Do not wire it in casually: it performs lossy history pruning and custom format transforms |
| `ElapsedTimeSimulation` | **Possibly dead — manual verification required** | Tests plus declaration only; documented opt-in Performance contract | Keep while Performance engine is staged |
| `AdaptiveBlockEntityTicker` / `AdaptiveEntityWork` | **Possibly dead — manual verification required** | Declaration only in production; new framework APIs | Keep while integration plan is active |

No registered block, item, entity, payload, codec, event subscriber, or mixin was classified dead solely from Java references. Public classes may be API consumers' entry points, and JSON/resources can instantiate codecs and registry identifiers.

## Architecture Findings

### Simplified architecture map

    Mod entrypoint
    ├── Registries and configs
    │   ├── blocks/items/entities/effects/features
    │   ├── 20 config modules and immutable runtime snapshots
    │   └── payload registration
    ├── Server lifecycle/orchestration
    │   ├── AsyncTaskManager
    │   ├── Background + Tick Performance engines
    │   ├── Data Engine
    │   ├── world upgrade
    │   └── telemetry
    ├── World systems
    │   ├── canonical water + projection + SPH + shoreline + watershed
    │   ├── weather authority + local precipitation/lightning
    │   ├── ecosystem + mob control + distant wildlife
    │   ├── Riftfall + temporal rift
    │   ├── meteor configured feature + runtime impacts
    │   └── radiation / tides / vegetation
    ├── Worldgen and structures
    │   ├── registered features/biome modifiers
    │   ├── spawn bunker
    │   ├── StructureGen source-to-NBT pipeline
    │   └── expanded structure-block tooling
    ├── Networking
    │   ├── water/weather bounded state
    │   ├── Codex server-authorized mutations
    │   ├── cloak/gameplay state
    │   └── diagnostics/Data Engine
    ├── Client
    │   ├── water meshes/waves and renderer bridges
    │   ├── localized clouds/precipitation/sky
    │   ├── entity renderers and effects
    │   └── Codex, debug, donation/loading UI
    └── Compatibility mixins
        ├── Create and WorldEdit
        ├── Sodium / Embeddium / Iris
        ├── vanilla water/entity/worldgen
        └── weather, commands, packets, and structure blocks

### Ownership and coupling assessment

- **Good:** `WaterServices` and `WeatherServices` expose ownership instead of letting callers instantiate fake worlds/simulations.
- **Good:** authoritative simulation, network snapshot, and client rendering are separate layers.
- **Problem:** process-wide static maps/singletons sometimes carry per-server/per-level state ([AUDIT-CONC-001](#audit-conc-001--client-tick-can-drain-server-sph-settlement-work), [AUDIT-LIFE-001](#audit-life-001--riftfall-static-state-survives-world-and-server-lifecycles), [AUDIT-TEL-001](#audit-tel-001--telemetry-retains-server-and-player-lifecycle-state)).
- **Problem:** `StructureBlockEntityMixin` is a god-mixin combining bounds, UI/session state, detection, chunk warmup, scanning, persistence, and file post-processing.
- **Problem:** execution/backpressure authority is split ([AUDIT-ARCH-001](#audit-arch-001--runtime-budget-and-worker-ownership-is-duplicated)).
- **Problem:** global mutations of other mods' state create hidden side effects ([AUDIT-COMPAT-001](#audit-compat-001--create-schematic-directories-are-globally-replaced), [AUDIT-COMPAT-002](#audit-compat-002--worldedit-entity-copying-is-enabled-for-unrelated-pastes)).

Incremental direction: create lifecycle-owned server contexts, separate pure computation from server mutation, route all background admission through one bounded authority, and split the structure-block workflow behind a stable facade without changing packet/file formats in the first step.

## Performance Execution Map

    SERVER TICK
    ├── ServerLifecycleEvents
    │   ├── drain async main-thread callbacks
    │   ├── Data Engine bounded work
    │   └── Riftfall
    │       └── world-sized entity queries [critical]
    ├── telemetry processor
    ├── performance pre/post sampling
    ├── environment/weather/water synchronization
    ├── radiation / temporal rift / vegetation / wildlife
    └── world upgrade
        └── one chunk migration, block scan

    LEVEL TICK
    ├── meteor impact
    │   └── candidate grid × vertical block scan [critical]
    ├── shoreline waves and shoreline water
    ├── tides
    └── registered fluid work

    PLAYER TICK
    ├── cloak
    ├── ecosystem coordination
    ├── lore (internally every 2 seconds)
    └── cryo spawn retry (every tick)

    CLIENT TICK
    ├── SPH/water interpolation
    ├── localized weather/Riftfall visuals
    ├── cloak/input/effects/music
    ├── Codex/debug
    └── loading/donation/client-save state

    RENDER FRAME / CHUNK MESH
    ├── vanilla/Sodium/Embeddium water mesh handoff
    ├── Gerstner and face-culling hooks
    ├── Iris material bridge
    ├── localized clouds/precipitation/sky
    ├── cloak/entities/HUD
    └── GPU diagnostics

    BLOCK MUTATION
    └── Level#setBlock
        └── canonical-water reconciliation for every active server mutation

    WORLD GENERATION
    ├── NoiseBasedChunkGenerator write wrapper
    ├── ProtoChunk write canonicalization
    ├── spring/coral/aquatic feature predicates
    ├── meteor configured/placed feature
    └── generation-future diagnostics

    ADMIN / MANUAL SPIKE
    └── expanded structure-block detect/save
        ├── synchronous chunk warmup
        ├── volume scan up to 512³
        └── NBT read/rewrite

    BACKGROUND WORKERS
    ├── AsyncTaskManager CPU + I/O
    ├── Performance engine compute pool
    ├── Data Engine via shared async admission
    ├── telemetry HTTP/persistence
    └── SPH computation
        └── results must return to the owning server thread

## Configuration Findings

- Twenty configuration classes/files were identified. The registration validator catches duplicate filenames/specs, and most numeric values use `defineInRange` and immutable snapshot publication.
- [AUDIT-META-001](#audit-meta-001--generated-mod-metadata-is-not-validly-constrained-for-neoforge-1211) is the largest configuration/build drift: Gradle hardcodes values that conflict with `gradle.properties`.
- [AUDIT-CONC-003](#audit-conc-003--async-config-reload-can-race-and-drop-work) makes live async reload unsafe.
- [AUDIT-CONF-001](#audit-conf-001--meteor-minimum-and-maximum-values-are-not-cross-validated) needs cross-field validation.
- Performance engine components are enabled before production adoption ([AUDIT-ARCH-001](#audit-arch-001--runtime-budget-and-worker-ownership-is-duplicated)); staged infrastructure should default off or explicitly “metrics only.”
- Telemetry player/event reporters default disabled, a good privacy baseline. Master queue enablement should not cause periodic objects/work when all producers are disabled.
- Expanded structure size defaults are too permissive for a synchronous workflow; bounds should reflect safe volume, not only coordinate range.
- Heavy systems generally expose intervals/caps, including weather grid retention, shoreline regions, meteor cadence/counts, and telemetry queue length. That is a strong base for measured tuning.

## Resource/Data Findings

- All source and generated JSON files parsed successfully in the audit.
- [AUDIT-RES-001](#audit-res-001--recipes-and-vanilla-tags-use-pre-121-directory-names) prevents recipes/tags from loading.
- [AUDIT-DEAD-002](#audit-dead-002--meteor-biome-modifier-and-impact-zone-data-are-orphaned) identifies orphan/misdocumented meteor data.
- Registered sounds match `sounds.json` entries; two OGG files are intentionally reused for multiple events rather than missing.
- The meteor configured feature, placed feature, and biome modifier identifiers are coherent with `ModRegistries`.
- `src/main/resources/data/wildernessodysseyapi/worldgen/noise_settings/development_studio.json` is retained only as an inert legacy-save compatibility record; no selectable preset or runtime tooling uses it.
- `src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt` is an immutable source/validation fixture for StructureGen. Its plural directory is intentional; do not “fix” it with the runtime recipes/tags.
- Final Rift Maw/Listener textures are missing as product-quality assets ([AUDIT-VIS-001](#audit-vis-001--two-rift-entity-renderers-still-use-placeholder-textures)).

## API Findings

- `WaterServices` and `WeatherServices` are the correct public ownership boundaries. They expose authoritative queries/actions without requiring consumers to construct replacement simulations. Keep implementation classes and SavedData details behind those facades.
- Water compatibility levels and physics profiles are explicit and data/mod-aware. This is preferable to treating every modded fluid or entity as vanilla water. Document the thread and lifecycle requirements on every API method that can reach a level, entity, chunk, or server-owned mutable state.
- Payload and API record types generally bound inputs and use immutable values, which makes them safer for asynchronous computation and third-party callers.
- Performance engine types such as `ElapsedTimeSimulation`, `AdaptiveBlockEntityTicker`, and `AdaptiveEntityWork` look public/extension-facing but are not yet used by production subsystems. Mark them experimental until one real integration establishes semantics; otherwise the mod may accidentally commit to an API before its behavior is proven.
- Data Engine's non-blocking submission result is a better extension contract than `AsyncTaskManager`'s current implicit caller-runs fallback. Standardize on explicit accepted/rejected outcomes.
- Deprecated Gerstner/water fields and zero-internal-reference utility classes require a binary/source compatibility check before removal. This project is named and packaged as an API, so internal reference counts alone are especially weak deletion evidence.
- Structure-block packet context and mixin accessors are implementation details and should not become third-party API. A stable facade should own expanded-structure requests, validation, progress, and result reporting.

## Error Handling and Diagnostics

- No actionable production `TODO`, `FIXME`, or `XXX` marker was found. Marker hits were intentional limitations, temporary-water terminology, the deliberate offline voice stub, test fixtures, and the two placeholder renderers already reported.
- Broad `catch (Exception)` blocks are concentrated in external HTTP/telemetry, commands, structure placement, version checking, async schedulers, and the structure NBT post-processor. Most inspected paths log or return a bounded failure rather than crashing a tick, but messages should include a stable operation ID, target world/dimension, retry decision, and whether partial mutation occurred.
- `ForwardExtentCopyMixin`'s catch path is misleading because it claims fallback safety without restoring the entity-copy option; that is captured by AUDIT-COMPAT-002.
- `NbtDataCompactor#extractTimestamp` intentionally ignores parse failures, but that entire compactor is unreferenced. Do not activate it without lossless round-trip tests and explicit diagnostics for pruned history/custom encoding.
- The generation-error mixin adds context to failed chunk futures, and StructureGen reports unsupported fields and validates temporary output before publication. These are strong diagnostic patterns to reuse.
- Async rejections, telemetry drops/retries, migration phase/progress, meteor sampled positions, structure volume/chunks, and active renderer compatibility route need explicit bounded metrics. Avoid per-tick log spam; aggregate counters and rate-limit repeated errors.
- Debug logging is configured for development runs under generated `build/` configuration, not as a committed production log override.

## Build/Dependency Findings

### Build attempts

`gradlew test --no-daemon --console=plain --max-workers=1` was attempted twice with JDK 21.0.10. Both attempts failed before Java compilation/tests in `:createMinecraftArtifacts` because NeoForm could not replace a generated `output.jar` under `build/tmp/neoformruntime` (`AccessDeniedException`). `generateStructures` was attempted and hit the same prerequisite lock. This is an environment/file-lock failure, not evidence that source tests failed or passed.

The prior test HTML under `build/reports/tests/test` already contained an unrelated `AccessDeniedException` for a generated Curios config/crash-report path. It predates this validation and is not claimed as a current run.

### Static/artifact checks

- Every JSON file under `src/main/resources` and `src/generated/resources` parsed.
- `git diff --check` found no whitespace error; it emitted only line-ending warnings for pre-existing user-modified files.
- The pre-existing `build/libs/wildernessodysseyapi-4.2.0.jar` was inspected for packaged paths and metadata. This audit did **not** produce that JAR.
- Gradle reports deprecated features that will be incompatible with Gradle 10; capture `--warning-mode all` after the file lock is released.

### Dependency observations

The build integrates WorldEdit, TickTokLib, Create, Regions Unexplored, Lithostitched, Biolith, TerraBlender, Geckolib, Locate Fixer, Curios, Amendments/Selene/Supplementaries, multipart cooking, and optional renderer/profiling tools. OkHttp/Okio, YAML, resilience4j, and zstd are jar-in-jar dependencies. This is a broad compatibility surface, but unused-dependency removal was not recommended without a successful dependency report and runtime matrix.

Metadata currently lists TickTokLib, Curios, Geckolib, and Create as intended required dependencies and Spark as intended optional. Correct the schema and explicitly decide WorldEdit's status before release.

No access transformer or access widener file was found. Private vanilla/external access is handled through mixin accessors/invokers and targeted injections.

## Security/Trust Findings

No critical arbitrary-client world mutation was found in the inspected payload handlers:

- Codex journal input is length-bounded/sanitized.
- Data Engine debug operations require elevated permission.
- Batched data payloads cap entries/body size.
- Water/weather messages have bounded counts/radii.

Security hardening priorities:

1. Expanded structure-block requests combine custom packet data with potentially huge server work. Enforce permissions, dimensions, volume, chunk, and rate limits before any scan ([AUDIT-PERF-003](#audit-perf-003--expanded-structure-block-operations-can-freeze-a-server)).
2. Meteor commands must cap requests and schedule work so an authorized operator cannot accidentally watchdog the server ([AUDIT-PERF-002](#audit-perf-002--meteor-landing-search-can-perform-millions-of-synchronous-block-reads)).
3. External telemetry/AI/feedback endpoints should use bounded timeouts, redact secrets/player identifiers according to policy, and never log tokens or entire payloads. No committed token was observed in the reviewed configuration paths.
4. The cloak packet is server-authoritative, but explicit per-player rate limiting would reduce abusive packet overhead.
5. `NbtAccounter.unlimitedHeap()` in the structure post-processor is inappropriate at an input/file trust boundary; impose a strict file-size and accounting limit.

## Feature Interaction Risks

| Interaction | Emergent risk | Related finding |
| --- | --- | --- |
| SPH worker + integrated client tick + server materialization | A client tick can execute a server-world callback | AUDIT-CONC-001 |
| Telemetry burst + async pool saturation | Full-queue rewrites saturate I/O, then rejected work runs on the server thread | AUDIT-TEL-002 + AUDIT-CONC-002 |
| Riftfall + ecosystem + many modded mobs | Two independent systems perform broad entity work; Riftfall's spatial query is catastrophic | AUDIT-PERF-001 + AUDIT-PERF-005 |
| Meteor search + unloaded chunks + worldgen mixins | A runtime event can synchronously cause generation and traverse canonicalization hooks | AUDIT-PERF-002 |
| Expanded structures + packet extension + chunk warmup + NBT rewrite | One admin action crosses networking, world access, persistence, and rendering | AUDIT-PERF-003 |
| World upgrade + restart/version change | Early metadata commit makes partial mutation appear complete | AUDIT-MIG-001 |
| Create present + WorldEdit paste | Create availability globally enables entity copying even for non-Create operations | AUDIT-COMPAT-002 |
| Create path mutation + user schematic workflow | WO behavior hides Create's ordinary and other-mod schematic data | AUDIT-COMPAT-001 |
| Local weather input substitution + Riftfall/Echo tint | Correct individual mixins can compose in order-dependent colors | Mixin weather cluster |
| Canonical water + global block-set hook + automation/worldgen | Correct reconciliation semantics can impose pack-wide mutation cost | AUDIT-PERF-004 |
| Performance engine + AsyncTaskManager | Independent pools and budgets can oversubscribe during the same lag episode | AUDIT-ARCH-001 |

## Valuable Improvements

- Add a startup diagnostic that prints the authoritative server-context owners, enabled heavy systems, executor queue capacities, and active renderer compatibility route once.
- Add counters/histograms for Riftfall count duration, meteor candidate/block samples, structure scan volume/chunks, ecosystem enumeration size, shoreline refresh reads, weather publication size, async rejections, and telemetry flush coalescing.
- Build a deterministic large-modpack GameTest/integration harness with synthetic entities, chunk loaders, block mutation, weather cells, and water projections.
- Add packaged-artifact tests for `neoforge.mods.toml`, required/optional dependency semantics, recipe/tag paths, mixin target gating, and accidental placeholder assets.
- Create lifecycle tests that start/stop two integrated server worlds in one JVM and assert static state does not cross the boundary.
- Give migrations durable, operator-visible status and backup/rollback documentation.
- Publish a mixin compatibility matrix with exact supported Create/WorldEdit/Sodium/Embeddium/Iris versions and a controlled fallback when a private target changes.
- Convert external-work callers to immutable snapshots and explicit rejection results.
- Add resource reload/client smoke scenarios for VBO/texture cleanup, high GUI scale, localized weather, all liquid orientations, and shader/render-mod combinations.

## Top 10 Recommended Fixes

1. **Correct and test generated mod metadata (AUDIT-META-001).** Startup/dependency correctness affects every user before any gameplay test can matter.
2. **Guard or formally require WorldEdit (AUDIT-MIX-001).** This removes a probable no-WorldEdit startup crash with a small, well-bounded change.
3. **Replace Riftfall's world-sized AABB counts (AUDIT-PERF-001).** It is the clearest recurring catastrophic TPS path.
4. **Make SPH settlement server-owned (AUDIT-CONC-001).** Cross-thread world mutation can corrupt state and is harder to recover from than ordinary lag.
5. **Bound and schedule meteor landing search (AUDIT-PERF-002).** This removes millions of synchronous reads and accidental chunk generation.
6. **Make world upgrades durable and completion-committed (AUDIT-MIG-001).** Existing worlds must not be left partially migrated and falsely marked current.
7. **Eliminate caller-thread async fallback (AUDIT-CONC-002).** Backpressure must reduce optional work, not move HTTP/disk latency onto the tick thread.
8. **Bound/split expanded structure operations (AUDIT-PERF-003).** The current 512³ workflow can freeze or exhaust a server through an authorized action.
9. **Stop mutating Create/WorldEdit global behavior (AUDIT-COMPAT-001 and AUDIT-COMPAT-002).** Preserve user schematics and ordinary paste semantics in mixed packs.
10. **Fix recipe/tag packaging and add artifact tests (AUDIT-RES-001).** This restores three recipes and tool/music tags and prevents silent resource regressions.

This order prioritizes startup, corruption/thread safety, watchdog-level work, existing-world safety, and broad modpack compatibility. FPS improvements do not outrank these because no measured client bottleneck was found.

## Quick Wins

- Add a mixin-plugin class-presence gate for `ForwardExtentCopyMixin` if WorldEdit is intended optional.
- Move the three recipes and three vanilla tag routes to singular 1.21 directories and assert them in the JAR.
- Scan for a Create entity before changing WorldEdit's `copyingEntities` option; preserve the original value.
- Add server-stop/logout removal for telemetry, Riftfall, version-notice, and partner-ad lifecycle collections.
- Coalesce telemetry persistence to one dirty flush.
- Cross-validate meteor min/max pairs and emit a readable config warning.
- Replace cryo assignment's permanent player-tick path with a pending-player set and discovery-triggered retry.
- Remove the no-op meteor biome modifier only after the common-tag compatibility check.
- Add counters around suspected hotspots before changing their architecture.

These are not all more important than the larger work; they are simply low-to-moderate risk changes with useful returns.

## Do Not Touch Without Further Investigation

- **Canonical water persistence, `WaterServices`, projection, and generated-water synchronization.** Simplifying this to ordinary block placement would break the authoritative volume model, save compatibility, renderer handoff, and bounded synchronization.
- **Weather authority and `WeatherServices`.** Client-local weather is a view of server state, not a duplicate simulation. Do not move gameplay decisions client-side or make vanilla global weather a second authority.
- **`bunker.nbt` and StructureGen publication.** The NBT is an immutable regression fixture; source JSON is validated before generated output is published. Do not hand-edit or relocate it as part of resource cleanup.
- **World-upgrade metadata or migration contents.** First make migration idempotent and resumable, back up representative worlds, and only then change completion semantics.
- **Structure-block packet/file formats.** Client/server packet extension, block entity, renderer, and saved NBT must change together with migration/version handling.
- **Renderer mixin clusters.** Removing a seemingly redundant vanilla/Sodium/Embeddium/Iris handoff can produce invisible water, leaks, or only-one-renderer failures.
- **Deprecated/public API fields and interfaces.** Internal zero references do not prove external callers are absent.
- **Create schematic directories.** Removing the global override without migrating user files can make previously created data appear lost.
- **Riftfall state persistence semantics.** Determine which phase/exposure data is intended to survive restart before moving it to SavedData.

## Priority Matrix

| ID | Finding | Severity | Confidence | Performance Impact | Stability Impact | Difficulty | Recommended Order |
| --- | --- | --- | --- | --- | --- | --- | ---: |
| AUDIT-META-001 | Invalid/drifting generated metadata | Critical | High | Low | Critical startup | Medium | 1 |
| AUDIT-MIX-001 | Unguarded WorldEdit target | Critical | High | Low | Critical startup | Low | 2 |
| AUDIT-PERF-001 | Riftfall world-sized entity query | Critical | High | Critical TPS | Critical watchdog | Medium | 3 |
| AUDIT-CONC-001 | Client drains server SPH work | Critical | High | Medium | Critical correctness | High | 4 |
| AUDIT-PERF-002 | Meteor synchronous landing scan | Critical | High | Critical spike | High watchdog/chunks | Medium | 5 |
| AUDIT-MIG-001 | Early migration completion commit | High | High | Medium | Critical world correctness | High | 6 |
| AUDIT-CONC-002 | Caller-runs external work | High | High | High under load | High | Medium | 7 |
| AUDIT-PERF-003 | Expanded structure freeze/NBT risk | High | High | Critical manual spike | High | High | 8 |
| AUDIT-COMPAT-001 | Global Create path replacement | High | High | Low | High user-data visibility | Medium | 9 |
| AUDIT-COMPAT-002 | WorldEdit copies unrelated entities | High | High | Medium | High gameplay | Low/Medium | 10 |
| AUDIT-RES-001 | Wrong recipe/tag paths | High | High | None | High functionality | Low | 11 |
| AUDIT-LIFE-001 | Riftfall lifecycle leak | High | High | Low/Medium | High cross-world | Medium | 12 |
| AUDIT-CONC-003 | Async reload races/drops work | Medium | High | Medium | Medium/High | Medium | 13 |
| AUDIT-TEL-002 | Telemetry write amplification | Medium | High | Medium/High | Medium | Medium | 14 |
| AUDIT-TEL-003 | Off-thread player/blocking logout | Medium | High | Medium/High conditional | Medium/High | Medium | 15 |
| AUDIT-TEL-001 | Telemetry lifecycle retention | Medium | High | Low | Medium | Low/Medium | 16 |
| AUDIT-ARCH-001 | Duplicate execution authority | Medium | High | Medium future | Medium | Medium/High | 17 |
| AUDIT-PERF-004 | Global setBlock water hook | Medium | Medium impact | Potentially High | Medium | High | 18 |
| AUDIT-PERF-005 | Ecosystem loaded-entity scan | Medium | Medium impact | Potentially High | Medium | Medium | 19 |
| AUDIT-PERF-007 | Shoreline bathymetry burst | Medium | Medium | Medium spike | Low | Medium | 20 |
| AUDIT-PERF-006 | Weather view allocations | Medium | Medium | Medium GC | Low | Medium/High | 21 |
| AUDIT-VIS-001 | Placeholder Rift textures | Medium | High | Low | Low | Low | 22 |
| AUDIT-LIFE-002 | Global meteor cadence | Medium | High | Low | Medium | Low | 23 |
| AUDIT-PERF-008 | Cryo handler every player tick | Low | High | Low | Low | Low | 24 |
| AUDIT-CONF-001 | Meteor pair validation | Low | High | Low | Low | Low | 25 |
| AUDIT-DEAD-001 | Disconnected wave route | Low | High/Medium | Low suspected | Medium renderer | Medium | 26 |
| AUDIT-DEAD-002 | Orphan meteor path/data | Low | High | None | Low | Low | 27 |
| AUDIT-LIFE-003 | Small static set cleanup | Low | High | Low | Low | Low | 28 |
| AUDIT-PERF-009 | GPU hook inactive overhead | Low | Medium | Low suspected FPS | Low | Low/Medium | 29 |

## Recommended Follow-Up Phases

### Phase 1 — Critical startup and correctness

Fix metadata generation, guard/require WorldEdit, partition SPH settlement work by owning server/level, replace Riftfall entity counts, and add no-WorldEdit/dedicated-server/integrated-server tests.

### Phase 2 — Catastrophic work and world safety

Bound/schedule meteor and structure operations; make migrations idempotent, durable, resumable, and completion-committed. Validate with copied representative worlds and forced interruption/restart.

### Phase 3 — Async, telemetry, and lifecycle

Remove caller-runs I/O, define rejection behavior, make reload transitions safe, coalesce telemetry persistence, snapshot player data, and clear all server/player lifecycle state.

### Phase 4 — Resource and compatibility corrections

Fix singular resource paths, correct Create/WorldEdit behavior with user-data migration, finish Rift textures, and establish the renderer/worldgen compatibility matrix.

### Phase 5 — Measured performance integration

Instrument the block-mutation hook, ecosystem scan, shoreline refresh, weather allocation, GPU diagnostics, and renderer rebuilds. Consolidate execution authority only after representative Spark/JFR/heap/frame captures.

### Phase 6 — Conservative obsolete-code and architecture cleanup

Remove only proven disconnected meteor/wave/util paths, split the structure god-mixin behind stable interfaces, document authority boundaries, and deprecate public compatibility surfaces before removal.

### Phase 7 — Release verification

Run full `test` and `build`, StructureGen validation, GameTests, dedicated server, client at high GUI scales, clean-world and upgraded-world sessions, disconnect/reconnect lifecycle tests, packaged-JAR metadata/resource assertions, and renderer/mod dependency matrices. Compilation/JAR success is necessary but not sufficient for mixins, visuals, threading, or world migration.

## Validation Record and Limitations

| Check | Result |
| --- | --- |
| Repository inventory and architecture trace | Completed |
| Production/test/resource counts | 955 / 211 / 118 |
| Mixin inventory | 67 configured + 5 dynamic Embeddium; every source mixin listed above |
| Source/generated JSON parse | Passed |
| Packaged-JAR metadata/path inspection | Completed on pre-existing `wildernessodysseyapi-4.2.0.jar` |
| `git diff --check` | No whitespace errors; pre-existing line-ending warnings only |
| `gradlew test --no-daemon --console=plain --max-workers=1` | Blocked twice before compilation by NeoForm generated-JAR `AccessDeniedException` |
| `gradlew generateStructures --no-daemon --console=plain --max-workers=1` | Blocked by the same prerequisite generated-JAR lock |
| Current compile/test/build | **Unverified** |
| Dedicated server startup | Not run |
| Client/mixin/render behavior | Not run |
| Runtime profiler capture | Not obtained; all performance claims are explicitly static |

At audit start, the worktree already contained modified and untracked user work, especially Data Engine and Performance engine changes. Those files were inspected but not edited. The only file created by this audit is this report.
