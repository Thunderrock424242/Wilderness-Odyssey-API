# Advanced water review and shallow-depth checkpoint

Date: 2026-10-29. Baseline: clean local `in-dev`, HEAD `04fb979f`.
This is a focused extension of Hybrid Water Engine 2.0, not completion of the
long-term cinematic ocean, coastline, river and finite-water roadmap.

## Existing architecture and implemented work

| Responsibility | Existing owner and boundary |
| --- | --- |
| Generation and existing worlds | Generation-only state mapping writes namespaced water and compact `GeneratedWaterChunk` spans. Ordinary runtime leaves old vanilla chunks alone; explicit bounded conversion is separate. No new terrain generation or completed-chunk scan is needed here. |
| Canonical ownership | `WildernessWaterAuthority` resolves sparse canonical overrides before generated spans and provisional projections. `CanonicalWater` owns accepted transfers, displacement reservoirs and projection. Cosmetic waves, foam and shoreline grids cannot spend water. |
| Reactive reservoirs | `ReactiveWaterController` is called by the existing canonical queue. `CanonicalSurfaceFlux` proposes bounded face transfers; accepted canonical transactions determine debit. Experimental regions remain disabled by default, limited to 32 columns and eight slots per column, with voxel fallback for unsupported geometry. |
| Hydrology and weather | `WaterPerformanceIntegration` uses the existing Data Engine for sea state, regional hydrology, shoreline maintenance and publication. Regional stores have explicit rain/ET/ocean receipts; funded flood claims support recession. Separate derived shoreline grids are not another inventory. |
| Forces and local fluid | `WaveEntityPhysics` applies server buoyancy, drag and current response through existing services. Client boat tilt/wakes are presentation. Server SPH temporarily owns transferred canonical water until settlement; it has body/particle budgets and round-robin advancement. |
| Synchronization and lifecycle | Existing generated attachments, paged baselines/revision deltas, immutable client snapshots and unload cleanup remain. No packet, persistence format or public API changes in this checkpoint. |
| Surface rendering | `WaterRenderCoordinator` remains the single owner. `WaterChunkMeshCache` builds dirty snapshot meshes with shared world-aligned corners, loaded-edge anchors and acknowledged fallback handoff. Low/Medium use one subdivision per block; High/Cinematic use two. This is quality-dependent topology, not camera-distance adaptive topology. |
| Waves and optics | `WaterShaders` supplies the existing Gerstner program with synchronized regional spectra, tide, cached depth and eight bounded wake/impact slots. `WaterSurfaceEquation` mirrors vertical composition. Shared scene capture, refraction/absorption, Fresnel/environment/SSR reflection and underwater optics already exist. |
| Coastlines | `ClientCoastalSegmentStore` caches bounded loaded shoreline candidates. `CoastalRunupRenderer` appends terrain wash/wetness to the coordinator's batch; native Gerstner geometry owns crests. Existing coastal timing, foam, spray and sound remain. |
| Rivers and waterfalls | River reach metadata, hydraulic currents and flow-aligned waves feed the same surface. `RiverSoundscape` and `WaterAmbientEffects` use bounded neighborhoods/budgets for current, confluence, rapids and waterfall cues. General continuous waterfall sheets and fully coupled receiving-water turbulence remain further work. |

The reservoir-column, disk persistence and controlled storm/runoff/flood
checkpoints are already implemented; see [reservoir columns](reactive-reservoir-columns.md),
[disk persistence](reservoir-disk-persistence.md) and
[storm runoff](storm-runoff-checkpoint.md). Their fixture acceptance does not
establish arbitrary open-world geometry or multiplayer performance.

The inspected active paths do not need another renderer, authority, scheduler
or physics owner. CPU/GPU wave arithmetic is intentionally mirrored and tested.
Legacy ocean/shore emitters remain in source but are not invoked by the current
coordinator; unrelated deletion/refactoring was not part of this change.

## Confirmed visual problem and implemented correction

The BLOCK-format mesh reserves three low alpha bits for depth. Its previous
linear 0-to-24-block encoding had a 24/7-block step: depths below approximately
1.714 blocks became a zero-depth shader input. That discarded important parts
of the existing shallow shoaling and coastal crest envelope. It did not remove
physical water or surface coverage.

The same three bits now select the nearest depth from:

`0, 0.5, 1.5, 3, 6, 10, 16, 24` blocks.

| Input depth | Previous decoded depth | New decoded depth |
| ---: | ---: | ---: |
| 0.5 | 0 | 0.5 |
| 1.5 | 0 | 1.5 |
| 3 | approximately 3.429 | 3 |
| 24 | 24 | 24 |

`WaterSurfaceVertexData` keeps the current/shore payloads and five display-color
bits unchanged. `gerstner_water.vsh` decodes the palette before interpolation;
its `depthFactor` still means blocks divided by 24. Existing fragment optics
and enclosed-water fetch therefore keep their units and interface.

`ClientWaterImmersion.visibleSurfaceHeight` now supplies the same quantized
depth for a custom uniform-column sample, instead of raw column depth. Both
shoaling and lake wind-fetch inputs consume that value. External shader/stock
fallback behavior is unchanged. No wave profile, carrier, tide, freeze rule,
water transfer, terrain geometry or simulation setting was replaced.

This deliberately trades some deep-water precision for shallow detail. Depth
is still a bounded approximation; values below 0.25 blocks select zero, midpoint
ties select the deeper level, depths above 24 clamp, and nonfinite/negative
inputs select zero. This is derived mesh data, not a world-save migration.

## Findings and remaining architecture work

- **Corrected:** shallow depth loss in the GPU mesh and unquantized depth in
  uniform-column CPU presentation.
- **Confirmed remaining limitation:** CPU immersion samples column/body inputs
  and evaluates a continuous equation, whereas the actual mesh averages corner
  metadata and rasterizes displaced subdivided triangles. Mixed-column depth,
  weights/current and arbitrary points on a crest are not proven exact CPU/GPU
  matches. The feedback test establishes sampled parameter/equation agreement,
  not a general inverse of horizontally displaced geometry.
- **Confirmed remaining limitation:** the active cache chooses subdivision by
  quality, without camera-distance/wave-complexity LOD. Existing legacy dynamic
  ocean LOD settings do not make the snapshot VBO topology adaptive.
- **Performance concerns to measure:** high-quality large ocean VBOs, coastal
  cache refreshes and local SPH can be expensive. Existing budgets bound work,
  but source inspection alone does not establish the live bottleneck. No new
  scan, mesh rebuild trigger, per-frame allocation or GPU resource was added.
  Encoding performs at most seven comparisons at each existing encoding/sample
  call; shader decoding is one fixed eight-entry lookup.
- **Deferred physics:** larger/open reactive domains, general pressure flow,
  controlled openings with ambiguous geometry, explicit storm-surge receipts,
  and deeper river/waterfall coupling require separate conserved fixtures.

## Compatibility evidence and limits

| Area | Evidence and limit |
| --- | --- |
| Sodium / Iris | Existing optional mixin probes and acknowledged section handoff remain. Active or unknown external shader ownership preserves the compatibility path; built-in palette decoding is isolated to our shader. No live optional-mod session was run here. |
| External shader packs | BLOCK format, atlas coordinates, uniforms and stock fallback were preserved. Existing shader-interface/coverage regressions passed in the broader suite. Pack-specific visuals remain untested. |
| Distant Horizons | No dedicated water bridge was found in the inspected water/render compatibility path. Distant water, fog and LOD intersections need actual integration acceptance. No support claim is made. |
| Tectonic, Terralith, Continents, Regions Unexplored | The change uses existing derived snapshot depth, not biome IDs, generator assumptions or terrain edits. This preserves the compatibility approach; those installed-mod combinations were not tested. |
| Existing worlds / multiplayer | No stored data or wire format changed. Existing conservation/sync regressions passed, but the outstanding live two-client join/agreement/reconnect test was not completed here. |
| Quality presets | The same codec and wave components serve all four tiers; topology and optical budgets remain under existing quality controls. This is not FPS/MSPT certification of any preset. |

The earlier native failure evidence was reviewed separately. Alice's recorded
Corretto interpreter crash identifies `glfw.dll+0x101ab` while
`GLFW.glfwPollEvents` runs in Minecraft's display loop. Earlier C1 attempts
identify JVM runtime stubs. These records do not establish a water Java failure
or determine the native root cause. The prior multiplayer configuration
timeouts and native failures remain open; see
[live acceptance](live-client-acceptance.md). No full Minecraft process was
launched, global runtime/security/driver setting changed, or interpreter-only
result used to claim normal gameplay performance in this checkpoint.

## Actual validation

All commands ran serially using the checked-in wrapper and Java 21. The first
sandboxed focused invocation was blocked by access denied on the existing
Gradle wrapper distribution lock. The approved rerun used the existing cache;
no permission/ACL or cache reconstruction was performed.

1. **Expected red:** `:test` selecting `WaterSurfaceVertexDataTest` and
   `WaterSurfaceVertexGpuTest`, with `'-PcodexBuildDir=.codex-build' --no-parallel`.
   Before implementation: 11 tests, four expected assertion failures (three
   codec checks and actual production GPU shallow-depth decoding), 28 seconds.
2. **Passed:** `compileJava '-PcodexBuildDir=.codex-build' --no-parallel`, 3 seconds.
   Existing unrelated `ClientTickHandler`/deprecated `WaveAnimator` removal
   warning remains; no new compile error.
3. **Passed:** focused command below, 27 tests / five suites, zero failures,
   errors or skips, 27 seconds. Actual GPU output included 144 height comparisons,
   plus arbitrary-depth, tide/impulse and near/far chunk-edge cases.
4. **Passed:** broader command below, 35 seconds. Root water/weather selection:
   **823 tests / 184 suites**; offline `waterSimulationTest`: **222 / 43**;
   both zero failures, errors and skips. These suites overlap. Packaging/build
   completed successfully; unchanged subproject tests may be up to date.

```powershell
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSurfaceVertexDataTest' --tests 'com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSurfaceVertexGpuTest' --tests 'com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSurfaceEquationTest' --tests 'com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSurfaceGerstnerMirrorTest' --tests 'com.thunder.wildernessodysseyapi.watersystem.water.wave.DepthWaveResponseTest'
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.*' waterSimulationTest build
```

The production vertex shader compiled/linked and ran with transform feedback on
NVIDIA GeForce RTX 4090 Laptop GPU, OpenGL 3.2.0 / NVIDIA 621.01. This verifies
sampled arithmetic on that driver. Existing production-fragment coverage tests
also passed. No new GameTest is appropriate for a client vertex codec with no
Minecraft world mutation; existing GameTest fixtures were not rerun. The broader
root selection is not the repository's entire unfiltered test suite.

Local logs are `.codex-build/water-depth-{red,compile,focused}.log` and
`.codex-build/water-depth-regression-build.log`; XML counts are under
`.codex-build/test-results/{test,waterSimulationTest}`. Generated output is not
part of the source change. Final diff/whitespace review is recorded in the
[execution plan](../superpowers/plans/2026-10-29-water-depth-precision.md).

## Next implementation phase and acceptance

First share bounded mesh-input sampling between CPU presentation and cached
surface generation. Cover adjacent unequal-depth/body columns, frozen state,
currents, ownership edges and the actual subdivided triangle interpolation in
pure tests and production GPU fixtures. Do not introduce another surface owner.

Then add adaptive detail through the existing cache/coordinator in phases:

1. Define/test a bounded distance/complexity selector with hysteresis and fixed
   quality ceilings, keeping world-aligned border vertices identical.
2. Build seam-safe transition topology and retain the old published buffer
   until the existing rebuild/handoff path accepts the replacement. Reuse its
   count/time budgets; no animation-driven rebuilds or per-frame world scans.
3. Measure steady and moving-camera VBO/triangle cost and CPU/GPU times on all
   tiers, then validate optional renderer/shader/Distant Horizons boundaries.

Live acceptance still needs a stable native runtime. In a disposable generated
world, compare shallow coast, river and lake surfaces through all four tiers;
check shoaling, crests, ice, surface entry, F3+T, Save & Quit/reload and chunk
frontiers. Run optional-mod combinations separately, then complete normal
two-client synchronization/reconnect and a measured long-duration storm/boat
session. None of those manual items is marked passed by this shader fixture.
