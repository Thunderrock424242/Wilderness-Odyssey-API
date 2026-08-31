# Vulkan Rendering Migration

## Status

Direct OpenGL implementations are deprecated for removal from the next
Vulkan-targeted project version. They remain active in the Minecraft 1.21.1
line because that runtime still creates an OpenGL context and the current mod
must remain functional.

Backend-neutral APIs are not deprecated. Water and weather should continue to
depend on `RenderBackend`, `GPUCapabilities`, `RenderFrameContext`,
`EnvironmentState`, `TemporalFrameData`, and `UpscalingProvider`.

## Deprecated compatibility inventory

| Legacy owner | Current responsibility | Vulkan replacement |
| --- | --- | --- |
| `rendering.backend.opengl` | capability capture, shader validation, scoped render-state restoration, asynchronous timestamp samples, owned timer cleanup | renderer-supported `RenderBackend` implementation |
| `gpuprofiler.client` | OpenGL memory extensions, allocation tracking, debug callbacks, state snapshots, draw timing | backend telemetry and renderer-supported debug/timing hooks |
| `WaterSceneCapture` | raw framebuffer color/depth blit and integer texture IDs | typed scene color/depth inputs supplied by the backend or render graph |
| `StructureBlockRendererMixin` GL state reads | remembers depth-test and depth-mask state around the overlay | supported scoped render-state API |
| OpenGL GPU profiler mixins | intercepts `GlStateManager`, texture preparation, and `drawElements` | backend resource and command instrumentation |

Do not add new imports from `org.lwjgl.opengl`, new raw framebuffer IDs, or new
OpenGL state queries to water, weather, or backend-neutral rendering packages.
The source contract test intentionally fails if another direct OpenGL owner is
introduced without updating this migration boundary.

GLFW window and input calls are not OpenGL rendering calls. They should be
reviewed against the target Minecraft version, but they are not included in
this deprecation solely because their class names begin with `GL`.

## Removal gates

Remove the deprecated code only after all of these conditions are true:

1. Minecraft's target renderer exposes a supported backend bootstrap point.
2. `RenderBackends` installs the new adapter without loading OpenGL classes.
3. Water receives valid scene color and depth handles without framebuffer IDs.
4. Shader and pipeline validation no longer reads OpenGL program IDs.
5. GPU timing is asynchronous, identifies the measured source frame, and is
   supplied and released by the target backend.
6. GPU identity, capabilities, and memory evidence come from supported backend
   information and continue to degrade conservatively when unavailable.
7. Structure overlays restore state through supported renderer APIs.
8. Deprecated profiler mixins are removed from the mixin configuration.
9. Water, weather, low-capability fallback, resource reload, world join, and
   renderer shutdown pass focused and live validation.

Deprecation is a migration signal, not permission to delete the current path
from the 1.21.1 release. Removing it early would leave that version without a
working rendering backend.
