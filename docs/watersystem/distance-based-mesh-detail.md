# Distance-based water mesh detail

Date: 2026-11-01. Baseline: `in-dev` HEAD `34d37be9` plus the existing
uncommitted [shared mesh sampling](shared-mesh-sampling.md) changes, preserved
and extended here. This implements bounded distance/detail selection and
transition topology; live graphics/performance acceptance remains open.

## Selection and ownership

The existing `WaterRenderCoordinator`, `WaterChunkMeshCache`, snapshot dirty
queue and count/time rebuild budgets remain the owners. No new renderer,
simulation scheduler, water authority, packet, save schema or dependency was
introduced. Wave components, server forces, water transfers and physical-region
defaults are unchanged.

`WaterMeshLodPolicy` selects between the existing one-block and half-block
resolutions. Low/Medium stay at one block. High/Cinematic retain fine interiors
near the camera and in chunks with partial/covered surfaces, shallow water,
strong snapshot currents, significant river weight or uneven base height.
Other distant calm chunks can use one-block faces. This is chunk-level selection,
not arbitrary large-cell ocean decimation.

The base fine radius is 64 horizontal blocks from the nearest chunk edge.
Existing local sea strength extends it up to twice the configured radius.
An already-fine mesh has another 24 blocks of hysteresis before demotion and
must remain fine for at least 40 game ticks. Promotion is not dwell-delayed.
The same dwell check applies when an ordinary source update also rebuilds a
mesh, so source churn cannot bypass the policy.

At most eight cached entries are checked per render frame through a retained
weak iterator. Each entry refreshes local sea strength at most once per ten
game ticks. These are cached-state queries, not block/chunk scans. Detail
requests use the existing deduplicated dirty queue through
`queueMeshDetailRefresh`, which queues only that loaded chunk instead of the
metadata-change neighbor halo. Actual uploads and existing baked-section/handoff
work retain the original rebuild budgets. The published old mesh stays in use
until the replacement is built. Selection and rebuild convergence are gradual
under load; newly nearby meshes are not all upgraded in one frame.

## Shared edges and transition faces

`WaterMeshTopology` keeps every chunk's outer edges at **one-block spacing** at
both detail levels. Fine interiors connect through center fans. Adjacent fan
triangles are paired into the existing BLOCK-format QUADS; an odd edge polygon
has one zero-area index triangle. Corner samples return the exact shared input
instead of passing through endpoint interpolation rounding.

This makes edge topology independent of neighboring detail, quality, availability
and rebuild order. It requires no coordinated neighbor rebuild, skirt or extra
water layer. Normal source invalidation still refreshes shared metadata as
before. Matching topology is not a claim that arbitrary stale metadata revisions
or every optional renderer combination have been certified.

The shared CPU sampler now indexes the actual transition triangles, not just
regular rectangular quads. A query checks at most eight triangles in one block
column and evaluates only the selected three vertices. Half-grid coordinates
and positive body-normal channels fit in the existing third payload word, so
there is still no retained native buffer or GL handle. The copied primitive
data cap is **14.25 KiB** for coarse meshes and **56.25 KiB** for fine meshes,
plus object/array headers. Cache replacement, unload and owner/resource clear
release detail state, iterator and sampling ownership together.

## Verified geometry reduction

For a synthetic fully wet 16-by-16 chunk:

| Mesh | Quads | Vertices | Submitted triangles |
| --- | ---: | ---: | ---: |
| Fine interior with fixed borders | 1,020 | 4,080 | 2,040 |
| Coarse one-block faces | 256 | 1,024 | 512 |

That is approximately **74.9% fewer submitted triangles** for this distant
coarse fixture. The count includes the fine border's degenerate index triangles.
Coverage/winding and shared edge vertices are tested separately. This is a
geometry-count result, not an observed FPS/MSPT or frame-time improvement.

## Client settings

Under the existing `water_rendering` category:

| Setting | Default | Meaning |
| --- | --- | --- |
| `enableAdaptiveSurfaceMeshDetail` | `true` | Enables distance reduction; disabling restores the quality ceiling through existing rebuild budgets. |
| `surfaceMeshNearDetailDistanceBlocks` | `64` | Base horizontal fine radius, range 16–256 blocks; storm energy can double it. |

Fixed outer-edge topology remains when distance reduction is disabled. Low and
Medium never acquire half-block interiors. No synchronized server setting is
needed because this controls client tessellation, not authoritative water.

## Actual validation

All commands ran serially through the wrapper using isolated build output.

| Stage | Actual result |
| --- | --- |
| Initial policy/topology red | Seven tests ran; six expected failures with the missing implementation stubs, 43s. |
| Transition test setup | One compile attempt failed because the new test called a nonexistent cleanup helper. The test was corrected to remove its own snapshot entries through the existing API. No production workaround was added. |
| Transition/queue red | Seventeen tests ran; exactly two expected failures: the old sampler rejected transition faces and the empty detail-refresh method queued nothing, 27s. |
| Core green | Compile and all 17 tests passed, 30s. |
| Integrated compile | Passed, 3s. |
| Focused integration | **37 tests / six suites**, zero failures, errors or skips; passed in **36s**. |
| Broader water/weather + packaging | **843 tests / 187 suites**, zero failures, errors or skips; **build passed in 43s**. |
| Offline water in the same build | **222 tests / 43 suites**, zero failures, errors or skips; overlaps root coverage. |

Focused coverage includes distance/sea response, hysteresis, minimum dwell,
request deduplication, quality ceilings, snapshot sensitivity, exact chunk
distance, no neighbor-halo detail refresh, all edge/corner transition winding
and area, submitted triangle counts, dry holes and packed triangle sampling.
The transition sampler checks 162 interior points over both detail levels and
nine edge/corner/interior column positions. Earlier packed-buffer, real Minecraft
BLOCK-buffer and mixed-body regressions remain green.

The unchanged production vertex shader ran on NVIDIA GeForce RTX 4090 Laptop
GPU, OpenGL 3.2.0 / NVIDIA 621.01. New fixtures compare coarse/fine shared-edge
positions and normals at near-origin, coarse-phase and world-border coordinates,
and compare 12 captured transition sample heights with CPU evaluation at three
freeze levels. Original depth/freeze, mixed-body, tide/impulse and chunk-edge
fixtures still pass. These are hidden-context arithmetic/topology checks, not
a full Minecraft visual session.

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests '*WaterMeshLodPolicyTest' --tests '*WaterMeshTopologyTest' --tests '*WaterSurfaceMeshSamplesTest' --tests '*WaterSurfaceVertexGpuTest' --tests '*ClientWaterSurfaceInvalidationTest' --tests '*WaterRenderingConfigTest'
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.*' waterSimulationTest build
```

Logs: ignored `.codex-build/water-mesh-lod-*.log`; XML results under
`.codex-build/test-results/{test,waterSimulationTest}`. Root filters are not the
entire unfiltered repository suite, and unchanged subproject tests may be up to
date. Existing deprecation/content-catalog warnings remain nonfatal. No data
generation command or new GameTest was needed for this client topology change.

## Remaining acceptance

No full Minecraft client/server was launched. Existing native JVM/GLFW failures
remain separate and unresolved; no system Java, driver, permission or security
settings were changed. Normal performance, multiplayer, Sodium/Iris/shader-pack,
Distant Horizons and modded-terrain visuals are not certified by these checks.

With a stable runtime, use a disposable generated world and observe near/far
travel, storm buildup/decay, all four tiers, paused quality/toggle changes,
shorelines, lake/river transitions, loaded frontiers, resource reload and
Save & Quit/reload. Compare submitted counts and asynchronous water timings,
total frame/MSPT, rebuild/section-compile cost, memory and camera effects.

Spatial transition faces prevent detail-dependent edge cracks with matching
inputs; they do not implement temporal geomorphing. Discrete uploads may still
show a detail pop and need live assessment. Exact horizontal Gerstner inversion
also remains outside the CPU sampler's parameter-plane model. Further physics,
flood/surge and waterfall work retains the existing canonical-conservation
requirements.
