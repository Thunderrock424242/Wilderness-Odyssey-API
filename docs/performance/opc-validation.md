# Odyssey Performance Core validation

Date: 2026-10-17. Current checkout: `in-dev`; baseline commit `a9b56c665406931b977e628cef251fc8eb5552f7`. Changes remain in the working tree; no commit, push or release was created. Validation used the checked-in wrapper, isolated `.codex-build`, and serialized Gradle execution.

## Final result

The complete final invocation exited **0**, `BUILD SUCCESSFUL in 2m 31s`:

```powershell
.\gradlew.bat :compileJava :performanceDiagnosticsTest :test :build :runGameTestServer '-PcodexBuildDir=.codex-build' -PcoordinatorGameTests -PauditGameTestRun=opc-20261017 --no-parallel --no-daemon
```

| Validation | Result |
| --- | --- |
| `:compileJava` | Passed. Java 21 source compiled through ModDevGradle. |
| `:performanceDiagnosticsTest` | Passed: 137 tests, 36 suites, zero failures/errors/skips. Includes chunk lifecycle architecture, coordinator/save models, worker lifecycle, queue contracts, configuration, ownership and loading diagnostics. |
| `:test` | Passed: 1,621 tests, 433 suites, zero failures/errors, four skips. |
| `:build` | Passed. This repository explicitly wires build to assemble; full tests were therefore requested separately. |
| `:runGameTestServer` | Passed: both required coordinator-namespace tests; fresh isolated directory `.codex-build/run-audit-opc-20261017`. Server saved all dimensions and shut down normally. |
| Independent source review | Three important issues reproduced and corrected: adaptive required-priority admission, accepted-result loss, and intermediate-priority starvation. Follow-up review found no important issue remaining in the fixes. |
| Diff/artifact inspection | `git diff --check` passed. New source/document whitespace checked separately. Packaged 5.0.0 JAR contains OPC core, legacy facade and new Data Engine GameTest. Generated output remains ignored. |

JVM detail: `java -version` on PATH reports Oracle 21.0.12; the Gradle runtime used Temurin 21.0.6, as confirmed by the captured JVM crash header. Both are Java 21. No JDK installation or system setting was changed.

Local evidence:

- `.codex-build/opc-acceptance.log`: final complete invocation, including native GameTest result and Gradle exit.
- `.codex-build/test-results/performanceDiagnosticsTest/` and `.codex-build/reports/tests/performanceDiagnosticsTest/`.
- `.codex-build/test-results/test/` and `.codex-build/reports/tests/test/`.
- `.codex-build/run-audit-opc-20261017/logs/latest.log`: `All 2 required tests passed`, followed by ordinary save/shutdown.
- `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`: current artifact. A pre-existing 4.2.0 JAR is also present locally; it is not the newly built artifact.

## Skipped full-suite tests

These are existing conditional tests; none was deleted or disabled by OPC:

1. `QuestPublicationStoreTest.containedPathsRejectSymbolicDirectories`: this Windows account cannot create the required symbolic link.
2. `StructureGenCliCatalogTest.rejectsCatalogAncestorSymbolicLinkWhereSupported`: conditional symbolic-link support.
3. `StructureGenCliCatalogTest.rejectsCatalogFileSymbolicLinkWhereSupported`: conditional symbolic-link support.
4. `BunkerStructureRegressionTest.readsAndInspectsTrackedBunkerWithoutMutatingIt`: requires the separate opt-in 4 GiB fixture task/property. Not run for this performance consolidation.

## Regression coverage and native proof

Existing contracts preserved: queue priority order and live server allowance, cooperative deferral, capacity, expiry/coalescing, governor entry/overload/recovery/watchdog behavior, native ownership vetoes, required shared IO, reload preservation and stale-lifecycle rejection.

New/expanded coverage proves:

- AsyncComputeManager executes on the shared CPU pool and its shutdown does not shut that pool down.
- Worker-to-server result publication is observable only after queue insertion; every accepted successful result fits within the outstanding reservation.
- Fair priority turns persist across single-task passes and rotate past perpetually deferred idle work to intermediate priorities.
- Both scheduler APIs preserve critical bypass in adaptive mode while still honoring live NeoForge allowance.
- Data/Tick/Background/Save accounting shares actual costs, module opportunity and a wall deadline; overload does not bank catch-up work.
- The legacy entry point remains present; the new mode defaults to LEGACY and the old coordinator default remains false.
- Coincident raw JVM consumers share a sample and retain its actual capture timestamp.

`OpcDataGameTests.opcEntryDefersOptionalDataWithoutLosingCriticalCallbacks` uses the real transformed server/Data Engine: a critical callback executes during zero-budget ENTRY, a noncritical event remains queued, and the event executes after the temporary core session is cleared. The fixture asserts its isolated/default-disabled precondition, cleans OPC in `finally`, and never restarts Data Engine or modifies native saving.

The unchanged `CoordinatorSaveGameTests.nativeSaveRetainsChestDataAndFailureLeavesDirtyForNormalRetry` verifies generation-reference readiness, observed dirty tracking, native asynchronous chest inventory persistence, and controlled serialization-event failure retaining dirty state for ordinary retry. Its expected injected failure produces a warning/error in the log; the test itself passed. This is fixture proof, not arbitrary world/modpack safety certification.

## Intermediate failures and recovery

- Baseline compile passed, but 1 of 127 focused tests failed: `AsyncComputeManagerTest.computedResultWaitsForExplicitServerThreadDrain`. Counter publication could precede queue insertion. Consolidating result publication into the session lock corrected this; no sleep was added to conceal it.
- New shared-worker/fairness/OPC tests were observed failing before implementation. Later review regressions for result reservation, intermediate priority and adaptive critical admission also failed before their corrections. Red-run logs are `.codex-build/opc-review-red.log`, `.codex-build/opc-jvm-red.log`; the JVM sampler test initially could not compile because its requested API did not yet exist.
- The architecture test rejected adding Data Engine control references to the native-chunk save fixture. The new test was moved to its owning `dataengine/OpcDataGameTests.java`, and the architecture guard was preserved.
- A later Gradle daemon crashed while packaging `:jar`: native `EXCEPTION_ACCESS_VIOLATION (0xc0000005)` in `zip.dll+0x2942`, with `Deflater`/Gradle ZIP writer frames. This was a JVM/native packaging failure, not a Java compiler error or evidence that OPC code crashed. The crash log was preserved as `.codex-build/opc-gradle-hs_err_pid62576.log`.
- One recovery invocation used a fresh `--no-daemon` JVM and passed compile, 137 focused tests, 1,621 full tests and packaging (`.codex-build/opc-final-recovery.log`). The subsequent final invocation above included current final sources and both GameTests, and passed. No cache deletion, generated-JAR editing, permission changes, security changes or unrelated process termination was used.
- StructureGen's normal packaging dependency reports a stale optional content-catalog fingerprint and falls back from that modded catalog. All 15 blueprints validated/generated without errors. No `runData`, resource rewrite or catalog manipulation was used for OPC.

## Changed-file map

| Responsibility | Actual source files |
| --- | --- |
| Core policy/config/raw measurements/shared deferred engine | New `performance/core/OdysseyPerformanceCore.java`, `OpcConfig.java`, `DeferredWorkScheduler.java`, `JvmMetrics.java`; modified `performance/coordinator/PerformanceCoordinator.java`, `CoordinatorGovernor.java`, `config/PerformanceServerConfig.java`. |
| Scheduler/worker adapters and configuration | Modified `performance/tickengine/TickEngine.java`, `TickWorkScheduler.java`; `performance/background/BackgroundEfficiencyManager.java`, `BackgroundWorkScheduler.java`, `AsyncComputeManager.java`, `config/BackgroundEfficiencyConfig.java`. No public class was deleted; the duplicate scheduler algorithms and secondary executor implementation were removed. |
| Production Data integration | Modified `dataengine/DataEngine.java`, `interest/InterestManager.java`, `debug/DataEngineDebugIntegration.java`; new `dataengine/OpcDataGameTests.java`. |
| Diagnostics and command integration | Modified `diagnostics/performance/PerformanceDiagnostics.java`, `PerformanceDiagnosticsConfig.java`, `PerformanceCommand.java`, `JvmPerformanceSampler.java`; `performance/ram/RamAdvisorService.java`, `RamAdvisorEvents.java`. |
| Unit regressions | New `performance/core/OdysseyPerformanceCoreTest.java`, `JvmMetricsTest.java`, `performance/coordinator/OpcCompatibilityTest.java`; expanded background `AsyncComputeManagerTest.java`, `BackgroundWorkSchedulerTest.java`, `AnalyticsBatcherTest.java` and diagnostics `PerformanceDiagnosticsConfigTest.java`. Paths in this row are below `src/test/java/com/thunder/wildernessodysseyapi/`. |
| Documentation | New `docs/performance/opc-audit.md`, `opc-adapter-audit.md`, `opc-diagnostics-audit.md`, `opc-design.md`, and this validation record. |

Production Java paths in this table are below `src/main/java/com/thunder/wildernessodysseyapi/`. Existing `SavingCoordinator`, native save mixins, gameplay resources, world data schemas and feature authority implementations were not changed.

## Manual acceptance still required

On a disposable copied world/affected modpack:

1. Begin with mode LEGACY, old coordinator disabled, and record a comparable baseline. Confirm existing weather/water/vegetation/ecology behavior, joining/rejoining and Save & Quit.
2. Set `performance.opc.mode=OBSERVE`, restart, run `/wo perf status`, `/wo perf queues`, `/wo perf ram`, `record start`, then reproduce and export. Confirm idle state and ownership reports reflect the actual instance. Do not infer patch activity from mod presence.
3. Set mode ADAPTIVE, leave safeMode true and saveDrain OFF, restart and repeat the same workload. Inspect per-module cost/backlog/cooldowns, eventual deferred progress and recovery. Compare MSPT/CPU/thread/queue data; do not infer FPS or speedup from the unit suite.
4. Test no debug subscribers, one subscribed operator, unsubscribe and world restart. Confirm ordinary players receive no operator debug updates and a new subscription gets current data.
5. Use two clients for weather/water/vegetation/ecosystem synchronization, required updates, disconnect cleanup and optional-worker saturation. Verify copied-world save/reload and installed optimization-mod combinations separately.
6. Keep GPU profiling manual with `/wovram`; verify real client/driver/shader behavior and cleanup separately. Loading diagnostics must still report a stall without relying on server tick progress.
7. Roll back to mode LEGACY and restart. Existing numeric tuning keys remain in place and no world/config migration is required.

No FPS/MSPT/RAM/loading/exit speedup, real client visual acceptance, multiplayer acceptance, copied-world acceptance or native-crash prevention is claimed by these results.
