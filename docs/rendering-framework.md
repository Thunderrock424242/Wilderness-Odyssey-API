# Wilderness rendering framework

## Purpose and non-goals

The framework gives Wilderness water and localized weather a small shared
client-rendering boundary without replacing Minecraft's renderer. Minecraft and
NeoForge still own render-pass order, shader/resource loading, render targets,
chunk buffers, and command submission.

The framework does not implement Vulkan, DLSS, FSR, XeSS, a custom render graph,
or vendor-specific native code. It records only capabilities that the active
backend proves and leaves unavailable temporal inputs empty.

The ownership flow is:

```text
Minecraft renderer/backend
  -> RenderBackend + cached GPUCapabilities
  -> RenderFrameContext
       -> camera-local EnvironmentState
       -> frame timing + transient quality ceiling
       -> explicitly available TemporalFrameData
  -> existing water and weather render owners
```

## Phase 1 audit: current owners

| Concern | Existing owner | Current behavior |
|---|---|---|
| Replacement water surfaces | `WaterRenderCoordinator` and `WaterChunkMeshCache` | Incremental snapshot-mesh rebuilds, frustum culling, renderer-section handoff, one translucent detail flush |
| Water displacement | `GerstnerWaveAnimator`, `GerstnerWaveRenderMixin`, `WaterSurfaceEquation`, `WaterShaders` | Shared CPU/GPU Gerstner equation plus regional sea-state interpolation |
| Water optics | `WaterShaders`, `WaterSceneCapture`, `UnderwaterEffectsRenderer` | Optional scene color/depth copy, SSR/refraction, underwater distortion/caustics, safe stock fallback |
| Local SPH and ripples | `FluidRenderer`, `RippleRenderer` | Bounded synchronized local meshes/effects in Minecraft's translucent path |
| Localized weather state | `ClientWeatherCoordinator` | Immutable server-authored regional snapshots with spatial and temporal interpolation |
| Clouds | `LocalizedCloudRenderer`, `ContinuousCloudFieldAtlas`, cloud shader owners | Raymarched, layered-volume, voxel, and vanilla-compatible fallbacks |
| Precipitation | `LocalizedPrecipitationRenderer`, `WeatherImpactRenderer` | Bounded near columns, distant shafts, deterministic impacts, vanilla light/texture integration |
| Fog and surface response | `WeatherClientEvents`, `WeatherSurfaceRenderer` | NeoForge viewport fog events and bounded wet/puddle overlays |
| Shared world environment | `RegionalEnvironmentSnapshot`, `ClientEnvironmentState` | Server/gameplay composition and a once-per-second ambience summary; not a render-frame context |
| GPU profiling | `GpuProfiler`, `GpuDiagnostics`, `GpuHardwareProbe` | Opt-in allocation/draw diagnostics and best-effort identity/memory probing |

The systems were already modular but had several cross-cutting leaks:

- `WaterGpuTimer` owned raw OpenGL timestamp queries.
- water and both cloud shader owners repeated raw OpenGL program-link checks;
- weather imported `WaterShaders` solely to ask whether a shader pack was active;
- the water hardware selector used GPU product-family names as performance policy;
- water fetched camera-local weather separately during its shader update; and
- there was no common frame/context contract for future backend or temporal data.

## Phase 2 design and responsibility boundaries

### Backend and capabilities

`RenderBackend` exposes only cached capabilities, Minecraft shader validation, and an
asynchronous GPU timer factory. `OpenGlRenderBackend` is today's adapter.
`RenderBackends.install(...)` is the handoff for a future Minecraft-supported
backend adapter; water and weather must not choose a backend themselves.

`GPUCapabilities` keeps vendor, renderer, version, and memory-provider evidence
for diagnostics. Policy uses features such as framebuffer blits, depth textures,
multiple render targets, timer queries, compute shaders, image load/store, and
texture limits. Vendor strings are never consulted by the shared selector.

Memory evidence distinguishes a dedicated total from an available-only value.
Unknown or partial memory evidence cannot auto-promote a device to the highest
tier. Runtime adaptation is the long-term answer for hardware whose performance
cannot be inferred safely from static capabilities.

### Frame and environment

`WildernessRenderingFramework` publishes one immutable `RenderFrameContext` at
frame start and completes its CPU timing at frame end. It samples one
camera-local `EnvironmentState` from the existing synchronized weather and wind
owners. The state includes rain, snow, storm energy, wind speed/direction,
wetness, frozen fraction, temperature, humidity, and lightning activity.

This state is presentation-only. It never ticks weather, mutates water, scans
blocks, loads chunks, or becomes gameplay authority. `WaterEnvironmentState`
continues to own water-specific tide, current, fetch, turbidity, and wave-spectrum
composition.

### Adaptive quality

`AdaptiveQualityController` uses an exponential moving average, a 60-frame
warm-up, separate reduction/promotion thresholds, and a configurable cooldown.
It moves one tier at a time between configured minimum and maximum bounds.

The controller publishes only `RenderQualityState`, a transient ceiling:

- disabled feature toggles remain disabled;
- manual or hardware-selected water quality remains a stricter ceiling;
- weather variants are prebuilt only on config load/reload, avoiding per-frame
  settings allocations; and
- no selected value is written back to the player's config.

New client keys are:

```text
renderer.adaptiveQuality
renderer.targetFrameTime
renderer.minimumQuality
renderer.maximumQuality
renderer.qualityChangeCooldown
```

Adaptive quality defaults off. Existing water and weather options remain the
feature-level authority. No duplicate `water.reflections`, `water.caustics`, or
weather-quality keys were added because equivalent controls already exist under
`water_rendering` and `weather_rendering`.

### Temporal upscaling boundary

`TemporalFrameData` can carry backend-specific image wrappers for rendered
color, depth, motion vectors, and a reactive mask, plus jitter, exposure,
render/output resolutions, and frame time. `UpscalingProvider` is the future
provider boundary.

The current native path publishes empty handles. Minecraft 1.21.1 does not
provide this project a supported motion-vector/post-process handoff, so the
framework deliberately reports temporal reconstruction as unavailable. An
`renderer.upscaler` option was not added because no non-native provider is
usable today.

## OpenGL and renderer-coupling classification

### Moved behind the OpenGL backend

- timestamp query creation, polling, and destruction;
- core-versus-ARB timer-query selection;
- static feature/limit discovery; and
- native shader-program link validation; and
- the depth/blend state snapshot restored by the fullscreen underwater pass.

### Kept in place for now

- `RenderSystem`, `ShaderInstance`, `RenderType`, `VertexBuffer`,
  `BufferBuilder`, `DynamicTexture`, and `RenderTarget` usage. These are
  Minecraft/Blaze3D integration APIs and should follow Minecraft's migration
  rather than be mirrored wholesale in a mod abstraction.
- `LevelRendererLocalizedWeatherMixin`. It wraps the narrow vanilla fallback
  ownership hooks needed to suppress global clouds/precipitation while
  preserving dimension renderers and Fabulous targets. It is version-sensitive
  but not itself an OpenGL implementation.
- water's renderer-section handoff mixins. They coordinate ownership with
  vanilla/Sodium/Embeddium chunk compilation and do not issue graphics API calls.

### Still OpenGL-specific and a future backend-adapter candidate

- `WaterSceneCapture` reads framebuffer bindings and blits color/depth using
  OpenGL IDs. It is guarded by backend capabilities but remains the main water
  path that a future render-graph/attachment API must replace.
- the water shader samplers consume integer texture IDs from that capture.
- `GpuMemoryProbe` and the opt-in `GpuDiagnostics` profiler use OpenGL extensions
  and debug callbacks. They are diagnostics, not water/weather render policy,
  but will need their own backend implementations.
- some debug information exposes framebuffer IDs. This is diagnostic-only.

Do not emulate Vulkan resources with synthetic OpenGL-style integer IDs. When
Minecraft exposes backend-neutral render attachments, add a backend-specific
`TemporalFrameData.ImageHandle` and scene-input provider instead.

## Shader-pack compatibility

`ShaderPackCompatibility` now owns cached optional Iris/Oculus API discovery.
Both water and weather consult it without depending on each other. If a known
shader mod is installed but its API cannot be queried, the integration fails
closed to the tagged/vanilla-compatible paths.

The existing water material alias bridge remains optional and does not edit a
user's shader-pack archive. Risks that still require live compatibility tests:

- optional API packages/methods can change between Iris/Oculus versions;
- exact `LevelRenderer` mixin descriptors are Minecraft-version-sensitive;
- shader packs remain final pixel owners, so built-in water displacement and
  cloud volumes intentionally fall back when a pack is active; and
- no supported Iris uniform API is assumed. Integrations can read the immutable
  `EnvironmentState` later if an explicit supported shader API is available.

## Performance characteristics

- backend capability discovery is cached;
- optional API reflection is resolved once and active-pack ownership is sampled
  once per render frame for all water/cloud consumers;
- environmental state is one constant-time camera sample per frame;
- adaptive weather settings allocate only on config reload;
- GPU timestamps remain asynchronous and never wait for an unfinished query;
- controller state is bounded and changes one tier at a time; and
- existing mesh caches, hard caps, rebuild budgets, deterministic sampling, and
  no-chunk-load rules remain intact.

## Future Vulkan adapter work

When Minecraft exposes the relevant supported renderer hooks:

1. implement a Vulkan-backed `RenderBackend` and install it during renderer
   bootstrap;
2. replace `WaterSceneCapture` with supported render-graph attachment access;
3. supply typed backend image handles rather than leaking native resources into
   water/weather code;
4. keep shader/resource creation in Minecraft's supported API; and
5. retain OpenGL as a graceful backend while it remains supported.

No water/weather simulation, mesh ownership, or environment model should need
to change for those steps.

## Future DLSS, FSR, or XeSS work

A real temporal provider still needs all of the following before it can be
implemented honestly:

- a supported post-process insertion point;
- resolved color and depth attachments for the correct frame;
- engine-produced motion vectors for world, entity, particle, translucent, and
  camera motion;
- controlled projection jitter and matching unjittered matrices;
- exposure or luminance information;
- render-resolution versus display-resolution control;
- reactive/transparency-mask generation for water, particles, precipitation,
  clouds, and other unstable pixels;
- backend synchronization and resource-lifetime guarantees;
- the provider SDK/native libraries, licensing, platform packaging, and failure
  fallback; and
- live validation with shader packs, Fabulous graphics, resizing, alt-tab,
  resource reloads, multiple dimensions, and ordinary integrated hardware.

Spatial upscaling may need fewer inputs, but it still requires a supported
render-resolution/output handoff. Provider names must not become hardware
policy: availability should be discovered and the native renderer must always
remain functional.
