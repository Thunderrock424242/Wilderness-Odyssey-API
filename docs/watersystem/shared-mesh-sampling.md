# Shared uploaded-water mesh sampling

Date: 2026-10-30. Baseline: clean `in-dev`, HEAD `34d37be9`.
This follows the [shallow-depth checkpoint](advanced-water-depth-checkpoint.md).
It completes the packed-input and parametric triangle-sampling phase, not the
full cinematic-water or live acceptance roadmap.

## What changed

The previous client height sampler used one column's depth, current, body blend
and base height. The actual surface averages neighboring columns, interpolates
subdivisions and packs current/depth/color/normal bytes before evaluating the
shader at vertices. A continuous one-column CPU sample could therefore disagree
at river/lake transitions, uneven depths and shorelines even with the same
Gerstner equation.

The existing owner flow is now:

```text
immutable water snapshots + bounded existing mesh construction
  -> actual BLOCK-format MeshData vertex buffer
  -> VBO upload + immutable WaterSurfaceMeshSamples in the same cached entry
  -> coordinator's current-level / owned-column / acknowledged-handoff gate
  -> three packed vertex evaluations + the emitted NW-SE triangle interpolation
  -> camera immersion, ambient spray/effects and cached surface continuity
```

- `WaterSurfaceMeshSamples` copies only base height, encoded color and packed
  body-normal data. Format offsets come from `VertexFormat` APIs. It reads the
  actual BLOCK bytes, including signed normalized body channels, instead of
  independently reproducing mesh averaging or Minecraft's normal packing.
- `WaterChunkMeshCache` keeps samples alongside the uploaded group in a private
  entry. The public `MeshGroup` record/constructor remains unchanged. Replacing,
  removing or clearing that entry drops the CPU data with the existing upload;
  a level-identity guard rejects samples belonging to another client level.
- `WaterRenderCoordinator` exposes samples only for the current level, an
  column owned by that upload, a present snapshot, an acknowledged custom upload and
  active native shader ownership. Unpublished, unloaded, stock-fallback and
  external-pack cases use the existing flat fallback; missing quads never borrow
  neighboring wet geometry.
- `WaterSurfaceEquation` evaluates decoded depth, current, normalized body blend,
  packed continuity and base height at each selected vertex. It mirrors the
  shader's **linear chunk-corner** spectrum/wind interpolation and lake fetch,
  then uses the existing Gerstner, tide, wake and frozen envelopes. Shader assets
  and wave profiles were not changed.
- `ClientWaterImmersion` samples the same emitted triangle at the actual cached
  subdivision level, including while quality changes await a rebuild. Continuity
  also comes from the cached geometry. Its frame cache includes mesh identity,
  so publishing/replacing/releasing a mesh invalidates a stationary sample.

No authoritative gameplay, canonical transfer, hydrology, SPH ownership,
configuration default, packet or persistence format changed. This adds no
renderer, scheduler, world-generation operation or completed-chunk scan.
Server forces remain server-authoritative.

## Bounds and practical limits

Retained primitive sampling data per full wet chunk is at most **13 KiB** for
Low/Medium topology and **52 KiB** for High/Cinematic, plus array/object headers.
Data is bounded by the existing cache entries. Capture happens only on an
existing dirty-mesh rebuild and retains no native buffer or GL handle.

Each query selects one quad and evaluates three vertices. It reads no terrain
neighborhood. Height evaluation performs three bounded wave/impulse evaluations
and four existing regional corner queries instead of the former single-point
wave evaluation; this trades bounded CPU work and memory for input/topology
agreement. No live FPS/MSPT or speedup claim is made. Existing F3 snapshot-memory
counters do not include this new CPU mesh copy.

The interpolation is in the **undisturbed horizontal parameter plane**. It does
not invert horizontal Gerstner displacement to find an exact world-X/Z camera
intersection. GPU feedback validates the sampled vertices/parameter triangles,
not arbitrary rasterized pixels or every world-border phase. Existing large-
coordinate shader edge tests still pass. Boat presentation receives cached
continuity, but its full hull response is not replaced by this sampler.

Native client instability from [live acceptance](live-client-acceptance.md)
remains unresolved. No full Minecraft client/server was launched here, no
system/driver/security/Java installation setting changed, and no interpreter-only
measurement was presented as normal gameplay performance.

## Actual validation

Commands ran serially through the checked-in wrapper with isolated build output.

| Check | Actual result |
| --- | --- |
| New sampler red | The new test initially failed compilation because the sampling API was absent; this was not a runtime or environmental failure. |
| Packed shader red | With the new CPU packed-evaluation methods stubbed, the production-shader regression ran and failed its expected height assertion. Stubs were then replaced. |
| Pure sampler tests | Six initial tests passed in 41s before integration. |
| Intermediate integration | Compile passed in 6s; 25 focused tests passed in 44s. |
| Final focused compile/tests | **27 tests / five suites**, zero failures, errors or skips; **1m57s**, including real Minecraft BLOCK-buffer capture and production GPU feedback. |
| Broader water/weather + packaging | **831 tests / 185 suites**, zero failures, errors or skips; build **passed in 42s**. |
| Offline water suite in the same command | **222 tests / 43 suites**, zero failures, errors or skips. This overlaps root coverage and is not 222 additional unique tests. |
| GameTests / full client / optional-mod matrix | Not run: this change is client mesh sampling with independently testable packed data and shader arithmetic; native live acceptance remains separate. |

Focused coverage includes both NW-SE triangles, nonlinear vertex evaluation
before interpolation, half-block topology, dry holes, chunk-edge coordinates,
nonfinite/out-of-range queries, malformed buffer sizes/topology and independent copied
buffer lifetime. A real `BufferBuilder`/`MeshData` fixture verifies Minecraft's
BLOCK packing without requiring a loaded world or GL calls.

The GPU fixture compiled/linked the unchanged production vertex shader on
NVIDIA GeForce RTX 4090 Laptop GPU, OpenGL 3.2.0 / NVIDIA 621.01. Its original
144 depth/freeze/continuity height comparisons still pass. The new regression
adds 24 mixed packed-vertex comparisons and 12 triangle-height comparisons
against interpolated GPU feedback, spanning both mesh resolutions and three
freeze levels with regional wind/spectrum variation, currents, unequal base
heights, depths and ocean/river/lake weights. It does not render a new pixel-level
camera intersection test. Existing shader/fallback/handoff regressions passed
in the broader selection.

Exact final commands:

```powershell
.\gradlew.bat compileJava :test '-PcodexBuildDir=.codex-build' --no-parallel --tests '*WaterSurfaceMeshSamplesTest' --tests '*WaterSurfaceVertexGpuTest' --tests '*WaterSurfaceEquationTest' --tests '*WaterSurfaceTopologyContractTest' --tests '*ClientWaterImmersionContinuityTest'
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.*' waterSimulationTest build
```

The build retains an existing deprecated `WaveAnimator` warning and the
structure content-catalog fingerprint fallback warning. Neither failed the
command. Unchanged subproject tests may be up to date; the root selection is not
the repository's entire unfiltered suite. Logs are the ignored
`.codex-build/water-mesh-sampling-*.log` files and XML results under
`.codex-build/test-results/{test,waterSimulationTest}`.

## Manual acceptance and next phase

The subsequent [distance-based detail checkpoint](distance-based-mesh-detail.md)
implements bounded selection and fixed-border transition topology. Its triangle
indexing supersedes this checkpoint's regular-quad lookup and updates the CPU
copy caps to 14.25/56.25 KiB; the results above remain this earlier phase's record.

With a stable native runtime, use a disposable generated world and compare
underwater entry/exit and surface spray where river, lake and ocean depths mix.
Check all four quality tiers, paused quality changes, shoreline/loaded-chunk
frontiers, resource reload, Save & Quit/reload and dimension changes. Test
Sodium/Iris/external packs separately, then multiplayer/reconnect. Those live
items are not marked passed here.

The next rendering phase is the existing plan's seam-safe distance/complexity
mesh detail: bounded selection and hysteresis, shared border vertices, cached
transition topology and the coordinator's existing rebuild/handoff budgets.
Exact horizontal wave inversion remains a distinct sampling extension; larger
physical regions, storm surges and waterfall coupling retain their existing
canonical-conservation prerequisites.
