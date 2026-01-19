# Optimization Migration Plan

This document outlines which optimization-focused classes should move into a dedicated optimization project and which should remain in the core Wilderness Odyssey API because other systems depend on them.

## Ready to Move to the Optimization Project
These files are self-contained optimization utilities or are only consumed by other optimization components (chunk streaming, async workers, or client performance checks). They can relocate with the expectation that the main mod will depend on the new optimization module.

| Path(s) | Notes |
| --- | --- |
| `src/main/java/com/thunder/wildernessodysseyapi/chunk/*` | Chunk streaming/lighting throttling stack (e.g., `ChunkStreamManager`, `ChunkTickThrottler`, `ChunkDeltaTracker`) with callers limited to optimization commands and mod bootstrap wiring. |
| `src/main/java/com/thunder/wildernessodysseyapi/async/*` | Asynchronous task framework (`AsyncTaskManager`, `AsyncThreadingConfig`, `MainThreadTask`) used to drive chunk streaming workloads and exposed via `AsyncStatsCommand`. |
| `src/main/java/com/thunder/wildernessodysseyapi/io/*` | IO helpers (`BufferPool`, `CompressionCodec`, `IoExecutors`) backing chunk streaming; current non-optimization touch points (e.g., `NbtCompressionUtils`) should be refactored before extraction. |
| `src/main/java/com/thunder/wildernessodysseyapi/command/ChunkStatsCommand.java`, `DebugChunkCommand.java`, `AsyncStatsCommand.java` | Commands that surface chunk/async metrics and should travel with the systems they report on. |
| `src/main/java/com/thunder/wildernessodysseyapi/ModPackPatches/client/LoadingStallDetector.java` | Client load stall watcher; only referenced by the loading overlay mixin and can be supplied by the optimization project. |
| `src/main/java/com/thunder/wildernessodysseyapi/util/NbtDataCompactor.java` | Payload compaction helper used by chunk disk adapters; relocates alongside chunk IO code. |
| `src/main/java/com/thunder/wildernessodysseyapi/util/LightweightMath.java` | Standalone math micro-utilities; safe to rehome with other optimization helpers. |

## Keep in Core Because Other Systems Depend on Them
These files are performance-adjacent but are coupled to other game systems (analytics, worldgen, AI story integration). They should remain in this project unless those systems are also migrated or reworked to consume the optimization project as a dependency.

| Path(s) | Dependency reason |
| --- | --- |
| `src/main/java/com/thunder/wildernessodysseyapi/AI/AI_perf/*` | Shared with AI story features (`AI_story/AIClient` uses `MemoryStore`/`requestperfadvice`) and analytics (`AnalyticsTracker` pulls `PerformanceAdvisor`), so moving would break non-optimization AI tooling. |
| `src/main/java/com/thunder/wildernessodysseyapi/MemUtils/*` | Referenced by core mod bootstrap (`WildernessOdysseyAPIMainModClass`) and analytics metrics (`AnalyticsTracker`), making it a shared runtime utility rather than a pure optimization module. |
| `src/main/java/com/thunder/wildernessodysseyapi/util/NbtCompressionUtils.java` | Used by structure block mixins (`mixin/StructureBlockEntityMixin.java`) in worldgen and by chunk disk IO, so it bridges optimization and world structure features. |
| `src/main/java/com/thunder/wildernessodysseyapi/util/StructureBlockCornerCache.java` and `WorldGen/structure/bridge/StructureBlockCornerCacheBridge.java` | Cache consulted by structure block mixins (`mixin/StructureBlockEntityMixin.java`, `mixin/BlockEntityMixin.java`) and worldgen scaffolding; worldgen would break if the cache moved. |
| `src/main/java/com/thunder/wildernessodysseyapi/WorldGen/structure/LargeStructurePlacementOptimizer.java` | Called directly from structure placement logic (`WorldGen/structure/NBTStructurePlacer.java`), so it is tightly bound to worldgen internals. |
| `src/main/java/com/thunder/wildernessodysseyapi/mixin/LoadingOverlayMixin.java` | Depends on `LoadingStallDetector`; if that class moves, this mixin must be updated to consume it from the new project before relocation. |

## Migration Guidance
- Migrate the "Ready to Move" set first, exporting them as a reusable optimization dependency for the main mod.
- For items in the "Keep in Core" list, either leave them in place or refactor their dependents so they can consume the new optimization project via an API rather than direct package access.
- After moving, update Gradle settings to point to the optimization project and adjust imports/mixins accordingly.

## Nova API Overlap (Replace and Delete Candidates)
Nova API already ships many of the same optimization classes. Once the main mod depends on Nova API, the duplicated classes here can be deleted after updating imports and mixins to the Nova packages.

| Current project scope | Nova API equivalent | Action |
| --- | --- | --- |
| `chunk/*` | `com/thunder/NovaAPI/chunk/*` | Replace usages with Nova API classes, then delete local chunk classes. |
| `async/*` | `com/thunder/NovaAPI/async/*` | Point async initialization/metrics to Nova API, then remove local async classes. |
| `io/*` | `com/thunder/NovaAPI/io/*` | Switch IO helpers to Nova API versions; remove local copies after chunk IO migrates. |
| `util/NbtDataCompactor.java`, `util/NbtCompressionUtils.java` | `com/thunder/NovaAPI/util/*` | Update worldgen mixins and chunk IO to Nova utilities, then delete local versions. |
| `AI/AI_perf/*` | `com/thunder/NovaAPI/AI/AI_perf/*` | Rewire analytics and AI story code to Nova API, then remove local AI_perf package. |
| `MemUtils/*` | `com/thunder/NovaAPI/MemUtils/MemoryUtils.java` | Adopt Nova’s MemoryUtils in bootstrap/analytics; drop local MemUtils afterward. |
| `analytics/*` | `com/thunder/NovaAPI/analytics/*` | Shift analytics to Nova API implementation, then delete local analytics classes. |

## Nova API Optimizations to Consider Adopting
These components exist only in Nova API and can further reduce code in this project if adopted:
- Saving system stack (`SavingSystem/*`): async saves, autosave management, Kryo/Chunk save managers.
- Render optimizations (`RenderEngine/*`): instanced rendering helpers, render thread interception, and API surface for render threading.
- Async worldgen hook (`optimizations/AsyncWorldGenHandler.java`): worldgen async helper absent in the main mod.
- Thread utilities (`utils/ThreadPoolFallbackManager.java`, `utils/ThreadMonitor.java`), background scheduler (`novaapi/task/BackgroundTaskScheduler.java`), and regional cache (`novaapi/cache/RegionScopedCache.java`).

## Deletion Guardrails
- Before deleting local classes, ensure every caller (including mixins and commands) imports the Nova API package paths.
- For worldgen mixins using `NbtCompressionUtils` or structure caches, confirm Nova equivalents are wired in or provide shims to avoid breaking worldgen.
- When replacing analytics/AI_perf/MemUtils, verify AI story and analytics flows compile against Nova API to avoid regressions.

## Quick References
- Classes to migrate to Nova: see `docs/OptimizationMoveList.md`.
- Classes to delete after Nova adoption: see `docs/OptimizationDeleteList.md`.
