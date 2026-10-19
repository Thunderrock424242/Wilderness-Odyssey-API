# WAGE stabilization and acceptance

This pass completes the implemented default-off cloud optimization slice. It
preserves Minecraft's rendering ownership, existing cloud/weather authority,
public provider interfaces and dedicated-server separation. No native DLSS,
frame-generation, terrain compute or Vulkan provider is advertised.

## Hardening changes

- GPU evidence must be no more than eight frames and 500 milliseconds old. A
  fixed 16-slot timestamp history also rejects evidence from before a reset.
- Older/duplicate timer results cannot replace newer evidence. Impossible future
  labels and backend failures clear evidence, close resources and latch failure
  until lifecycle reset; ordinary timer-ring saturation remains nonblocking.
- A known timer failure rejects the current draw. Whole-frame and cloud timers
  use the same tested backend-neutral wrapper.
- Render policy requests only cached heap/GC pressure from `JvmMetrics`.
  Full system-memory/process-CPU diagnostics retain their API and separate cache,
  lock and lazy OS probe. They cannot serialize the pressure consumer behind
  slow system counter reads.
- The Aether executable timeout test accepts EOF or TCP reset for a closed
  incomplete request. A socket read timeout still fails, and generation/outage
  assertions remain. Aether production behavior is unchanged.

Three timing regressions were observed failing before repair. Two added sampler
cases first failed to compile without the pressure API, then passed, verifying
that pressure collection never invokes full system metrics and has independent
cache/clock-reset behavior.

## Acceptance added and executed

`gpuRenderingTest` compiles the packaged shaders and compares float-buffer output
for 24 original fixtures and 576 additional combinations spanning eight near and
distant altitude bands, three atlas blends, four morphologies, two coverage
values, three detail strengths and lightning. Resize, shader/resource recreation
and a replacement GL context are also exercised. A timestamp-query microbenchmark
alternates normal/optimized order for 30 pairs after ten warm-up pairs. All
synchronous readback and waiting are restricted to this explicit hardware test.

The opt-in `wageAcceptanceTest` profile uses a copied disposable world in a fixed
isolated game directory. It runs the real integrated server, normal weather
controls, cloud renderer and GPU policy; it does not force fake timer values or
bypass cloud admission. The completed run observed:

| Stage | Optimized admissions since the last lifecycle reset | Framebuffer |
| --- | ---: | --- |
| Disabled baseline | 0 | 1024 x 576 |
| Enabled | 2,695 | 1024 x 576 |
| Fresh warm-up after resource reload | 1,298 | 1024 x 576 |
| Fresh warm-up after resize | 1,070 | 960 x 540 |

World unload cleared admission/active state, native save/shutdown completed and
the wrapper exited 0 in 2 minutes 25 seconds. The harness requires fresh admissions
after each lifecycle change and verifies that the framebuffer actually resized.
Its initial missing `level.disconnect()` call was diagnosed against vanilla's
pause-screen shutdown flow and corrected; the final run uses that normal flow.

The first run also exposed slow policy observations while unused system counters
were collected. The pressure-only path removed those spikes from the observed
windows: enabled/reloaded/resized policy averages were 9.58/12.28/0.93 microseconds,
with p99 43.90/23.30/5.10 microseconds. These are the retained 240-sample diagnostic
windows, not universal overhead bounds or whole-run benchmark statistics.

Baseline/enabled/reloaded/resized screenshots were captured and inspected. Weather
and wind continue evolving, so these views establish scene/lifecycle continuity,
not a pixel-identical in-world A/B result. Exact fixture comparisons and controlled
shader timing are reported separately in `wage-validation.md`.

Dedicated-server startup, an empty-server soak of 188 seconds, console stop,
all-dimension save and clean exit also passed. Player login and long-duration
multiplayer acceptance are separate from that smoke scope.

## Review and remaining release gates

An independent read-only review of the production slice found no confirmed
critical or important defects. Follow-up acceptance review identified the fresh
post-reload/resize admission assertion gap; it was corrected before the completed
run. The reviewer reached its usage limit during the remainder of the acceptance
follow-up. The final pressure-path and harness corrections have implementation-owner
review, targeted tests and actual client evidence; they are not described as a
second independent review of every final line.

Keep `renderer.gpuEngine.enabled=false` by default. Broader GPU/driver coverage,
repeated static-scene FPS/frame-time captures, supported shader-pack/rendering-mod
combinations, long sessions and multiplayer remain rollout gates. Native DLSS,
frame generation, spatial upscaling, Vulkan and terrain/worldgen GPU offload remain
behind the concrete renderer contracts in `wage-renderer-roadmap.md`.

See `wage-validation.md` for final commands, exact counts, measurements, artifacts
and reproduction instructions. The hidden test context follows GLFW's
[context guidance](https://www.glfw.org/docs/latest/context_guide.html). Its Gradle
[upToDateWhen](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/TaskOutputs.html#upToDateWhen(groovy.lang.Closure))
predicate prevents a cached past driver result from replacing current execution.
