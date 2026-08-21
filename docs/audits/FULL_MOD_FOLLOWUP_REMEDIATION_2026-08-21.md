# Wilderness Odyssey API follow-up audit remediation

**Remediation date:** 2026-08-21<br>
**Source audit:** `FULL_MOD_FOLLOWUP_AUDIT_2026-08-20.md`<br>
**Target:** Minecraft 1.21.1, NeoForge 21.1.x, Java 21

## Outcome

All 18 findings in the follow-up audit have a concrete remediation in this change set. The fixes
favor bounded work, server/world-owned state, honest compatibility surfaces, and explicit release
gates. Existing worlds and user-authored runtime files were preserved. Runtime behavior that needs
a real Minecraft client or server is still called out separately from compile/test evidence.

## Finding-by-finding status

| Finding | Status | Remediation |
|---|---|---|
| `FOLLOWUP-DIAG-001` | Fixed | Removed the impossible final-registry overwrite scan and per-entry success logging. Class/shader archive checks now share one bounded, lifecycle-owned executor and traversal. Deadlocks are logged only when found and only once per thread per server lifecycle. Diagnostics use the normal rotating mod log. |
| `FOLLOWUP-BUILD-001` | Fixed | Restored `-PcodexBuildDir` before later build paths are configured. `verifyBuildIsolation` now fails `check` if the requested directory is not the effective Gradle build directory. StructureGen accepts only the normal `build/` root or a direct hidden `*-build` isolation root. |
| `FOLLOWUP-WORLDGEN-001` | Fixed | Scaffold NBT now uses singular `structure/`; a real template pool is emitted; `start_pool`, encoded `start_height`, and current runtime data-pack format are used; obsolete generator-owned plural NBT and unused `has_structure` output are removed on explicit regeneration. Documentation and contract tests were updated. |
| `FOLLOWUP-WORLDGEN-002` | Fixed | External NBT uses 16 MiB compressed and 64 MiB decoded limits, plus axis, volume, block-entry, entity-entry, touched-chunk, and already-loaded-chunk limits. Placement never requests an unloaded chunk. External files take precedence over a bundled template with the same id so operator input cannot bypass validation. |
| `FOLLOWUP-WORLDGEN-003` | Fixed | Added and validated the missing `village` blueprint. Placement is Overworld-only, generation-only, biome-gated, and deterministic from both world seed and chunk position, so reloads cannot re-place over player work. |
| `FOLLOWUP-PERF-001` | Fixed | Echo distortion is generation-only. It checks section palettes before reading individual states and visits only relevant sections, so later player doors/leaves are not rewritten on chunk reload. |
| `FOLLOWUP-MIG-001` | Fixed | `/worldupgrade` and `WorldUpgradeSavedData` are the only migration authority. Legacy `/updateworldversion` starts that queue and reports honest completion conditions. The old JSON label is imported once as non-authoritative metadata and is never treated as migrated state. Format-2 completed upgrades remain completed when format-3 metadata is added. |
| `FOLLOWUP-CI-001` | Fixed | Rebuilt CodeQL/Qodana workflow without duplicate mappings or missing config references. It uses current official action generations, manual Java compilation, bounded timeouts, and SARIF upload. Workflow YAML is parsed with duplicate-key rejection in tests. |
| `FOLLOWUP-CI-002` | Fixed | Removed the `build -> assemble` override. Pull requests run generation, focused StructureGen tests, the full build/test gate, and a generated-diff check. Publishing runs only for a release/manual request and requires a successful full isolated build before publishing with `GITHUB_TOKEN`. |
| `FOLLOWUP-CONF-001` | Fixed | Removed non-functional dynamic structure/POI config entries while retaining deprecated always-enabled source-compatibility methods. `DEBUG_DISABLE_IMPACT_SITES` is now enforced both when a shower is requested and immediately before crater/site mutation. |
| `FOLLOWUP-LIFE-001` | Fixed | Exact successful bunker bounds are persisted in `CryoSpawnData`. Older saves reconstruct bounds once from durable cryo positions. No dimension-keyed static zone remains, so protection survives restart without leaking between integrated worlds. |
| `FOLLOWUP-TOOL-001` | Fixed | The changelog CLI reads authoritative `mod_version` from `gradle.properties`; explicit `--version` remains supported. Tests now mirror the repository metadata layout. |
| `FOLLOWUP-FAQ-001` | Fixed | Unknown-query telemetry is capped at 256 normalized keys with bounded key length and debug-level logging. Player cooldowns are removed on logout and all cooldowns are cleared on server stop. |
| `FOLLOWUP-COMPAT-001` | Fixed contract | Curios is optional and its unused runtime dependency/bridge were removed. Mask modules are explicitly documented as an extension-only API: there are no fictional built-ins or install UI, and registry snapshots are immutable. |
| `FOLLOWUP-RES-001` | Fixed | `logo.png` is copied to the JAR root and asserted by the artifact test. The placeholder self-donation URL was removed; the verified charity link remains. |
| `FOLLOWUP-SCHED-001` | Fixed | Cheap authoritative Riftfall phase, cooldown, and exposure advancement now runs every server tick. Weather sampling, corrosion, and spawn searches remain optional work gated by spare tick time. |
| `FOLLOWUP-COMPAT-002` | Fixed | Removed the unused access-transformer declaration, metadata block, and file, including the obsolete frame-time target. |
| `FOLLOWUP-DEAD-001` | Fixed | Removed disconnected private/internal schedulers, conflict helpers, Curios stub code, legacy client version UI/command code, and unused unlimited-read/rewrite NBT helpers while retaining the active bounded writer and deprecated public compatibility surfaces. |

## Additional release-gate repair

Re-enabling the full test lifecycle exposed an existing chunk-ownership architecture failure:
`StructureBlockEntityMixin` submitted file recompression through the global optimization governor.
That work now uses `StructureBlockIoExecutor`, a structure-block-owned single worker with a 32-task
queue. It starts and stops with each server, never blocks the server thread on queue admission, and
performs file-only work; Minecraft world/chunk access remains on the owning server thread.

## Deletion and removal ledger

The following removals were intentional:

- `StructureConflictChecker.java` was deleted because iterating the final registry cannot reveal an
  overwritten prior owner. Keeping it produced false confidence and thousands of success-log writes.
- `ShaderConflictChecker.java` was deleted because shader and class overlap checks now share the same
  bounded archive traversal; retaining it would scan every mod JAR twice.
- `modconflictchecker/util/Utils.java` was deleted after the final consistency pass confirmed it had
  no callers; its conflict method repeated the same impossible ownership model.
- `EchoMaskCuriosBridge.java` was deleted because it was an uncalled placeholder with no Curios API
  behavior. Curios was changed from required to optional instead of pretending this bridge worked.
- `DeferredTaskScheduler.java` was deleted because it had no callers or lifecycle owner.
- `WorldVersionClientChecker.java`, `WorldVersionWarningScreen.java`, and the fully commented-out
  `WorldVersionClientCommand.java` were deleted because they belonged to the contradictory label-only
  version system. The legacy server command name remains as a compatibility redirect to real migration.
- `META-INF/accesstransformer.cfg` and its declarations were deleted because all targets were unused
  and one target does not exist in the resolved 1.21.1 source.
- Unused `NbtCompressionUtils` read, async-read/rewrite, and decompression methods were removed because
  they had no callers and advertised unlimited NBT accounting. The active bounded compression writer
  remains.
- Non-functional generated structure/POI config entries and obsolete version-warning translation keys
  were removed because there was no runtime behavior behind them. Deprecated Java compatibility methods
  remain where source compatibility has value.
- The placeholder `https://your.donation.link` line was removed because a trusted in-game command must
  not send players to an unowned placeholder. No verified project donation URL was invented.
- The direct Curios implementation dependency was removed because there are no Curios API calls. Optional
  metadata remains so packs may install Curios without a hard requirement.

The publishing workflow was replaced, not deleted: the new file adds a release trigger, permissions,
the full build gate, and `GITHUB_TOKEN`. The scaffold generator deletes only the exact obsolete files it
previously generated for the same structure id during an explicit scaffold request. It does not delete
source NBT, arbitrary datapack files, worlds, or the existing large runtime conflict log. A JVM crash log
created by the remediation's own failed sandboxed Gradle attempt was removed as generated local output.

## Validation record

| Validation | Result |
|---|---|
| `compileTestJava -PcodexBuildDir=.codex-build --max-workers=1` | Passed; compiled production/tests and validated/generated all four blueprints. |
| Focused audit regression set | Passed; workflow, artifact, scaffold, NBT budget, village rarity, FAQ bound, changelog metadata, migration, and ownership tests. |
| `runData -PcodexBuildDir=.codex-build --max-workers=1` | Passed; created a verified catalog containing 20 mods and 2,874 registered blocks and left tracked generated resources unchanged. |
| `structureGenTest -PcodexBuildDir=.codex-build --max-workers=1` | Passed against that verified catalog: 71 tests, 0 failures/errors, 3 Windows symlink-permission skips. |
| `test -PcodexBuildDir=.codex-build --max-workers=1` | Passed: 773 tests, 0 failures/errors, 3 skips. |
| `build -PcodexBuildDir=.codex-build --max-workers=1 --rerun-tasks` | Passed against the exact final Java source; all 16 tasks executed, including 773 tests, `verifyBuildIsolation`, `check`, generation, and packaging. The deliberately stale pre-refresh catalog was rejected and safe vanilla fallback was used for this forced pass. |
| Final `runData` then `build` CI-parity sequence | Passed; the refreshed exact-source catalog was accepted with 20 mods/2,874 blocks, generated sources stayed unchanged, and the final build/check/package gate succeeded. |
| JSON parse across main/generated resources | Passed. |
| Workflow YAML parse with duplicate-key rejection | Passed for build, CodeQL/Qodana, and publish workflows. |
| `git diff --check` | Passed; line-ending notices are repository/Windows normalization warnings, not whitespace errors. |
| Exact packaged JAR inspection | Passed: root `logo.png`, generated `village.nbt`, and metadata are present; AT and Curios JAR entries are absent; Curios metadata is optional. |

Built artifact: `.codex-build/libs/wildernessodysseyapi-4.2.0.jar`<br>
SHA-256: `C22DB3F1A27FB99F8E5E65B119DB40A8060EE27EDF43D0AA72ED0EE509042DB8`

The build reported Gradle deprecation warnings for future Gradle 10 compatibility and one deliberately
retained deprecated `WaveAnimator` compatibility call. They did not fail this Gradle 9.7/NeoForge
build and are not evidence of an in-game failure.

## Runtime checks still required

Automated validation cannot prove rendering, mixin application, optional-mod combinations, or gameplay
feel. Before a public release, use a copy of an existing world and a fresh world to verify:

1. dedicated-server startup/shutdown with the required pack and with Curios absent;
2. `/worldupgrade status`, legacy `/updateworldversion`, retries, and explicit completion;
3. one-time Secret Order village placement in newly generated jungle chunks;
4. Echo generation and preservation of doors/leaves placed after generation;
5. over-budget `/modpackstructures place` refusal without loading chunks or changing blocks;
6. a generated scaffold loaded as a real datapack; and
7. bunker hostile-spawn protection before and after a full server restart.
