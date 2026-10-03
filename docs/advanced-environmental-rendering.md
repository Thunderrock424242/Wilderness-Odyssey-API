# Advanced environmental rendering: feasibility and first proof of concept

Date: 2026-09-30. Target: Minecraft 1.21.1, NeoForge 21.1.248, JDK 21.

## Feasibility decision

Extend the existing native water screen-space reflection (SSR) path and its
automatic Iris fallback. The repository already contains a reflection effect,
shared rendering framework, regional weather presentation, and water ownership
handoff. A second environmental renderer would duplicate these responsibilities.

The current implementation uses OpenGL rasterization and GLSL 150 shaders.
SSR marches reflected rays against the visible scene depth; it is not hardware
ray tracing. It can reflect visible terrain, structures, and lit surfaces from
the captured color image. It cannot recover off-screen or occluded geometry,
produce physically traced indirect illumination, or guarantee reflections of
effects drawn after capture. Missing hits blend into the existing sky and
sun/moon reflection.

Hardware ray tracing is not practical as an incremental feature of this
backend. The project has no acceleration structures, ray-query/pipeline
integration, or renderer-supported scene geometry handoff. Vulkan by itself
would not supply these: the relevant extensions, device features, resource
lifetime, geometry updates, and synchronization would still be required.
Keep the existing `RenderBackend` boundary for future supported backends;
do not add a speculative Aperture integration or an RT setting that cannot run.

## Existing ownership map

Paths below are relative to `src/main/java/com/thunder/wildernessodysseyapi/`.
In water rows, the shorter `water/` and `ocean/` paths are under `watersystem/`.

| Responsibility | Existing owners | Integration constraint |
| --- | --- | --- |
| Canonical water volume and gameplay | `watersystem/water/volume/CanonicalWater`, `WildernessWaterAuthority`, `WaterVolumeChunk`; `water/authority/AuthorityWaterAccess` | Server authority, finite volume, and world persistence remain independent of graphics |
| Water cycle and local dynamics | `water/hydrology`, `water/sph`, `ocean/shore`, `ocean/tide` | Consume their existing snapshots; graphics must not add/remove world water or run a second solver |
| Wave and regional sea state | `ocean/OceanSeaStateField`, `ClientOceanSeaState`, `water/wave/GerstnerWaveAnimator`, `water/render/WaterSurfaceEquation` | Preserve the shared CPU/GPU equation, clock, regional spectrum, and immersion behavior |
| Native water geometry | `water/render/WaterRenderCoordinator`, `WaterChunkMeshCache`, `WaterSurfaceHandoff` | One surface owner at `AFTER_TRANSLUCENT_BLOCKS`; retain renderer-section upload acknowledgements |
| Water optics | `water/render/WaterShaders`, `WaterSceneCapture`, `UnderwaterEffectsRenderer` | Reuse the isolated scene copy and existing Gerstner shaders |
| Atmospheric simulation | `weather/simulation/WeatherAuthority`, `AtmosphereSimulationEngine`, `weather/storage`, `weather/networking` | Server-authored regional simulation and snapshots remain authoritative |
| Regional weather presentation | `weather/client/ClientWeatherCoordinator`, `WeatherVisualState`; `rendering/EnvironmentState` | Use synchronized regional values, including surface memory and wind |
| Clouds, rain, fog, wetness | `weather/client/cloud`, `weather/client/precipitation`, `WeatherClientEvents`, `weather/client/surface/WeatherSurfaceRenderer` | Native optical effects must yield when an external shader owns them |
| Shared frame and performance | `rendering/client/WildernessRenderingFramework`, `RenderFrameContext`, `GPUCapabilities`, `rendering/performance` | Sample once per rendered frame; preserve player settings and the adaptive quality ceiling |
| Optional shader ownership | `rendering/compat/ShaderPackCompatibility`, `WaterShaders.externalShaderPackOwnsWater()` | Distinguish installed-but-disabled from active; uncertainty preserves external ownership |
| Shader water classification | `water/render/ExternalShaderWaterMaterialBridge`, optional Iris/Sodium/Embeddium mixins | Existing internal bridges are version-sensitive and require live testing |
| Echo/story presentation | `temporalrift/client/ClientEchoState`, `EchoClientEffects`, `TemporalRiftShaders`; existing cinematic/event systems | Future structure effects should consume runtime events and synchronized state, not execute rendering from worldgen |

This is a targeted ownership review, not certification of every environmental
consumer or optional rendering mod.

## Minecraft, NeoForge, Sodium, and Iris

The current code already uses NeoForge client frame, level-stage, shader
registration, fog, and world-unload events. Minecraft/Blaze3D owns shaders,
render targets, vertex buffers, and command submission. These hooks allow
additional raster effects; they do not expose a hardware RT scene.

The development runtime currently selects Sodium `0.8.12+mc1.21.1` on the client
classpath. Sodium changes chunk compilation and upload ownership, so the
existing exact-section handoff and optional mixin guards must be preserved.
Sodium is not an RT backend. Its upstream documentation describes OpenGL driver
requirements and Fabric/NeoForge support.

Iris API v0 exposes `getInstance()` and `isShaderPackInUse()`. The latter reports
actual rendering ownership, including false when no pack is enabled or pack
compilation failed. Polling it at frame start supports enabled/disabled changes
without depending on Iris internal configuration. The public interface reviewed
does not provide a general custom-uniform or shader-program merge API.

The fallback therefore delegates water pixels to pack-owned tagged-fluid
geometry. Water physics, buoyancy, synchronized weather, and cosmetic detail
remain separate. Native GPU water displacement is suspended to avoid double
animation. Exact visual agreement between arbitrary shader-pack waves and
Wilderness gameplay waves cannot be promised without a verified pack-specific
interface. A pack change while both old and new packs are active retains the
same external ownership; no pack-specific state is cached by this proof of
concept.

## Candidate effects and limits

| Effect | Current source support | Next constraint |
| --- | --- | --- |
| Wave-responsive reflections | SSR uses displaced surface view position and Gerstner normals, with cosmetic micro-normal detail | Prove visual correctness and cost; visible-scene limits remain |
| Refraction and underwater visibility | Scene-depth reconstruction, absorption, turbidity, distortion, underwater optics | Verify depth provenance, paused-camera movement, Fabulous targets, and resizing |
| Caustics and sunlight highlights | Procedural caustics and analytic sun/moon highlights | These are approximations; validate underwater projection and weather response |
| Foam and storm appearance | Coastal crest/foam parameters, regional spectrum, rain detail, storm roughness | Preserve CPU/GPU surface parity and the existing coastal owner |
| Volumetric weather | Raymarched and layered cloud implementations already exist | External ownership must also cover fallback clouds and fog, not only custom programs |
| Wet terrain and puddles | Bounded cosmetic surface meshes exist | Reflective puddles would need a separate validated optical/material pass; current overlays are not scene reflections |
| Structure and anomaly effects | Existing Echo and event presentation paths provide an integration seam | Design bounded effect descriptors and runtime triggers after the first optical path is validated |
| Temporal reconstruction | `TemporalFrameData` boundary exists but usable motion/color/depth handoff is unavailable | Do not expose temporal upscaling or accumulation until inputs and rejection/lifecycle behavior are supported |

## First implementation slice

1. Retain the current water reflection algorithm, wave equation, native mesh,
   material bridge, and fallback ownership. No new rendering dependency is needed.
2. Make optional API discovery and invocation conservative under missing,
   incompatible, malformed, or linkage-failing providers. Cache discovery,
   sample active ownership every frame, and make the selected status visible
   in the existing rendering diagnostics.
3. Identify optical capture by the rendering framework's frame index. Game
   time and partial tick are animation inputs, not reliable rendered-frame
   identity while paused. Underwater overlays may only reuse that frame's
   water-stage capture; they must not capture cleared GUI/hand depth.
4. Release scene-copy resources and timing queries at shader ownership changes,
   shader reload, disabled rendering, and client-level teardown. Keep geometry
   handoff and rebuilds with `WaterRenderCoordinator`.
5. Apply the shared ownership decision to native weather clouds, sky darkening,
   air and underwater fog, underwater overlays, and cosmetic wet-ground overlays
   while retaining regional precipitation and impacts. Retain regional state
   queries used by gameplay and precipitation. Arbitrary shader packs do not
   receive a new regional-weather uniform bridge; their sky may still follow
   Minecraft's global weather inputs.

Basic/Enhanced/High preset labels can later map onto existing water/weather
quality controls. Existing LOW/MEDIUM/HIGH/CINEMATIC water tiers and bounded SSR
steps/distances are retained in this slice to preserve saved settings. Hardware
selection remains conservative and adaptive quality remains a temporary ceiling.

## Validation and acceptance

Automated checks should cover optional API absence, installed-but-disabled,
enabled/disabled switching, broken discovery/invocation, and recovery after a
temporary query failure. Existing shader contracts, wave-mirror tests, surface
handoff tests, and regional-weather tests provide regression coverage. Use the
normal Gradle pipeline and its existing offline JUnit runtime for focused tests.

Live acceptance remains necessary:

| Configuration | Observe |
| --- | --- |
| No Iris; Iris installed with shaders disabled | Native reflection path, one visible water surface, regional weather |
| Iris with an enabled pack | External ownership status, native SSR/underwater passes suspended, tagged water visible, no duplicate clouds/fog/wet overlays |
| Enable, disable, and change packs in a loaded world | Correct mesh handoff and rebuild, no stale scene textures, no accumulating GPU resources |
| Paused game with camera rotation; resize; resource reload | Reflections follow the rendered camera; underwater capture remains from the same frame |
| Calm/storm/shore/underwater/structure views | Regional weather drives appearance; wave and immersion behavior remain coherent |
| Dedicated server and two clients with different graphics | Server starts without optional render mods; volume, buoyancy, and weather agree |

Benchmark the same seed, camera route, resolution, view distance, and graphics
settings before/after and with SSR on/off. Record CPU frame time, asynchronous
GPU duration, scene-copy count, mesh/vertex counts, and memory over repeated
switches. Existing water GPU timing spans the surface pass, so it is not an
isolated SSR-only timing. No performance improvement is claimed without this
baseline.

Source, compilation, JUnit, packaging, client startup, visual acceptance, and
measured performance are separate evidence.

## Implementation result

The first slice is implemented in source. It extends the existing reflection
path rather than introducing another optical pass or wave authority. Graphics
presets, reflective puddles, additional atmospheric scattering, data-driven
structure effects, and pack-specific integrations remain later work after live
acceptance of this boundary.

| Changed source | Result |
| --- | --- |
| `rendering/compat/ShaderPackCompatibility`, new `IrisShaderPackProbe` | One ownership query per rendered frame, conservative optional API failure handling, retry after temporary invocation failures |
| `rendering/client/WildernessRenderingFramework` | Existing diagnostics show absent, disabled, active, or unknown shader ownership |
| `watersystem/water/render/WaterSceneCapture`, new `WaterSceneCaptureState`, `WaterShaders` | Frame/backend/viewport provenance, same-frame underwater reuse, framebuffer restoration around allocation and copying, sampler and target invalidation |
| `watersystem/water/render/WaterRenderCoordinator` | Resource release on native-to-external handoff, disabled rendering, and level unload; existing mesh rebuild ownership retained |
| `watersystem/water/render/UnderwaterEffectsRenderer` | Pack-owned fog and overlays are preserved, including the native animated-crest transition |
| `weather/client/WeatherClientEvents`, `weather/client/surface/WeatherSurfaceRenderer` | Native air fog, cloud resources, and surface overlays yield to an external pack |
| `mixin/ClientLevelLocalizedWeatherMixin`, `mixin/LevelRendererLocalizedWeatherMixin` | Native sky darkening and fallback cloud geometry yield; dimension hooks and regional precipitation retain their existing paths |
| `build.gradle`; new `IrisShaderPackProbeTest`, `WaterSceneCaptureStateTest`, `UnderwaterFogOwnershipTest` | Focused offline rendering task and 20 new regression cases |

Validation completed on 2026-09-30 with JDK 21.0.10:

```powershell
.\gradlew.bat renderingTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Result: **BUILD SUCCESSFUL**, exit code 0; **275 tests, zero failures, errors,
or skipped tests**. Production Java compilation, test compilation, resources,
and the regular JAR task completed as dependencies of this check. The new tests
were first observed failing before their fixes, including the underwater fog
ownership regression. The broad check includes existing backend contracts,
shader interfaces, CPU/GPU wave mirror, water handoff, quality policy, cloud
models, precipitation, and synchronized weather presentation tests.

The normal pipeline warned that the existing StructureGen content-catalog
snapshot fingerprint differs from the current dependency environment. It
excluded modded catalog content and validated/generated its three blueprints
successfully. No catalog or generated infrastructure was manually changed.

The generated package is `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`.
The focused task's HTML report is
`.codex-build/reports/tests/renderingTest/index.html`. This is compilation,
focused regression, and JAR packaging evidence; the full test suite and full
`build` task were not run for this slice.

Minecraft was not launched. Shader-pack compilation, switching/reloads,
Fabulous target behavior, actual framebuffer restoration/resource lifetime,
visual output, dedicated-server/two-client behavior, and benchmarks remain
unverified at runtime. The matrix above defines that acceptance work. Optional
API fixtures prove policy and recovery, not compatibility with every real pack.

## Primary references

- [Iris 1.21.1 public API](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java).
- [Iris NeoForge build integration](https://github.com/IrisShaders/Iris/blob/1.21.1/neoforge/build.gradle.kts).
- [Sodium upstream documentation](https://github.com/CaffeineMC/sodium/blob/dev/README.md).
- [Khronos Vulkan ray-tracing guide](https://docs.vulkan.org/guide/latest/extensions/ray_tracing.html).
- Repository boundaries: [rendering framework](rendering-framework.md),
  [Vulkan migration](rendering-vulkan-migration.md),
  [water overview](watersystem/overview.md), and
  [physical atmosphere](weather/physical-atmosphere.md).
