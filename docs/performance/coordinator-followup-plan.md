# Opt-in performance coordinator continuation

User authorization: continue through the remaining phases, with the whole coordinator configurable and disabled by default. The original requirements still condition Phases 3 and 4 on profiling and safety evidence.

## Design

`performance.coordinator.enabled=false` gates diagnostics, governance, optional-job admission, dirty tracking and save drain. It does not change the pre-existing Background/Tick/Data Engine switches. Configuration is captured per server session; restart applies changes. Observation OFF also disables governance because an unobserved optimization cannot establish spare capacity. AUTO and ON both obey all safety limits. `performance.safeMode=true` remains the default; no experimental persistence or chunk-priority policy is enabled.

Phase 1 uses a pure `CoordinatorGovernor` and a small session bridge under `performance/coordinator`. Existing end-of-tick schedulers obey a shared remaining time allowance; optional jobs use explicitly classified admission on existing executors. Existing persistence and gameplay jobs keep their contracts. Shutdown rejects and interrupts only explicitly disposable jobs; required quest/telemetry/vanilla persistence remains with its owner. No new executor, arbitrary third-party cancellation, or generation policy.

Phase 2 uses a bounded dimension/position registry under `saving/coordinator`. Narrow dirty-state and native-write hooks feed metadata and completion outcomes. The adapter only retrieves already-loaded full chunks on the server thread and invokes native `ChunkMap.save`; normal serialization/events/attachments/IOWorker ordering remain intact. Pending writes retain detached keys/generations only. Completion failures re-mark loaded chunks and disable optional drain, pending writes are re-marked before native shutdown/unload, and metadata overflow never discards Minecraft dirty state. Save-mod/C2ME candidates veto drain, regardless of uncertain config activity. Cold-age eligibility and measured cost govern admission. No chunk scan, force load, format change or additional IO queue.

Phases 3 and 4 are investigation decisions, not promises to introduce invasive code. The inspected pipeline already offloads region IO; no real-pack profile currently demonstrates a remaining detached-persistence or chunk-priority bottleneck. Keep those changes absent until the evidence thresholds are met.

## Execution and validation

Work in the current checkout to preserve the uncommitted Phase 0 implementation and unrelated anomaly/rift changes. No broad staging, commits or branch reset. Only this agent runs Gradle. Use JDK 21, the wrapper, `-PcodexBuildDir=.codex-build --no-parallel`.

- [x] Master gate: regression for disabled defaults, enabled/OFF session behavior, honest command output; compile and focused tests.
- [x] Governor: deterministic tests for entry, overload, JVM/queue pressure, hysteresis, cooldown, cost/deadline sharing and OFF; integrate existing schedulers.
- [x] Optional jobs: real bounded-executor tests for rejected admission, queued/running disposable cancellation and required-job preservation; connect only callers with disposable contracts.
- [x] Save model: test coalescing, dimension isolation, hot/cold eligibility, cost admission, pending-generation ordering, errors, overflow and lifecycle clear.
- [x] Save adapter: native private-save accessor and narrow write hook, server-owned events, ownership veto and fail-closed readiness; compile and targeted Minecraft validation if the isolated run is available.
- [x] Reporting: module states, budgets/watchdog/optional jobs, observed save backlog and limits, config/trial guide, explicit Phase 3/4 decisions.
- [x] Fresh review, focused/root tests and packaging. Record runtime versus source/unit/package proof separately; full-suite failures are recorded below.

## Review focus

1. Disabled master config must bypass all coordinator collection and policies, including recording commands.
2. An optional native save failure or unfinished write must leave dirty state for the native final/unload path.
3. A mutation during a pending write must survive an older completion.
4. Disposable cancellation must not interrupt persistence or stale jobs in a later world.
5. Budget overruns cannot be preempted inside native serialization; measured cost must prevent knowingly admitting another expensive save.

## Ledger

2026-10-11: current checkout contains Phase 0 changes plus unrelated anomaly/temporal-rift work. Preserve all. Read-only audit reconfirms native save serializes on the server, posts the NeoForge event, and already dispatches region IO. Three Java processes are idle Gradle daemons; no active wrapper or development worker was reported.

Ruling: the master switch includes Phase 0 observation because the user said the whole coordinator; no clarification is needed. Ruling: conditional Phases 3/4 retain their original profiling gates despite authorization to continue. Ruling: runtime ownership presence vetoes optional saving conservatively; third-party config contents are not guessed.

2026-10-11 implementation: Phase 1 policy, two in-flight disposable jobs, shared optional ledger and aggregate four-second existing-executor shutdown deadline; Phase 2 bounded coordinate index and server-thread native-save adapter. Master `enabled=false`, governor AUTO and save drain OFF. Observer OFF disables controls. Config is the registered `config/wildernessodysseyapi/wildernessodysseyapi-server.toml`, verified in the isolated Minecraft log.

Independent review found a missing `ChunkHolder.isReadyForSaving()` check. The native GameTest reproduced a save while a loaded holder had a generation reference; the adapter now preserves that native barrier. The same GameTest passed after correction, including direct block dirty notification, stored chest NBT, pending-write re-mark before final save and a controlled serialization-event exception with normal native retry. Focused validation passed 119 tests without failures/errors/skips. The fixed GameTest passed one required test. These checks do not prove asynchronous disk-write failure, transformed pending-mutation/unload recovery, actual-pack data reload safety or measured improvement.

Phase 3 decision: native IOWorker already handles async/coalesced region writes, and the affected-pack profile has not established a remaining detached-persistence bottleneck. Phase 4 decision: native priorities/generation dependencies remain owned by Minecraft and installed scheduler candidates; no affected-pack profile establishes a remaining priority bottleneck. Neither invasive phase is installed. Actual-pack profiling and copied-world acceptance remain the stated requirements for a later intervention.

Final checks used JDK 21.0.12 and the checked-in wrapper, isolated output and `--no-parallel`:

| Check | Result | Evidence |
|---|---|---|
| `:compileJava :performanceDiagnosticsTest` | Passed; 119 tests, no failures/errors/skips | `.codex-build/performance-audit/coordinator-focused-final-2026-10-11.log`; repeated successfully within the final combined invocation |
| `runGameTestServer -PcoordinatorGameTests` | Passed; one required transformed native-save test | `.codex-build/performance-audit/coordinator-gametest-final-2026-10-11.log` |
| Readiness regression before fix | Failed at the intended pending-generation assertion | `.codex-build/performance-audit/coordinator-readiness-red-2026-10-11.log` |
| `:compileJava :performanceDiagnosticsTest :test :build` | Full suite failed; 1,560 tests, three failures, four skips, no errors; build did not complete | `.codex-build/performance-audit/coordinator-final-validation-2026-10-11.log` |
| `:assemble` | Passed independently | `.codex-build/performance-audit/coordinator-package-validation-2026-10-11.log` |

The full-suite failures are in preserved unrelated changes: `AnomalyPostResourceTest` resolves two shader fixture paths relative to ModDev's test working directory instead of the configured project directory; both source resources exist. `OpenGlDeprecationBoundaryTest` finds direct OpenGL access in the pre-existing `AnomalyPostEffectController` and `RiftPostProcessing` changes outside its allowed boundary. No coordinator source is named by those failures. They were investigated and left intact, not excluded or rewritten. The full build is not reported green.

Read-only package inspection confirms the governor/config/disposable classes, save adapter/index/native access, three new mixins and registrations, and gzip-normalized isolated GameTest template in `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`. This artifact includes the current checkout's unrelated changes and is not an isolated release. Scoped diff whitespace checks passed. No staging, commit, release or real gameplay-world change was performed.
