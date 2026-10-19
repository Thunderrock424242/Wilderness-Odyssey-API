# WAGE infrastructure audit and implementation plan

Date: 2026-10-17; isolated delivery prepared 2026-10-18. The initial audit was
performed on the shared `in-dev` working tree, including its ongoing OPC package
migration. The final `codex/wage-in-dev` worktree is based on clean `in-dev`
commit `a9b56c66` and carries only WAGE plus the independent `JvmMetrics` sampler
it consumes. The rest of the OPC migration and river/coastal edits are excluded.

## Ownership and feasibility

| Area | Existing owner and finding | Decision |
| --- | --- | --- |
| Client frame lifecycle | `WildernessRenderingFramework`, `RenderFrameContext` | Extend this owner; no second performance manager or executor. |
| Backend capabilities and lifetime | `RenderBackend`, `RenderBackends`, `GPUCapabilities` | Reuse capability evidence, backend generations and asynchronous timer rings. |
| GPU allocation/driver diagnostics | `gpuprofiler.client` in this `in-dev` baseline | Preserve the profiler and its opt-in attribution sessions. Static reported VRAM is capacity/free-memory evidence, not current execution headroom. |
| Adaptive visual quality | `AdaptiveQualityController`, `RenderQualityState`, `RendererConfig` | Preserve its public API and existing quality controls. WAGE controls a specific optional shader optimization, not a second global quality ceiling. |
| Server/background performance | `performance.coordinator.PerformanceCoordinator`, `BackgroundEfficiencyManager`, shared `JvmMetrics` | Keep server budgets and background execution separate. Reuse the cached JVM sampler; never submit Minecraft state to GPU/background work. |
| Terrain | Minecraft/Sodium/Embeddium chunk renderer; Wilderness water section handoff | Defer terrain compute/occlusion/indirect draws. There is no supported chunk-buffer/command ownership contract here. |
| Clouds | `ClientWeatherCoordinator`, `ContinuousCloudFieldAtlas`, `LocalizedCloudRenderer`, `RaymarchedCloudShaders` | Existing synchronized atlas plus GPU procedural density is a complete path. Optimize this shader, preserve every existing fallback and weather authority. |
| Water | `WaterRenderCoordinator`, cached chunk meshes, `WaterShaders`, shared Gerstner equations | Already uses GPU displacement and optics. No duplicate wave compute, GPU readback or simulation changes. |
| Anomalies | Feature-owned post effects and particles | Keep current ownership. A particle-compute prototype needs a supported buffer/submission contract and a measured CPU bottleneck first. |
| Upscaling | `UpscalingProvider`, `TemporalFrameData` | Extend input validation/provider metadata compatibly. Do not register a fake upscaler. Safe world-only resolution control and pre-HUD composition are missing. |
| Vulkan | `docs/rendering-vulkan-migration.md` | Keep all new policy backend neutral. Retain the OpenGL compatibility adapter and its native resource ownership. |

The overlapping instrumentation is complementary: driver/allocation attribution,
whole-frame timing, effect timing and server tick budgets measure different
things. No existing subsystem should be deleted or merged in this pass. The
implementation does share frame/cloud timer lifetime and failure handling in one
small backend-neutral helper rather than repeating those resource rules.

CPU frame duration is elapsed render-callback time, including possible driver
waits. It is not CPU utilization. Delayed GPU timestamps measure a GPU timeline
interval, not utilization, and must retain source-frame identity and freshness.

## Coherent first slice

1. Establish compilation and existing rendering-policy baseline before edits.
2. Add bounded frame statistics and a pure WAGE policy under
   `rendering.performance`: disabled/automatic/performance/quality, warm-up,
   sustained pressure, cooldown, stale-data rejection and immediate safety
   fallback. Use a cloud-specific budget, never a universal GPU-load percentage.
3. Extend `RendererConfig` beneath `renderer.gpuEngine`, default disabled. Keep
   existing quality/water/weather options authoritative. Hide unavailable terrain,
   upscaling and frame-generation options.
4. Wire policy and one cloud pass timer into the existing frame/render lifecycle.
   Use fixed-size timer rings, no retained task queue, and at most one optional
   cloud admission per frame. Release timers/reset observations on unload,
   resource reload, shader ownership handoff and backend replacement.
5. Prototype an exact density bound in the existing cloud fragment shader.
   Because fine noise lies in [0,1], skip its evaluation only when its entire
   possible contribution lies outside the smoothstep transition. Preserve the
   original equation as the disabled/unavailable path. This reduces redundant GPU
   arithmetic; it does not claim new CPU-to-GPU offload or a measured FPS gain.
6. Add behavior tests for scheduling, pressure, freshness, lifecycle, statistics,
   shader density equivalence and incomplete/mismatched temporal inputs. Keep
   existing API construction shapes working.
7. Run focused tests first, then relevant rendering/config regressions. Record
   actual results and a repeatable hardware acceptance/benchmark matrix.
8. Complete sourced DLSS/frame generation/upscaling/Vulkan/worldgen research and
   specify the missing renderer contracts. No native binaries or presentation
   hooks are introduced.

## Review focus

- An unavailable/delayed timer must not accidentally enable the prototype.
- Old-context and old-world samples must not cross lifetime boundaries.
- A shader-pack ownership change must immediately bypass WAGE's cloud path.
- Missing telemetry is unknown, not zero cost or spare execution capacity.
- The optimized density must match the full equation at threshold boundaries,
  including detail strength zero/one and floating-point rounding margins.

## Validation evidence

Baseline and implementation results, actual-driver fixtures and shader
microbenchmarks are recorded in `wage-validation.md`. Controlled in-world FPS
gains require repeatable same-scene captures and are not inferred from shader
batch timings or a short lifecycle acceptance run.
