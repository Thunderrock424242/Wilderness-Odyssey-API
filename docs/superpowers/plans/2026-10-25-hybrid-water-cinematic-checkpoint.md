# Hybrid Water Cinematic Checkpoint Implementation Plan

> **For agentic workers:** Execute inline using the repository workflow and test-driven development. The user explicitly authorized implementation in phases.

**Goal:** Complete a tested cinematic surface checkpoint while preserving existing water ownership.

**Architecture:** Extend `DepthWaveResponse`, `WaterSurfaceEquation`, and the active shader. Use existing frame weather, camera-relative disturbance selection, snapshots, rendering, and build tasks.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.x, Java 21, GLSL 150, JUnit 5, existing LWJGL.

**Spec:** `docs/watersystem/hybrid-water-engine-2.md`, first checkpoint design and the user's Hybrid Water Engine 2.0 master request.

## Global constraints

- One authoritative water state and one coordinated renderer.
- Preserve unrelated uncommitted work and existing configuration defaults.
- No remote Git changes, generated JAR edits, new scheduler, or force-loading.
- One Codex-controlled Gradle/NeoForm/development process at a time.
- This checkpoint does not complete the physical-region or dam phases.

## Review focus

- Frozen water must preserve base fill and ocean-weighted tide.
- Dry/unloaded continuity anchors must remain exactly still.
- Null/nonfinite environmental inputs must produce bounded values.
- Public existing surface overloads must retain unfrozen behavior.
- Shader and CPU arithmetic must agree for tested encoded depth and spectra.

### Task 1: Smooth the shared shoaling envelope

Files: `water/wave/DepthWaveResponse.java`, `shaders/core/gerstner_water.vsh`, and `water/wave/DepthWaveResponseTest.java` under their existing source roots.

- [x] Add regressions for zero depth derivative at the dry/3m/16m endpoints,
  bounded finite scaling, and existing ocean/inland response.
- [x] Run the targeted tests and observe the slope regression fail.
- [x] Ease both bands with cubic smoothstep in Java and GLSL.
- [x] Rerun targeted numerical tests and existing surface regressions.

### Task 2: Match frozen surface and disturbance inputs

Files: existing `WaterSurfaceEquation.java`, `ClientWaterImmersion.java`, and tests under `water/render`.

- [x] Add a frozen-overload regression for calm/storm waves, impulses, tide,
  wet-edge taper and invalid fractions; observe failure before implementation.
- [x] Add a compatible frozen-fraction overload and route immersion through
  frame weather, double tick age, and the camera's impulse-selection origin.
- [x] Add a hidden OpenGL transform-feedback regression using the production
  vertex shader and existing test dependencies; compare sampled GPU/CPU heights.
- [x] Run focused tests, water safety GameTests and packaging; immediately stop
  an accidental overlapping own invocation. Final validation completed despite
  a later separate-chat invocation; see the report for the exact process scope.

### Task 2 follow-up: Bound the existing disturbance consumers

Files: `WaterSurfaceDisplacement.java`, `WaterEntryEventHandler.java`,
`ClientWaveEntityEffects.java`, `RippleRenderer.java`, and their existing/new tests.

- [x] Reproduce missing range/lifetime APIs with failing tests.
- [x] Share the existing 96-block cosmetic admission policy across entry/wake
  consumers before their water/SPH sampling; preserve all server authority.
- [x] Age ripple rings with the same double-precision frame tick.
- [x] Pass nine focused follow-up tests, then the broad ordinary water/weather
  command and packaging: 580 tests, zero failures/errors/skips.

### Task 3: Review and record the checkpoint

- [x] Review scoped diff, inputs, fallback behavior, unrelated-file preservation
  and generated-output hygiene.
- [x] Record actual commands, counts, driver evidence, failures and limitations
  in the checkpoint report; update overview/testing links.
- [x] Leave changes available locally with no automatic commit, push or merge.
