# Wilderness River Network Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` inline. The user has
> explicitly requested implementation through feasible safe milestones.

**Goal:** Establish bounded deterministic river planning and integrate currents
without changing water inventories or unsafe terrain generation.

**Architecture:** Pure immutable planning under the existing water worldgen
package. Live inspection is loaded-only. Current interpolation consumes the
existing synchronized watershed state and existing physics/render owners.

**Tech Stack:** Java 21, Minecraft 1.21.1, repository NeoForge/Gradle wrapper,
JUnit Jupiter, existing offline `waterSimulationTest` and GameTest profiles.

**Spec:** `docs/watersystem/river-network.md` and the supplied complete brief.

## Global constraints

- Preserve all pre-existing dirty files on `codex/wilderness-river-network`.
- One Gradle/Minecraft process at a time, `-PcodexBuildDir=.codex-build`.
- No chunk generation requests, new schedulers, water ledgers or save migration.
- No terrain writes until generation safety and continuity are established.
- Report the incomplete overall system explicitly.

## Review focus

- Unknown halo samples must invalidate uncertain outlets rather than drain pits.
- Independent overlapping windows must agree on resolved downstream edges.
- Sampling budgets and coordinate overflows must reject before calling providers.
- Interpolation must not blend unrelated basins or amplify Manning speed.
- Physical and visual currents must use one canonical/background composition.

## Task 1: Planner

- [x] Write behavior tests for `RiverTerrainSnapshot.capture` and
  `RiverNetworkPlanner.plan` in the mirrored test package; run and observe RED.
- [x] Implement immutable keyed snapshots, bounded capture, deterministic D8
  routing, bounded area accumulation, explicit unresolved boundaries and a
  priority-flood potential-spill pass. No implicit lake volume.
- [x] Run focused planner tests; record exact results.

## Task 2: Live inspection

- [x] Add world-backed tests for loaded snapshot capture without metadata or
  block mutation, and absence of missing-chunk requests.
- [x] Implement `LoadedRiverTerrain` and register `/wowater riverplan` through
  `WaterDebugCommand`; keep capture bounded to 100 chunk-center samples.
- [x] Compile and run the isolated river GameTest namespace using a fresh
  `auditGameTestRun` name; inspect actual exit and test results.

## Task 3: Current integration

- [x] Write RED tests for convex interpolation, missing samples, basin separation
  and canonical/background vector selection.
- [x] Implement `RiverCurrentField`; integrate through `WatershedServices` and
  existing physical and client snapshot consumers, removing redundant entity
  correction. Keep inventories and water surface elevations unchanged.
- [x] Run water and rendering subsystem regressions and current GameTests.

## Task 4: Review and report

- [x] Review only this task's changes; obtain one independent final code review.
- [x] Package via normal Gradle; document commands, evidence and limitations.
- [x] Report current functionality and concrete terrain-writer prerequisites.

## Execution ledger

- Baseline: wrapper `compileJava` completed successfully in 5 s, Java 21, isolated
  output, including the user's existing dirty OPC/render/coastal changes.
- Ruling: keep work in the requested dedicated branch on this checkout; copying
  or stashing the extensive in-progress architecture would lose the target
  integration context. No pre-existing work is staged or committed.
- Ruling: no unsafe automatic channel writer. Noise heights are not final
  decorated terrain, and existing runtime drainage is not a generation oracle.
  The cost is an explicitly partial delivery of the complete river brief.
- Planner/current field initial tests: 17 passed. Final expanded water suite:
  149/149; rendering: 324/324; final world-backed river + water safety: 25/25.
- Review found a pre-existing units mismatch at the new integration boundary and
  missing mesh invalidation on smoothing changes. Added RED regressions, corrected
  both, reran suites, and obtained follow-up review with no remaining P1/P2 finding.
- Added the bounded synthetic benchmark through the established Gradle offline
  runtime. Results and their limits are in the subsystem document.
- Root `:build` passed; five key packaged class files match current compiled
  bytes and the river namespace template is present. Scoped whitespace checks
  passed. No interactive client or complete worldgen/compatibility acceptance.
- Ruling: repository HEAD advanced externally to `8f0d5552` while work continued,
  including the initial river documents. Preserve that commit and the concurrent
  coastal/build work; do not reset or stage unrelated changes. No commit/push was
  made by this task.
