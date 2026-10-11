# Anomaly Wilderness Implementation Plan

> **For agentic workers:** Use focused parallel workstreams for the independent worldgen, block content, and client presentation tasks. The main agent owns integration, Gradle execution and review. Steps use checkbox syntax for tracking.

**Goal:** Implement the requested natural anomaly ecosystem with five zones, safe stationary NBT islands, distorted blocks, restrained client effects and developer tools.

**Architecture:** Reuse Lithostitched biome injection, vanilla noise terrain and StructureGen blueprints. Extend `AnomalyBlocks` and the unified config/payload registration; keep temporal rifts and weather under their existing owners.

**Tech Stack:** Java 21, NeoForge 1.21.1, Minecraft data-pack JSON, GLSL and the existing structure-blueprint generator.

**Spec:** [Anomaly wilderness design](../specs/2026-10-10-anomaly-wilderness-design.md)

## Global constraints

- Preserve existing registry IDs, worlds, gateway travel data and unrelated diagnostics edits.
- No force-loading, runtime terrain movement, server rendering dependencies, generated JAR editing, new dependencies or security-setting changes.
- One Codex-controlled Gradle/NeoForm/Minecraft process at a time; all checks run by the main agent.
- Author generator-owned structures through source blueprints. No automatic unbounded flowing-water sources.
- Requirements are already supplied; execute within the requested scope using the repository instruction to implement clear requirements without repeated confirmation.

## Review focus

- Island collision or a structure reference must reject the whole placement before any write.
- Negative coordinates, build limits and missing loaded chunks must remain safe.
- Clients must clear stale intensity across travel, respawn and disconnect.
- Accessibility-disabled motion must not render distortion pulses.
- Shader integrations and existing post effects must retain their ownership.

### Task 1: Biomes, terrain and stationary structures

**Files:** `worldgen/anomaly/*`, anomaly biome/dimension resources, `ModBiomes`, `BiomeCompatibilityBootstrap`, small island blueprints and worldgen/resource tests.

**Produces:** `AnomalyZone` enum with `biomePath()`, `intensity()`, `fromBiome(ResourceKey<Biome>)`; `AnomalyWorldgenConfig.defineCommon(Builder)`; `AnomalyTerrainFeature`; `AnomalyIslandPlacement` inspection/place API for debug tools.

- [x] Add behavior/resource tests for conifer vegetation, five reachable variants and full placement preflight.
- [x] Implement climate placement and a bounded origin-chunk surface/detail pass, sharing feature ordering.
- [x] Author three vanilla-block small-island blueprints and safe template placement.
- [x] Main agent runs focused tests and reviews structure safety.

### Task 2: Distorted block content

**Files:** `anomaly/block/*`, `anomaly/registry/AnomalyBlocks`, block asset resources and focused tests.

**Produces:** The eight named deferred block constants and `terrainBlocks()` list; natural/dark textured content, client-only crack particles without server ticking.

- [x] Add resource tests for block models, loot, tags and texture completeness.
- [x] Register eight block/item pairs, implement restrained particle animation and author assets.
- [x] Keep existing ore and gateway intact; give worldgen access through constants.
- [x] Main agent validates registration/resource contracts and reviews palette.

### Task 3: Client atmosphere

**Files:** `anomaly/client/*`, `anomaly/config/AnomalyClientConfig`, `anomaly/network/AnomalyAtmospherePayload`, post shaders and visual policy tests.

**Produces:** Payload `(ResourceLocation dimension, int zone, int intensity, int riftIntensity)`; `AnomalyClientState.accept(Level, payload)`; `AnomalyClientConfig.define(Builder)`; client commands.

- [x] Add effect policy tests for clamp, screen-effect scale and disabled motion.
- [x] Replace purple fog, smooth received intensity, add bounded particles and optional independently owned post chain.
- [x] Add reload/disconnect cleanup, shader conflict fallback and client developer toggles.
- [x] Main agent tests policy/codec and reviews side ownership.

### Task 4: Integration and debug tooling

**Files:** unified config specs, feature registry, `ModPayloads`, `anomaly/AnomalyAtmosphereSync`, `anomaly/command/AnomalyDebugCommand`, build task and documentation.

- [x] Define unified common/client anomaly categories, feature and payload registration.
- [x] Add scheduled changed-only local atmosphere sync; reuse authoritative temporal-rift saved data and bounded temporary overrides.
- [x] Add operator developer tools; ensure mutation commands preflight and only use already-loaded chunks.
- [x] Compile, run focused tests, build, inspect diff and report manual acceptance limits.
