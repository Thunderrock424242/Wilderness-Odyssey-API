# Wilderness Odyssey Adaptive GPU Engine: first implementation

WAGE is an optional policy in `WildernessRenderingFramework`, using the existing
backend, frame context, config and cloud renderer. It does not own another thread
pool, server budget, terrain renderer or weather simulation. See
[`wage-audit.md`](wage-audit.md) for the pre-implementation ownership audit.

The framework, backend contracts, policy/config and acceptance probe now live
under `com.thunder.wildernessodysseyapi.wage`. See the
[package map and Java API migration](wage-packages.md) for locations and ownership.

## Implemented scope

- A pure, backend-neutral `AdaptiveGpuController`, with four modes, delayed-sample
  freshness checks, warm-up, hysteresis, cooldown and immediate safety fallback.
- One optional workload: exact interval rejection of redundant fine-noise
  evaluation in the existing raymarched cloud density fragment shader.
- A shared lifecycle wrapper for backend-owned four-slot frame/cloud timers,
  with old-context sample invalidation and immediate failed-timer disposal.
- Fixed 240-observation windows for frame cadence, elapsed CPU callback time,
  whole-frame GPU intervals, policy cost and baseline/optimized cloud passes.
- Existing `TemporalFrameData` now validates image dimensions/backend identity.
  Existing `UpscalingProvider` implementations retain their three original
  methods and gain optional reconstruction metadata/input diagnostics.

This is a coherent experimental slice, not the full twelve-phase engine. It adds
no CPU-to-GPU transfer path. The cloud workload already ran on the GPU; WAGE
reduces redundant work inside that workload. Water waves, optical effects and
cloud density were already GPU work and are not counted as new acceleration.

## Configuration

All keys belong to `wildernessodysseyapi-client.toml` through the unified client
spec. No migration of existing config keys or saves is required.

```toml
[renderer.gpuEngine]
enabled = false
mode = "AUTOMATIC"
effectAcceleration = true
debugDiagnostics = false
```

`enabled=false` leaves the experimental shader branch off. `debugDiagnostics=true`
can independently collect baseline cloud timings with acceleration disabled.
`effectAcceleration=false` disables experimental effects while preserving normal
Wilderness rendering. No nonfunctional terrain, upscaling or frame-generation
switches are exposed.

The existing `renderer.targetFrameTime` and `renderer.qualityChangeCooldown` are
reused. The existing `adaptiveQuality`, minimum/maximum quality, cloud settings
and water settings keep their established authority. WAGE neither changes those
values nor raises an effect above its configured ceiling.

| Mode | Cloud interval budget, as fraction of target frame duration |
| --- | --- |
| DISABLED | Experimental shader branch disabled |
| AUTOMATIC | 25% |
| PERFORMANCE | 18% |
| QUALITY | 35% |

These are experimental scheduling budgets, not hardware load estimates or claims
that changing the budget improves FPS. Quality mode waits for a larger measured
cloud cost; the shader optimization itself preserves the original density.

## Decision and workload lifecycle

The frame owner consumes existing whole-frame asynchronous timestamps and the
cloud timer's source-frame-labelled results. Samples must be positive, no more
than eight frames or 500 ms old, not from the future, and no longer than 500 ms.
A fixed 16-slot frame-start history validates elapsed age without retaining worlds
or growing with session length. Results from before a context/policy reset cannot
count toward warm-up. A gap
longer than 500 ms resets warm-up. Cached results are counted once, not once per
poll. Sixty distinct cloud samples warm the policy. Eight sustained pressure
samples and the configured cooldown are needed before activation.

Pressure means smoothed cloud cost exceeds its budget by 12%, or GPU elapsed
time is above 90% of the target while elapsed CPU callback time also misses the
target by 12%. Recovery requires both cloud and GPU costs below their 65%
thresholds, sustained for eight fresh samples and the cooldown. This is a timing
heuristic, not proof of CPU/GPU utilization or a causal bottleneck diagnosis.

Unsupported shader/timer capabilities, active or uncertain shader-pack ownership,
missing/stale timing, heap use at least 90%, or GC collection-time fraction at
least 15% immediately choose the existing renderer path and require warm-up
again. Heap and GC data reuse `JvmMetrics.samplePressure`, with its own one-second
cache and lock. This path never initializes or requests system-memory/process-CPU
counters, so full diagnostics cannot stall render policy while collecting values
it does not use. The
isolated `in-dev` copy includes that independent sampler, without the broader
OPC migration. GC collection time is a pressure proxy, not exact stop-the-world
time.

Known timer failure also rejects the current cloud draw before shader admission.
Failed timers are closed once and are not recreated until a lifecycle reset;
ordinary ring saturation simply skips a sample and is not permanent failure.

There is no retained work queue or cross-thread GPU task. At most one cloud pass
per observed frame is admitted. Timer rings skip unavailable slots without
waiting. The scheduler does not query GPU results synchronously, allocate native
resources outside the backend, or inspect another renderer's chunk buffers.

World unload, backend-generation changes, resolution changes, resource reload,
shader ownership handoff and monitoring disablement close timers and invalidate
observations. The resource listener is registered by the existing client setup
owner. Client event subscribers are restricted to `Dist.CLIENT`; common config
only references pure policy types. Dedicated-server startup, an empty-server soak
and clean save/shutdown were exercised separately from unit tests; the exact
scope is recorded in `wage-validation.md`.

## Exact cloud-density prototype

The existing shader computes
`mix(medium, 0.76 * medium + 0.24 * fine, strength)`. Each noise function lies in
[0,1], and the fractal weights sum to one. The possible detail interval is thus
`medium * (1 - 0.24 * strength)` through that value plus `0.24 * strength`.
If the entire interval is below the smoothstep transition, density is zero; if
entirely above, density is saturated. Only those cases skip the second fractal
noise call. A 0.00001 margin preserves full evaluation near rounding boundaries.
All other cases retain the original expression, sample count and lighting.

The uniform defaults to zero and is updated for every cloud draw. External
shader packs retain their existing world-effect ownership. Missing/failed cloud
shaders still select the existing layered/voxel/vanilla-compatible fallback.

The arithmetic proof alone does not establish driver efficiency or rendering
correctness. Actual-driver image fixtures, lifecycle checks and a controlled
shader microbenchmark are recorded in `wage-validation.md`. These narrower
results do not establish a general in-world FPS improvement.

## Diagnostics and evidence limits

The Rendering debug page reports mode/reason, actual cloud admission, native
upscaling, no generated frames, average and median/p95/p99 frame cadence,
elapsed CPU/GPU timing, cloud budget, accepted/rejected passes, fallback events,
policy overhead and timer failures. Detailed formatting/sorting happens only
when diagnostics are requested. Baseline and optimized cloud intervals are kept
separately using bounded frame labels; changing scenes makes their comparison
non-causal. They are a measurement aid, not an automatic A/B benchmark.

Elapsed CPU callback duration can contain driver waits. Cadence intervals,
including frame pacing, are used for the FPS calculation. GPU intervals can
include queue idle gaps and are not utilization percentages. Driver-reported
VRAM capacity is not current free VRAM or pressure. WAGE reports current VRAM
pressure unavailable; it does not use capacity to authorize work. The existing
GPU profiler remains the owner of driver-memory/allocation reports.

See [`wage-validation.md`](wage-validation.md) for executed commands, limitations
and the hardware matrix, and [`wage-renderer-roadmap.md`](wage-renderer-roadmap.md)
for unsupported features and their prerequisites.
