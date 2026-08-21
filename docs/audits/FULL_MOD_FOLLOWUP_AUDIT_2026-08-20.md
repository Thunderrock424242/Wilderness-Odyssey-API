# Wilderness Odyssey API follow-up repository audit

**Audit date:** 2026-08-20<br>
**Repository:** `Wilderness-Odyssey-API`<br>
**Branch:** `in-dev`<br>
**Audited commit:** `2366fab7abe982124e23d6df6bc37d9acb217822`<br>
**Target:** Minecraft 1.21.1, NeoForge 21.1.x, Java 21<br>
**Mode:** read-only source/resource/runtime-evidence audit; this report is the only file added

## Executive verdict

The first audit remediation materially improved the mod, but the release gate should remain closed. This follow-up found **nine high-priority issues**, **seven medium-priority issues**, and **two low-priority cleanup items** that were outside, introduced after, or not fully covered by the first pass.

The most important new finding is not speculative: the conflict-diagnostics subsystem cannot detect the registry overwrite conflicts it claims to detect, but it logs every successful registry entry through a new synchronous file open. The existing local conflict log is **49,887,017 bytes and 427,725 lines**. A single startup second contains **4,355 lines**, implying thousands of synchronous open/write/close cycles during server startup. The same subsystem also runs an unbounded common-pool JAR scan and writes a “no deadlock” line every 30 seconds for the lifetime of the JVM.

The other release blockers are:

- the latest commit removed the isolated `-PcodexBuildDir` behavior required for reliable Windows validation;
- the modpack structure scaffold emits invalid Minecraft 1.21.1 datapack files;
- the separate `/modpackstructures place` route still performs unlimited NBT accounting and synchronous chunk loading with no placement budget;
- Secret Order villages are missing their template and would be placed repeatedly on every eligible chunk reload if the asset were added;
- The Echo mutates a full vertical chunk scan on every load, including player-built blocks;
- two competing world-version systems can tell an operator an upgrade is complete without running the real migration queue;
- the CodeQL workflow is structurally malformed; and
- the normal `build` and publishing routes do not require the full JUnit suite to pass.

This audit made no production-code, resource, configuration, build-script, or workflow changes. It did not delete anything.

## Scope and method

The follow-up reviewed the current repository rather than assuming the first audit's snapshot still applied. The review covered:

- 960 production Java files;
- 227 test Java files;
- 120 files beneath `src/main/resources`;
- 87 JSON resources across main and generated resource roots;
- all 66 declared mixins and their source files;
- 26 `*Payload` classes and their registration/caller references;
- all config specifications and the runtime use of their public config fields;
- event registration and server/client lifecycle cleanup;
- serverbound request handling and authority checks;
- persistence, migrations, structure NBT, worldgen, chunk-load work, and async executors;
- metadata templates, resource paths, the existing built JAR, and GitHub Actions workflows;
- the previous audit and remediation record; and
- the most recent local client/integrated-server log as existing runtime evidence.

The audit did not launch Minecraft, mutate a world, regenerate data, or run Gradle. The reason Gradle was not run is itself a confirmed finding: the latest commit removed the isolated build-directory application, so the repository's documented `-PcodexBuildDir=.codex-build` safety mechanism currently does nothing. Running Gradle would therefore write to the normal `build/` directory and would not test the required isolated path.

## Priority matrix

| ID | Severity | Confidence | Area | Summary |
|---|---:|---:|---|---|
| `FOLLOWUP-DIAG-001` | High | Confirmed | Diagnostics/performance | Conflict checks are logically unable to detect registry overwrites but generate massive synchronous log I/O. |
| `FOLLOWUP-BUILD-001` | High | Confirmed | Build safety | `-PcodexBuildDir` is declared but no longer changes Gradle's build directory. |
| `FOLLOWUP-WORLDGEN-001` | High | Confirmed | Modpack structures | Generated worldgen scaffold is invalid for Minecraft 1.21.1. |
| `FOLLOWUP-WORLDGEN-002` | High | Confirmed | Structure safety | Runtime modpack NBT placement has unlimited NBT reads and synchronous chunk warmup without budgets. |
| `FOLLOWUP-WORLDGEN-003` | High | Confirmed | Missing feature/world safety | Secret Order villages have no template and the load hook has no one-time placement marker. |
| `FOLLOWUP-PERF-001` | High | Confirmed | The Echo/chunk loads | Every Echo chunk load performs a full-height scan and mutates player-owned blocks. |
| `FOLLOWUP-MIG-001` | High | Confirmed | World upgrades | Legacy `/updateworldversion` changes a label without running the authoritative migration system. |
| `FOLLOWUP-CI-001` | High | Confirmed | Security CI | The CodeQL workflow contains duplicate mappings/keys and a nonexistent config path. |
| `FOLLOWUP-CI-002` | High | Confirmed | Test/release gating | `build` excludes tests and publishing is not gated on build/test success. |
| `FOLLOWUP-CONF-001` | Medium | Confirmed | Config trust | Structure, POI, and impact-site switches are exposed but have no runtime consumers. |
| `FOLLOWUP-LIFE-001` | Medium | Confirmed | Starter bunker | Hostile-spawn zones disappear after restart and can leak between integrated worlds. |
| `FOLLOWUP-TOOL-001` | Medium | Confirmed | Changelog tool | Standalone version discovery still expects a literal version in `build.gradle`. |
| `FOLLOWUP-FAQ-001` | Medium | Confirmed | Memory/logging | FAQ cooldown and unknown-query collections are unbounded for a server/resource session. |
| `FOLLOWUP-COMPAT-001` | Medium | Confirmed product gap | Curios/mask modules | Curios is required metadata, but its bridge and mask module system have no usable registration/install path. |
| `FOLLOWUP-RES-001` | Medium | Confirmed | Player-facing resources | The declared mod logo is not packaged and `/donate` exposes a placeholder URL. |
| `FOLLOWUP-SCHED-001` | Medium | Runtime-dependent concern | Tick scheduling | Riftfall state progression can stop indefinitely whenever the server has no reported spare tick time. |
| `FOLLOWUP-COMPAT-002` | Low | Confirmed | Access transformer | The AT includes an obsolete nonexistent method target and otherwise-unused widening. |
| `FOLLOWUP-DEAD-001` | Low | Confirmed | Dead code | Several disconnected remnants remain after the first dead-code cleanup. |

## Detailed findings

### `FOLLOWUP-DIAG-001` — conflict diagnostics cannot detect their claimed conflicts and create severe file I/O

**Severity:** High<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/modconflictchecker/RegistryConflictHandler.java:27-45,55-103`
- `src/main/java/com/thunder/wildernessodysseyapi/modconflictchecker/StructureConflictChecker.java:23-47`
- `src/main/java/com/thunder/wildernessodysseyapi/modconflictchecker/DedicatedConflictDetector.java:30-60,63-115`
- `src/main/java/com/thunder/wildernessodysseyapi/modconflictchecker/ShaderConflictChecker.java:30-79`
- `src/main/java/com/thunder/wildernessodysseyapi/modconflictchecker/util/LoggerUtil.java:15,35-49,60-76`
- existing local evidence: `run/config/WildernessOdysseyAPI/conflict_log.txt`

**Evidence:**

`RegistryConflictHandler` and `StructureConflictChecker` iterate the final registry's unique key set. For each key, the supposed owner is calculated as `key.getNamespace()`. A registry cannot expose the same key twice, and the namespace is an immutable part of that key. On a later pass, the same key necessarily produces the same “owner.” The branch that reports one namespace overwriting another is therefore not a valid detector of datapack or registry provenance.

Despite that, each unseen structure, POI, biome, and recipe is logged as a successful registration. `LoggerUtil` always opens `config/WildernessOdysseyAPI/conflict_log.txt` in append mode, writes one line, and closes it for every message. On the current local runtime evidence:

- file size: 49,887,017 bytes;
- line count: 427,725;
- lines stamped `2026-08-20 19:57:40`: 4,355;
- repeated “no deadlocked threads” lines: 1,006; and
- actual conflict-like lines: 56.

`DedicatedConflictDetector` separately scans every class entry in every mod JAR through the global `CompletableFuture` common pool. `ShaderConflictChecker` performs another mod-JAR scan through the same common pool. The deadlock executor is static, starts once, has no shutdown path, and writes a success line every 30 seconds even when nothing is wrong.

**Impact:**

- thousands of blocking file opens can lengthen integrated/dedicated server startup;
- the log grows without rotation or size bound across runs;
- a daemon continues generating disk traffic after an integrated server closes;
- the registry report creates false confidence because it cannot observe the overwrite provenance it describes; and
- multiple uncontrolled archive scans contend with JVM/common-pool work during startup.

**Recommendation:**

Remove the impossible registry-key “owner” detector or replace it with an API that can observe resource/datapack provenance during reload. Log only actionable conflicts. Use the ordinary mod logger or a bounded, buffered, rotating diagnostic sink. Run optional archive scans on one bounded lifecycle-owned executor, deduplicate the class/shader traversal, and shut it down on server/client lifecycle close. The deadlock watcher should log only an actual deadlock, with an optional low-frequency debug heartbeat if explicitly enabled.

**Risk of changing:** Medium. Preserve any conflict-report format that users rely on, but do not preserve the per-entry success spam.

### `FOLLOWUP-BUILD-001` — isolated Codex build output was removed in the latest commit

**Severity:** High<br>
**Confidence:** Confirmed<br>
**Location:** `build.gradle:672`

**Evidence:**

The current file declares:

```groovy
def codexBuildDir = providers.gradleProperty("codexBuildDir")
```

but never applies that provider to `layout.buildDirectory`. Commit `2366fab7` removed the seven-line block that set the isolated build directory. Consequently:

```powershell
.\gradlew.bat test -PcodexBuildDir=.codex-build
```

uses the normal `build/` tree. This directly conflicts with the repository's `AGENTS.md` safety rule and invalidates the isolation assumption in the remediation report.

**Impact:** Routine validation can collide with IntelliJ, Minecraft, antivirus, or an existing Gradle process using `build/`. It also makes it harder to distinguish a source/test failure from the Windows NeoForm file-lock problem documented in the first remediation.

**Recommendation:** Restore the property application before any further Gradle validation:

```groovy
def codexBuildDir = providers.gradleProperty("codexBuildDir")
if (codexBuildDir.isPresent()) {
    layout.buildDirectory.set(file(codexBuildDir.get()))
}
```

If empty-property compatibility is still needed, retain the prior safe fallback. Add a small Gradle contract check that asserts the effective build directory when the property is supplied.

**Risk of changing:** Low. The behavior is opt-in and already documented throughout the repository.

### `FOLLOWUP-WORLDGEN-001` — modpack structure scaffolds are invalid for 1.21.1

**Severity:** High<br>
**Confidence:** Confirmed against the locally resolved Minecraft 1.21.1 sources<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/modpack/structure/ModpackStructureRegistry.java:123-164,167-194,215-225`
- `docs/modpack/structure-registry.md:82-113`
- resolved `StructureTemplateManager`: resource lister is `structure`, singular
- resolved `JigsawStructure.CODEC`: required fields include `start_pool` and a codec-backed `start_height`

**Evidence:**

The advertised `/modpackstructures scaffold` output currently:

1. copies NBT to `data/<namespace>/structures/<path>.nbt`, but Minecraft 1.21.1's structure resource directory is singular: `data/<namespace>/structure/`;
2. emits `start_pool_inline`, a field not accepted by the target `JigsawStructure.CODEC`;
3. does not create `data/<namespace>/worldgen/template_pool/<pool>.json` for a valid `start_pool` reference;
4. writes `start_height` as the number `0`, whereas the target height-provider codec expects an encoded vertical anchor/provider object;
5. writes datapack `pack_format: 61`, while this 1.21.1 project uses `pack_format: 48`; and
6. creates `has_structure/<id>.json`, but the structure JSON references `def.biomeTag` directly, so the generated tag is unused.

The documentation tells pack authors to copy this output into a real datapack and test natural generation, so this is an advertised workflow rather than an internal prototype.

**Impact:** Generated packs fail validation or load without the intended structure. Users can spend time tuning spacing, biomes, and NBT for artifacts that Minecraft cannot decode.

**Recommendation:** Generate a real template pool, point `start_pool` to it, use the singular NBT path, encode an accepted height provider/anchor, and source pack format from the target project metadata rather than a duplicate literal. Add a test that decodes the generated structure, pool, and structure-set JSON through the actual 1.21.1 codecs, plus a datapack-load or GameTest smoke test.

**Risk of changing:** Medium. Existing generated output is broken, but any user-authored corrected copies must not be overwritten without an explicit regeneration choice.

### `FOLLOWUP-WORLDGEN-002` — runtime modpack placement bypasses the new structure-block safety budgets

**Severity:** High<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/structure/NBTStructurePlacer.java:161-187,417-447`
- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/structure/LargeStructurePlacementOptimizer.java:80-110`
- `src/main/java/com/thunder/wildernessodysseyapi/util/NbtCompressionUtils.java:30-43`
- `src/main/java/com/thunder/wildernessodysseyapi/modpack/structure/command/ModpackStructureCommand.java:27-45,94-105`

**Evidence:**

The first remediation correctly added hard limits to expanded vanilla structure-block Save/Load/Detect requests. The separate modpack placement path does not reuse them:

- external and bundled structure reads use `NbtAccounter.unlimitedHeap()`;
- oversized templates only produce a warning after parsing;
- `preparePlacement` synchronously calls `level.getChunk` for every touched horizontal chunk;
- there is no maximum file byte size, decoded-NBT quota, block volume, touched-chunk count, or loaded-chunk requirement; and
- `computeChunkSlices` creates a list returned in `PlacementResult`, but the actual placement remains one monolithic `StructureTemplate.placeInWorld` call.

The command requires permission level 2, which reduces the remote attack surface but does not protect a server from an accidental, corrupt, or malicious operator-provided file.

**Impact:** A large compressed NBT can exhaust heap while decoding, force many chunks to generate synchronously, allocate a very large slice list, and then perform a long main-thread world mutation.

**Recommendation:** Reuse a shared structure-budget policy before any chunk load or world mutation: compressed bytes, decoded NBT quota, per-axis size, volume, block count, entity count, touched chunks, and already-loaded status. Reject safely and report exact violated limits. If large admin placement is a supported feature, stage it over bounded ticks with persistent/resumable ownership instead of pretending the unused slice list makes the current placement incremental.

**Risk of changing:** Medium. Existing very large templates may be rejected; provide configurable but safe ceilings and a clear diagnostic.

### `FOLLOWUP-WORLDGEN-003` — Secret Order village feature is missing and unsafe to activate

**Severity:** High<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/secretvillage/SecretOrderVillageGenerator.java:11-38`
- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/secretvillage/SecretOrderVillagePlacer.java:15-59`
- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/config/StructureConfig.java:47-48,119-122`
- resource roots: no `wildernessodysseyapi:village` NBT is present

**Evidence:**

The config exposes `secretOrderVillageSpawnChance`, and an auto-subscribed chunk-load handler calls the placer for every server-side `LevelChunk`. The placer is permanently configured for `wildernessodysseyapi:village`, but no matching bundled or generated template exists.

The code comment says “newly loaded” chunks, not newly generated chunks. The deterministic position seed prevents rerolling; it does **not** prevent re-placement. If a template is later supplied, every eligible jungle chunk will invoke placement again whenever it unloads and reloads. There is no chunk attachment, SavedData marker, structure start, or generation-only lifecycle check recording successful placement.

The seed also excludes the world seed, so the rarity roll selects the same chunk coordinates in every world before the biome test.

**Impact:** Today the feature cannot produce its advertised village. Once an asset is added, it can repeatedly overwrite terrain and player changes at the same location and inherit the unbounded synchronous placement behavior from `NBTStructurePlacer`.

**Recommendation:** Keep the handler disabled until the complete feature exists. Prefer a normal data-driven configured structure/placed feature at world generation. If runtime placement is intentional, gate it to first generation, include the world seed, record a durable per-chunk attempt/success state, validate the template before registration, and never overwrite a previously processed or player-modified site on reload.

**Risk of changing:** Low now because the asset is absent; higher after worlds begin depending on a placement scheme.

### `FOLLOWUP-PERF-001` — Echo chunk distortion scans and mutates every block on every load

**Severity:** High<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/EchoDimensionEvents.java:65-107`
- `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/config/TemporalRiftConfig.java:148-150`

**Evidence:**

With the default-enabled `enableEchoChunkDistortion`, every Echo `ChunkEvent.Load` loops through every `x`, `z`, and `y` position in the full build height. At the normal 384-block height, that is **98,304 block-state reads per chunk load**. It then opens every closed door and removes every leaf position selected by a deterministic hash.

There is no one-time/generated-chunk marker and no distinction between terrain generation and later player builds. A player who closes a door or places leaves in The Echo can have those changes altered when the chunk loads again.

**Impact:** Exploration/loading bursts can create large server-thread stalls. The behavior also violates player ownership by reprocessing and mutating established chunks indefinitely.

**Recommendation:** Move initial visual decay into generation or a one-time, persisted, budgeted transformation. Operate section/palette-first so empty or irrelevant sections are skipped. Record the applied transformation version per chunk. Do not touch later player changes; if ongoing environmental decay is desired, make it a separate documented, bounded rule with protection/tool compatibility.

**Risk of changing:** Medium. Existing Echo chunks may already be partially transformed; the migration marker must distinguish legacy chunks without reprocessing user builds.

### `FOLLOWUP-MIG-001` — two world-version authorities give contradictory operator guidance

**Severity:** High<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/core/ModConstants.java:18-27,35-38`
- `src/main/java/com/thunder/wildernessodysseyapi/worldversion/client/WorldVersionChecker.java:37-56,66-104,109-129`
- `src/main/java/com/thunder/wildernessodysseyapi/worldupgrade/WorldUpgradeManager.java:26-72,108-120`
- `src/main/java/com/thunder/wildernessodysseyapi/worldupgrade/command/WorldUpgradeCommand.java:17-68`
- `src/main/resources/assets/wildernessodysseyapi/lang/en_us.json:149`
- existing local evidence: `run/logs/latest.log:275,278`

**Evidence:**

The legacy `WorldVersionChecker` owns `config/world_version.json`, a world-root `world_version.json`, and `/updateworldversion`. That command only copies a version string into the world JSON and reports “World version updated.” It does not invoke `WorldUpgradeManager`, enqueue chunks, inspect failed migrations, or complete `WorldUpgradeSavedData`.

The newer authoritative system uses SavedData, per-chunk upgrade versions, a pending pack version, and `/worldupgrade start|pause|retry-failed|complete|status`. It deliberately refuses completion while paused, queued, or failed.

The latest local run demonstrates both systems simultaneously:

- `World upgrade for pack version 4.2.0 is running...`; and
- `Config 'world_version' is up to date: 1.0.0`.

The old `1.0.0` constant is also described as a modpack/world version in donation and partner-ad opt-out logic, while runtime release metadata is `4.2.0`.

**Impact:** An operator can follow the displayed `/updateworldversion` instruction, receive a success message, and reasonably believe the real migration has completed when it has not. Version-scoped opt-out behavior is also tied to a static compatibility label rather than the packaged release version it claims to track.

**Recommendation:** Make `WorldUpgradeSavedData` the single authority. Import legacy JSON once as compatibility input, then deprecate or redirect `/updateworldversion` to the authoritative status/start workflow. Never use “updated” or “complete” unless the migration queue's completion criteria pass. Separate schema version, pack/release version, and notification-version concepts with distinct names and storage.

**Risk of changing:** High unless migration is explicit. Preserve old JSON for recovery and avoid marking any chunk upgraded based only on the label.

### `FOLLOWUP-CI-001` — CodeQL workflow is structurally malformed

**Severity:** High<br>
**Confidence:** Confirmed from workflow source; remote Actions state was not queried<br>
**Location:** `.github/workflows/codeql.yml:20-85`

**Evidence:**

The Java job contains:

- two sibling `steps:` keys at lines 29 and 34;
- a stray `EXAMPLE_SECRET` mapping at job level after a step at line 32;
- duplicate `uses:` and `with:` keys inside the Qodana step at lines 59-68;
- a CodeQL config path `.github/workflows/codeql-config.yml`, while the repository's actual file is `/codeql-config.yml`; and
- two CodeQL analyze steps, with the first passing a Qodana SARIF path to an input that does not belong to the CodeQL database analysis step.

**Impact:** The workflow is likely rejected during YAML/workflow validation or fails before producing reliable CodeQL/Qodana results. The repository README's security-automation claim is therefore not supported by the checked-in workflow.

**Recommendation:** Use one `steps` sequence, one checkout, one Qodana action invocation, one Qodana SARIF upload, the correct root CodeQL config path, and one CodeQL analyze step. Remove the example secret. Validate with an Actions-aware linter and confirm a real pull-request run uploads both categories.

**Risk of changing:** Low. This is CI-only, but verify the current supported action inputs rather than copying old examples.

### `FOLLOWUP-CI-002` — tests and publishing are not release gates

**Severity:** High<br>
**Confidence:** Confirmed<br>
**Locations:**

- `build.gradle:637-646`
- `.github/workflows/build.yml:25-32`
- `.github/workflows/publish.yml:1-32`
- `docs/audits/FULL_MOD_AUDIT_REMEDIATION.md:45-54`

**Evidence:**

`tasks.named('build') { setDependsOn([assemble]) }` intentionally removes Gradle's normal verification dependency, so `gradlew build` does not run `test` or `check`. The build workflow runs `runData`, a focused `structureGenTest`, and then this assemble-only `build`; it never runs the full JUnit suite.

The publish workflow triggers independently on every push to `main` and runs `gradlew publish` without a preceding test/build gate or a dependency on the other workflows. GitHub workflows running on the same push do not implicitly wait for each other.

This matters because the remediation added many regression tests but explicitly records that none could be executed after the NeoForm lock.

**Impact:** A release can be assembled and published with compiling test sources or failing regressions never evaluated. Packaging assertions may also be skipped depending on their task wiring.

**Recommendation:** Restore normal `build` semantics if feasible, or introduce a clearly named `releaseCheck` that requires compilation, full JUnit, resource/JAR contracts, focused StructureGen tests, and any required GameTests. Make publish depend on that job in one workflow or trigger only from a successful required workflow. Never publish directly from an unverified branch push.

**Risk of changing:** Medium because the test runtime has an existing NeoForge/JUnit health issue. Fix the harness instead of permanently redefining `build` as assemble.

### `FOLLOWUP-CONF-001` — structure and impact-site config controls do nothing

**Severity:** Medium<br>
**Confidence:** Confirmed<br>
**Location:** `src/main/java/com/thunder/wildernessodysseyapi/worldgen/config/StructureConfig.java:21-22,50-59,125-177`

**Evidence:**

`DEBUG_DISABLE_IMPACT_SITES` has no runtime read. `isStructureEnabled` and `isPoiEnabled` have no callers. `registerAll` iterates built-in **structure types** and POI types during static config initialization, not individual configured structures or data-driven placements.

The generated local TOML demonstrates the mismatch: it exposes `minecraft.jigsaw`, `minecraft.shipwreck`, and other algorithm/type IDs rather than the actual configured structure IDs pack authors would expect. It contains only entries available at that early built-in-registry moment.

**Impact:** Server owners can disable values, restart, and see no gameplay change. The `debugDisableImpactSites` description specifically promises that meteor sites will not generate, but meteor impact placement does not consult it.

**Recommendation:** Either wire a correctly named, supported control at the appropriate data/registry lifecycle or remove/deprecate the misleading switches. For impact sites, consult a meteor-owned server config at the authoritative placement boundary. For structures/POIs, prefer data-driven tags/biome modifiers or explicit per-feature controls rather than attempting to enumerate registry types during class initialization.

**Risk of changing:** Medium. Existing configs contain inert keys; preserve them as deprecated aliases for one transition if removal would surprise pack maintainers.

### `FOLLOWUP-LIFE-001` — starter-bunker spawn protection has process lifetime rather than world lifetime

**Severity:** Medium<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/structure/StarterStructureSpawnGuard.java:18-29,32-86`
- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/spawn/SpawnBunkerPlacer.java:216`

**Evidence:**

The guard stores zones in a static map keyed only by `ResourceKey<Level>`. `clearAll` has no production caller, and a zone is registered only when a bunker is newly placed.

Therefore:

- after a JVM restart, an existing bunker has no zone and loses the promised hostile-spawn protection; and
- when an integrated client closes one save and opens another in the same process, the old `minecraft:overworld` zone can apply to the new save at the same coordinates.

The comment says “recently placed” and the config says “immediately after placement,” but there is no expiry. The actual lifetime is an arbitrary JVM session.

**Impact:** Protection is inconsistent and can affect unrelated worlds. A stale zone can block hostile spawning where no bunker exists; a restarted world can spawn hostiles in the bunker.

**Recommendation:** Decide on durable versus temporary behavior. For durable protection, persist/reconstruct exact bunker bounds from the existing cryo/spawn world data. For temporary protection, store an expiry tick in server-scoped state. In both cases key by the owning server/world identity and clear state on server stop/level unload.

**Risk of changing:** Low to medium. Persisted bounds must match the already-placed bunker rather than recomputing from a changed template.

### `FOLLOWUP-TOOL-001` — changelog CLI default version discovery no longer matches the build

**Severity:** Medium<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/changelog/tool/GeneratorOptions.java:18-23,47-76`
- current `build.gradle`: version is `project.mod_version`
- current `gradle.properties`: authoritative `mod_version`

**Evidence:**

The standalone tool's documented fallback searches `build.gradle` for a literal such as `version = "4.2.0"`. The build now sources version from `gradle.properties`, so the pattern cannot match the current repository. Gradle's `generateChangelog` task supplies `--version`, which masks the problem in that one route; the standalone/default invocation remains broken. Its test fixture still uses the obsolete literal layout.

**Impact:** Direct CLI use fails with “Could not find the top-level project version,” and the test does not protect the repository's real metadata architecture.

**Recommendation:** Read `mod_version` from `gradle.properties` through a small strict properties parser, or require `--version` and remove the false default claim. Add a test fixture matching the current repository.

**Risk of changing:** Low.

### `FOLLOWUP-FAQ-001` — FAQ diagnostics retain unbounded player/query state

**Severity:** Medium<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/faq/FaqCommand.java:27-29,209-223`
- `src/main/java/com/thunder/wildernessodysseyapi/faq/FaqManager.java:38-42,165-179`

**Evidence:**

`QUERY_COOLDOWN` retains one UUID entry per player and has no logout/server-stop cleanup. `NO_RESULT_QUERIES` retains every distinct normalized no-result query until a resource reload and emits an INFO log for every miss. `/faq` is available to ordinary players. The 1.2-second cooldown bounds per-player rate, but not total UUIDs, unique queries, or long-lived server memory/log growth.

**Impact:** A long-running public server accumulates stale identities and arbitrary query strings. Repeated nonsense searches create noisy operational logs.

**Recommendation:** Remove cooldown entries on logout and clear on server stop. Give unknown-query telemetry a fixed maximum cardinality and TTL or aggregate it into bounded counters. Log it at debug level or periodically summarize the top misses.

**Risk of changing:** Low.

### `FOLLOWUP-COMPAT-001` — required Curios and mask-module integration is only a future stub

**Severity:** Medium<br>
**Confidence:** Confirmed product-contract gap<br>
**Locations:**

- `src/main/templates/META-INF/neoforge.mods.toml:94-99`
- `src/main/java/com/thunder/wildernessodysseyapi/cloak/item/module/EchoMaskCuriosBridge.java:7-23`
- `src/main/java/com/thunder/wildernessodysseyapi/cloak/item/module/EchoMaskModuleRegistry.java:10-34`
- `src/main/java/com/thunder/wildernessodysseyapi/cloak/item/module/EchoMaskModuleStorage.java:17-102`

**Evidence:**

Metadata makes Curios a required dependency, but the only bridge says “Real Curios slot registration can attach here later.” It is not called. There are no Curios slot registrations or Curios API calls.

The mask-module storage can read/combine modules, but production code registers no modules and exposes no recipe, menu, command, event, or other install/remove path. The public registry and storage are therefore extension scaffolding rather than a player-usable feature.

**Impact:** Every user must install Curios without receiving the advertised integration from this mod, and the modular mask design cannot be exercised in normal gameplay.

**Recommendation:** Make Curios optional until the integration exists, unless another hard gameplay contract genuinely needs it. Otherwise implement a declared slot, equip behavior, rendering/attribute behavior, built-in modules, a bounded install/remove interaction, synchronization, and compatibility tests. Clearly document the module registry as an extension-only API if that is the actual intent.

**Risk of changing:** Medium. Changing a required dependency can affect pack resolution; implementing the integration creates client/server and API-version compatibility obligations.

### `FOLLOWUP-RES-001` — player-facing metadata contains a missing logo and placeholder link

**Severity:** Medium<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/templates/META-INF/neoforge.mods.toml:37-38`
- project-root `logo.png`
- `src/main/java/com/thunder/wildernessodysseyapi/donations/command/DonateCommand.java:17-35`
- existing `build/libs/wildernessodysseyapi-4.2.0.jar`

**Evidence:**

The metadata declares `logoFile="logo.png"`, which means the file must be in the JAR root. The image exists only at the repository root; `processResources` does not copy it. The existing JAR contains the metadata and mixin config but no root `logo.png`.

The registered `/donate` command exposes a clickable `https://your.donation.link`, which is a placeholder rather than a live project URL. The Memorial Sloan Kettering link is real.

**Impact:** The mod-list logo is missing, and players can be sent to a meaningless or potentially later-acquired domain from a trusted in-game command.

**Recommendation:** Put the logo in `src/main/resources/logo.png` or add an explicit resource copy, then assert the exact JAR entry. Replace the placeholder with an intentional configured/translated link or omit the self-donation line until one exists.

**Risk of changing:** Low.

### `FOLLOWUP-SCHED-001` — gameplay progression is conditioned entirely on spare tick time

**Severity:** Medium<br>
**Confidence:** Runtime-dependent concern, not yet proven as a live failure<br>
**Locations:**

- `src/main/java/com/thunder/wildernessodysseyapi/server/ServerLifecycleEvents.java:63-82`
- similar optional-work gates in weather, water/weather coupling, vegetation, environment sync, distant wildlife, and partner ads

**Evidence:**

`ServerLifecycleEvents.onServerTick` returns immediately when `event.hasTime()` is false, drains async/DataEngine work first, checks the same allowance again, and only then advances Riftfall. On a continuously overloaded server, Riftfall stage timers and scheduled spawn behavior can stop indefinitely rather than advancing at a reduced rate.

Several other systems use the same signal. Using spare time for optional scans is sound; using it as the only clock for authoritative gameplay state can cause simulation time to diverge from world time.

**Impact:** Under sustained load, players may observe events that never progress, ads or synchronization that never complete, or stale visual/gameplay state. The exact impact requires an overloaded-server test, so this remains a concern rather than a confirmed runtime defect.

**Recommendation:** Separate cheap state/cadence advancement from optional expensive work. Always advance authoritative clocks, enqueue a bounded unit, and consume that work only while budget remains. Add counters for scheduled, completed, skipped, and oldest-pending age, then verify under a controlled low-MSPT budget.

**Risk of changing:** Medium. Poorly chosen mandatory work can worsen overload; keep the always-run portion constant-time.

### `FOLLOWUP-COMPAT-002` — stale access-transformer entries remain

**Severity:** Low<br>
**Confidence:** Confirmed<br>
**Locations:**

- `src/main/templates/META-INF/accesstransformer.cfg:1-8`
- `build.gradle:39`
- `src/main/templates/META-INF/neoforge.mods.toml:53-56`

**Evidence:**

The access transformer targets protected `Camera.setPosition(DDD)V` and `Camera.setRotation(FF)V`, but no production code in this repository calls the widened methods. It also targets `Minecraft.getFrameTime()F`, which does not exist in the resolved 1.21.1 source; the current source exposes `getFrameTimeNs()` and `getDeltaTracker()` instead.

**Impact:** The file adds global compatibility surface and can emit transformation warnings without enabling a current feature.

**Recommendation:** Confirm no external compatibility contract depends on the camera widening, then remove the obsolete frame-time line. If all entries are unused, remove the AT declaration/property/file together and validate client plus dedicated-server startup.

**Risk of changing:** Low internally, but perform the runtime check because access-transformer failures occur during startup.

### `FOLLOWUP-DEAD-001` — disconnected remnants remain after the first cleanup

**Severity:** Low<br>
**Confidence:** Confirmed by repository reference search<br>
**Examples:**

- `src/main/java/com/thunder/wildernessodysseyapi/worldgen/util/DeferredTaskScheduler.java` has no callers;
- the read/async-read helpers in `src/main/java/com/thunder/wildernessodysseyapi/util/NbtCompressionUtils.java` have no callers and retain unlimited NBT accounting; the class's bounded-write helper is active and should be preserved;
- `src/main/java/com/thunder/wildernessodysseyapi/worldversion/client/WorldVersionClientCommand.java` is an entirely commented-out class; and
- the Curios bridge is disconnected as described above.

**Impact:** These files confuse ownership and make unsafe patterns appear available for reuse. They are not currently driving runtime behavior.

**Recommendation:** Remove private/internal remnants after confirming no reflection or documented extension API references them. Do not remove public compatibility markers such as the deliberately retained deprecated meteor/wave surfaces without a separate compatibility decision.

**Risk of changing:** Low for private dead code; medium for anything public or documented.

## Missing or incomplete features

The following are confirmed product gaps rather than merely future ideas mentioned in design documents:

1. **Secret Order village generation:** config and event path exist, but no template exists and the lifecycle is unsafe.
2. **Modpack structure scaffolding:** command and documentation exist, but generated data does not decode on the target version.
3. **Structure/POI/impact-site switches:** visible configuration exists but is not connected to behavior.
4. **Curios mask integration:** required dependency and bridge exist, but there is no slot or player-usable integration.
5. **Echo mask modules:** storage/API scaffolding exists, but there are no registered modules or install/remove gameplay route.
6. **Security automation:** README/workflow intent exists, but checked-in CodeQL workflow structure is invalid.
7. **Release verification:** many new tests exist, but the normal build/publish path does not require them.
8. **Player-facing polish:** declared logo is absent from the artifact and one donation URL remains a placeholder.

The audit did **not** classify clearly documented future layers as defects. Examples include cross-species ecosystem groups/genetics, external AI/voice support, severe Studio weather controls, and general snapshot restoration. Those documents explicitly label the behavior as future, optional, or intentionally disabled. They should become findings only if release/marketing metadata promises them as currently available.

## Areas that passed static follow-up checks

The second pass did not find a new confirmed defect in these areas:

- all 87 JSON resources parsed successfully;
- all 66 declared mixins have corresponding source files;
- all `*Payload` classes have registration or non-orphan references, and the reviewed serverbound handlers marshal work to the server thread;
- the Codex journal payload applies an explicit UTF length cap and server-side sanitization;
- Studio request handlers retain server-owned authority/region/budget checks;
- no unresolved TODO or FIXME markers exist in production or tests;
- no committed API key, webhook secret, password, or bearer token was found by the focused secret-pattern scan;
- recipe/tag resource directories fixed by the first remediation remain singular where required;
- custom model, blockstate, and texture references had no missing target in the static resource resolver;
- the mixin JSON and all other JSON stayed valid; and
- `git diff --check` was clean before this report was added.

These are static checks, not proof that mixins apply, shaders render, worldgen behaves, or optional-mod combinations start successfully.

## Existing runtime evidence, and its limits

`run/logs/latest.log` records a client/integrated-server session on 2026-08-20 after the large remediation commit and before the final build-script-only commit. It shows:

- the integrated server started;
- a player joined;
- Anomaly Gateway travel into The Echo succeeded;
- the server stopped cleanly; and
- no Wilderness Odyssey exception was found in the focused error scan.

It also proves the two world-version systems reported `4.2.0` and `1.0.0` in the same startup, and it supplies the conflict-log timing evidence above.

This is **existing developer runtime evidence**, not a run performed as part of this audit. It does not prove a clean dedicated server, mixin compatibility, new tests, worldgen in fresh chunks, migration correctness, or behavior without optional mods.

## Validation record

| Validation | Result |
|---|---|
| Repository/commit/worktree inventory | Passed; branch `in-dev`, commit `2366fab7`, clean before this report. |
| JSON parse across main/generated resources | Passed; 87 files, 0 failures. |
| Mixin declaration-to-source check | Passed; 66 declarations, 0 missing sources. |
| Payload orphan/registration reference scan | Passed; 26 payload classes, no single-reference orphan. |
| Config-field consumer scan | Found one declared-only gameplay switch: `DEBUG_DISABLE_IMPACT_SITES`; structure/POI helper methods also have no callers. |
| Model/blockstate/texture reference scan | Passed; no missing custom targets in the focused resolver. |
| Focused secret-pattern scan | Passed; only environment/property reads and an intentionally blank `rcon.password` were found. |
| `git diff --check` before report | Passed. |
| `compileJava -PcodexBuildDir=.codex-build` | **Not run:** current build no longer applies `codexBuildDir`; isolated validation is unavailable until `FOLLOWUP-BUILD-001` is fixed. |
| `test -PcodexBuildDir=.codex-build` | **Not run:** same isolation defect; first remediation also records an unresolved NeoForm generated-JAR lock before Java compilation. |
| full `build` / packaged-JAR contract | **Not run:** same reason. The existing JAR was inspected only as stale artifact evidence for the missing logo. |
| client/server/GameTest runtime | **Not run:** this was a read-only audit and the isolated compile/test gate is not restored. |

## Recommended remediation order

1. **Restore build isolation** (`FOLLOWUP-BUILD-001`) so every later change can be validated safely.
2. **Stop the conflict-log storm** (`FOLLOWUP-DIAG-001`) before more client/server testing; retain the current log as evidence or archive it outside the runtime config directory.
3. **Fix CI/release gates** (`FOLLOWUP-CI-001`, `FOLLOWUP-CI-002`) before treating any new build as releasable.
4. **Disable unsafe/incomplete world mutation** (`FOLLOWUP-WORLDGEN-003`, `FOLLOWUP-PERF-001`) until generation-only, persisted ownership exists.
5. **Apply one shared structure budget** to modpack placement and repair the generated scaffold (`FOLLOWUP-WORLDGEN-001`, `FOLLOWUP-WORLDGEN-002`).
6. **Unify world upgrade authority** without trusting legacy JSON as proof of migration (`FOLLOWUP-MIG-001`).
7. **Wire or deprecate misleading configuration and integration surfaces** (`FOLLOWUP-CONF-001`, `FOLLOWUP-COMPAT-001`).
8. **Close lifecycle/cardinality leaks and tool/resource polish** (`FOLLOWUP-LIFE-001`, `FOLLOWUP-FAQ-001`, `FOLLOWUP-TOOL-001`, `FOLLOWUP-RES-001`).
9. **Profile the runtime-dependent scheduler concern** before changing mandatory per-tick work (`FOLLOWUP-SCHED-001`).
10. **Remove stale internal remnants only after startup/compatibility verification** (`FOLLOWUP-COMPAT-002`, `FOLLOWUP-DEAD-001`).

After step 1, the minimum integration sequence should be:

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-daemon --console=plain --max-workers=1
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-daemon --console=plain --max-workers=1
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-daemon --console=plain --max-workers=1
```

Then inspect the produced JAR and run targeted runtime checks:

- clean dedicated-server startup/shutdown with required mods and without optional mods;
- client startup with vanilla renderer, Sodium/Embeddium, Iris combinations, Create, and WorldEdit absent/present;
- a fresh world and an existing-world copy through the real `/worldupgrade` workflow;
- generate and load a modpack-structure scaffold as a datapack;
- reject over-budget external NBT without loading chunks or changing blocks;
- enter The Echo, reload chunks, and verify player-built doors/leaves are preserved;
- verify a Secret Order village can generate only once after its feature is completed; and
- leave/reopen integrated worlds to confirm all world-scoped caches and bunker protection are isolated.

## Removal/deletion explanation

This follow-up audit did not remove or delete anything. It added only this report.

For the earlier remediation, two production classes were intentionally deleted:

- The disconnected `WaveRenderMixin` and its mixin-config entry were removed because they had no active target behavior, while retaining a dead mixin still adds startup and compatibility risk. The public `WaveAnimator` and `WaveVertexConsumer` surfaces were deliberately **not** deleted; they remain deprecated compatibility APIs.
- `ReactiveVegetationAttachmentSyncHandler` was removed because vegetation synchronization moved to the explicit `ReactiveVegetationSyncPayload` and `ReactiveVegetationSyncService`. The replacement sends only after NeoForge reports a chunk to the client, orders updates with revisions, skips unchanged visual state, and clears per-level lifecycle state. Keeping both handlers would duplicate synchronization and reintroduce timing ambiguity.

`BunkerPlacementProcessorTest` was also deleted because the narrow boolean helper it tested was removed. Its safety responsibility was replaced, not abandoned: `BlockEntityNbtSanitizingProcessor` now performs registry-aware block-entity metadata checks, and `BlockEntityNbtSanitizingProcessorTest` covers matching, incompatible, malformed, missing, and unknown IDs.

The recipe/tag changes were moves from obsolete plural Minecraft 1.21 paths to the required singular paths, not feature deletion. The Create schematic migration was changed from global replacement behavior to non-destructive staged copying; source schematics are never deleted or overwritten. Compatibility meteor tags and the public meteor biome-modifier marker were also retained because they may be external datapack/API contracts.

That deletion policy remains appropriate for future fixes: delete only proven-disconnected internal code, preserve worlds and user files, and deprecate public compatibility surfaces before removal.

## Final release assessment

The current repository contains strong systems and substantially better safety than the first audited snapshot, but it is not yet release-ready. The immediate blockers are concrete and fixable: restore deterministic isolated validation, stop the diagnostics I/O storm, repair CI gating, disable/rework unsafe chunk-load mutation, repair structure scaffolding/placement budgets, and make the migration system the single authority.

No claim in this report that a source path “looks correct” substitutes for a successful isolated JDK 21 compile, full JUnit run, packaged-JAR inspection, dedicated-server smoke test, and focused client/world compatibility matrix.
