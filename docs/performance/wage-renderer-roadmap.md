# WAGE renderer feasibility and remaining roadmap

Research checked on 2026-10-17. Product support and SDK availability can change;
pin and recheck the chosen SDK before native integration. No NVIDIA/AMD native
SDK, native binary or unsupported presentation hook is included in this change.

## Terrain, water, anomalies and world generation

Minecraft/Sodium/Embeddium own chunk visibility, chunk buffers, submission and
terrain lifetime. GPU visibility, occlusion and indirect draws must be supplied
by those owners or a supported adapter contract. This repository exposes no
such contract. Defer equivalent terrain optimizations rather than duplicating
them. Distant terrain likewise needs explicit renderer integration, not another
independent chunk scan or forced chunk loading.

Water already evaluates Gerstner waves in its shader and uses bounded cached
meshes and optical passes. Reflection/refraction, foam, displacement and wave
math should stay with that owner. Authoritative water simulation must remain on
its existing deterministic server path. The cloud prototype deliberately reuses
synchronized weather atlases instead of simulating weather on the GPU.

Anomaly particles/debris may eventually suit GPU instancing or simulation, but
need measured CPU submission/particle cost, a typed backend buffer/pipeline
contract, a bounded visual population and an existing-renderer fallback. The
present pass does not supply those contracts or a particle-compute kernel.

Pure batches of terrain-noise mathematics are a research candidate only when
transfer/synchronization cost is amortized. CPU/GPU floating-point noise can
diverge at terrain thresholds; GPU availability and completion order must never
change saved worldgen results. Before any experiment: capture deterministic
input/output fixtures across seeds/platforms, prove exact threshold equivalence,
bound queue time/memory, isolate cancellation and preserve the native generation
owner. No authoritative worldgen code is changed here.

## Spatial upscaling: prerequisites still missing

The current frame context deliberately publishes no scene image handles. Water's
legacy private framebuffer copies are not a world-render target handoff. Safe
spatial scaling needs renderer-owned world-only resolution allocation, a
pre-HUD reconstruction pass, output-target/viewport restoration, correct aspect
ratio/color space, full-resolution UI, resize/Fabulous/post-processing ordering,
resource reload cleanup and a shader-pack cooperation contract.

FSR 1 is spatial reconstruction, not AI or DLSS. AMD places FSR 1 after
anti-aliasing/tone mapping and before UI/noisy post effects. Its current SDK
backend requirements also differ from Minecraft's existing GLSL 150 programs.
A future supported pass may port an appropriate spatial algorithm with correct
licensing, optional sharpening and native fallback; no global framebuffer
replacement is justified now. [AMD FSR 1 integration manual](https://gpuopen.com/manuals/fidelityfx_sdk/techniques/super-resolution-spatial/).

`UpscalingProvider` remains the only provider API. Its additive reconstruction
metadata distinguishes native/spatial/temporal/hardware-AI requirements without
registering a nonfunctional provider. Image presence/dimension/backend checks
are safe prerequisites, not proof that a handle belongs to the current frame or
that command ordering is valid. Future backend-specific evaluation must enforce
that lifetime and synchronization contract itself.

## Genuine NVIDIA integration

Streamline's current public repository lists D3D11/D3D12/Vulkan integration,
native device initialization and feature support queries. It is not a Java
OpenGL plugin. A C++/JNI bridge alone cannot supply a missing rendering API,
motion vectors or presentation ownership. Keep native resources and command
buffers behind the backend; never export raw OpenGL IDs through shared APIs.
[Streamline programming guide](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuide.md).

| Technology | Required integration and current gap |
| --- | --- |
| DLSS Super Resolution / DLAA | Supported native backend/device, input/output color, matching depth and camera/entity motion, unjittered camera transforms plus pixel jitter, frame token/reset, exposure contract and resolution extents. The current frame model lacks actual attachments and motion producers. |
| Ray Reconstruction | In addition to temporal inputs: noisy ray-traced color, material albedo/normal/roughness guides and appropriate hit-distance/reflection data. Existing screen-space water reflections are not this pipeline. |
| Frame Generation | Renderer-controlled present/swapchain integration, HUD-less color/UI composition, dense motion/depth, frame constants, Reflex/latency and pacing integration, supported OS/driver/hardware checks and valid resources through present. None is currently owned here. |
| Multi Frame Generation | Requires a complete frame-generation integration and per-feature runtime availability/multiplier queries. Hardware support is narrower than Super Resolution; an NVIDIA vendor string is insufficient. |

The input requirements above follow NVIDIA's
[SR guide](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS.md),
[RR guide](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS_RR.md),
and [FG guide](https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS_G.md).
Use SDK feature queries for the actual adapter and SDK version, not a hardcoded
card-name list. NVIDIA currently describes Multi Frame Generation for RTX 50
and RTX PRO Blackwell hardware; this does not establish this mod's support.
[NVIDIA DLSS developer overview](https://developer.nvidia.com/rtx/dlss).

DLSS 5 now exists: NVIDIA's September 2026 publications describe a separate
3D-guided generative appearance stage, conditioned on rendered color, engine
motion and temporal state, running on RTX 50 hardware. It is not another name
for spatial upscaling or interpolated frames. Its art-direction controls also
need visual review for Minecraft's authored style.
[NVIDIA research description](https://research.nvidia.com/labs/adlr/DLSS5/),
[NVIDIA developer update](https://developer.nvidia.com/blog/whats-new-for-game-developers-dlss-5-with-3d-guided-neural-rendering-nvidia-ace-updates-and-new-rtx-kit-capabilities).

Those announcements do not establish a generally available, documented neural
rendering plugin integration for this repository. The inspected public
[Streamline repository](https://github.com/NVIDIA-RTX/Streamline) has established
SR/RR/FG guides; this audit did not verify a corresponding DLSS 5 integration
contract suitable for Minecraft. Obtain official SDK access/documentation and
feature availability first. Do not use leaked binaries or advertise support.

Streamline framework licensing and DLSS runtime redistribution are distinct.
Review the selected package's notices, signed production binaries, application
identification and redistribution terms before shipping a native bridge.
[Streamline license](https://github.com/NVIDIA-RTX/Streamline/blob/main/license.txt),
[DLSS SDK license](https://github.com/NVIDIA/DLSS/blob/main/LICENSE.txt).

## Temporal history and frame generation design

The renderer must publish frame identity/backend generation, typed live scene
attachments, color-space/depth conventions, motion units/scales, render/output
extents, camera matrices/jitter and exposure. History must reset after camera
cuts/teleports, dimension/world changes, resize, reload, shader ownership changes
and backend replacement. Camera-only velocity is insufficient for animated
entities, vegetation, clouds, water displacement and independently moving effects.
Transparent particles/water need motion or reactive/composition masks appropriate
to the selected algorithm; a reactive mask is not a universal DLSS SR input.

Keep simulation/rendered frames and displayed/generated frames separate in
telemetry. Generated frames may improve display cadence but cannot advance
ticks, input sampling, physics, AI, networking or server state. Measure end-to-end
input latency and pacing, not just displayed FPS. UI must be composed through a
supported full-resolution path without interpolated text/crosshairs. Test
disocclusion, camera rotation, water/cloud/particle trails, translucent overlays,
menus and shader transitions. A public `generateFrame()` placeholder would have
no legitimate execution contract today, so none is added.

## Ordered implementation gates

1. Run the existing cloud experiment on hardware, accept/reject it using identical
   scenes and image comparisons, then tune budgets from evidence.
2. Identify another measured Wilderness-only hot path; implement a bounded
   backend-owned kernel/instancing path only when it beats its fallback.
3. Obtain supported world-render target and pre-UI composition APIs; implement a
   genuine spatial provider with native fallback and resource tests.
4. Add renderer-produced motion/depth/jitter/exposure, typed resource validity
   and history reset metadata. Validate real transparent/animated scenes.
5. Install a renderer-supported Vulkan adapter under the existing migration
   gates. Retain OpenGL for Minecraft 1.21.1 until those gates pass.
6. Evaluate a pinned, licensed native SDK on its supported backend, then SR;
   assess RR only with an actual ray-tracing pipeline.
7. Integrate presentation/latency/UI contracts before FG/MFG; investigate DLSS 5
   separately after official integration access and style acceptance.
