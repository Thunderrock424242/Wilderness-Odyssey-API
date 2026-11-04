# Irregular, filtered water foam

Date: 2026-11-02. Follows the repeating bright-patch finding in
[mesh-detail live acceptance](mesh-detail-live-acceptance.md). Baseline is
`in-dev` HEAD `7b800ed6`, preserving the acceptance documentation already in
the working tree.

## Root cause and scope

The crest foam multiplier used a product of two directional sine/cosine
carriers. That product has an exact two-dimensional lattice, so qualifying
whitewater exposed a checker pattern even with adaptive mesh detail disabled.
Unlike existing micro-wave detail, the foam mask had no pixel-footprint
filtering, so distant samples preserved strong contrast after becoming
unresolvable.

A real production-fragment-shader GPU fixture held the physical crest inputs,
background, lighting and optical state constant. Translating the old mask by
its exact carrier-lattice period gave correlation 0.99999999997. Temporarily
replacing only its multiplier with mean coverage reduced the image's standard
deviation from 0.092945 to zero. That isolates the mask's checker contribution;
it does not establish that every visible long wave band or distant fallback
artifact has the same cause.

Only `gerstner_water.fsh` changes production behavior. No Java runtime,
simulation, networking, water-volume ownership, mesh topology, renderer,
shader interface, config, texture or dependency was added or changed.

## Fix

Foam now uses smooth value noise from hashed integer lattice corners. Chunk
indices produce an integer world origin, while mesh-local coordinates retain
fractional precision. Adjacent chunks share corner values, including negative
coordinates and the world border. Reduced animation phases provide bounded,
continuous drift. Low quality uses one noise octave; other tiers add one
bounded finer octave.

Pixel-footprint filtering fades unresolved octaves toward mean coverage. It
keeps eligible whitewater present without sampling an aliased fine pattern.
Derivatives are calculated at the start of the fragment entry point before
foreground rejection can discard neighboring lanes.

Existing crest compression, coastal crest, slope/weather, shoreline/current,
impulse, freeze and material-energy rules retain their previous responsibilities.
This is a cosmetic breakup change, not persistent foam transport or a new
physical water simulation.

## Automated evidence

The initial three-test GPU run failed the two intended regressions: exact
lattice repetition and unresolved variation. The diagnostic ablation passed.
The permanent GPU fixture removes the temporary shader substitutions and image
writer; it renders the production fragment shader without modification.

| Isolated whitewater measurement | Original | Fixed |
| --- | ---: | ---: |
| Exact old-lattice translation correlation | 1.000000 | -0.039909 |
| Distant red-channel standard deviation | 0.092850 | 0.000000 |
| Resolved red-channel mean | 0.393305 | 0.397305 |
| Distant red-channel mean | 0.393251 | 0.397400 |

The fixed distant field retains nonzero whitewater relative to the no-crest
control. All four quality settings converge without aliasing in this fixture.
Other guards exercise chunk-origin equivalence, world-border sub-block
smoothness, both animation phase wraps, animation movement and frozen contrast.
These are controlled GPU pixel measurements, not FPS or whole-scene benchmarks.

The new `WaterFoamPatternGpuTest.frozenWaterSuppressesWhitewaterContrast` assertion
initially demanded less than 10% liquid pixel
contrast. Baseline isolation showed the unchanged material retains about 10.3%
after composition. The control now allows the existing 12% foam remainder; no
freeze shader behavior was changed to satisfy the test.

Focused compilation and GPU/coverage/vertex/shader/clock validation passed in
31s: **36 tests / seven suites**, zero failures, errors or skips. GPU:
NVIDIA RTX 4090 Laptop, OpenGL 3.2.0 / driver 621.01.

```powershell
.\gradlew.bat compileJava :test '-PcodexBuildDir=.codex-build' --no-parallel --tests '*WaterFoamPatternGpuTest' --tests '*WaterSurfaceCoverageRenderTest' --tests '*WaterSurfaceVertexGpuTest' --tests '*Water*Shader*Test' --tests '*WaterShaderTimePhaseTest'
.\gradlew.bat test build '-PcodexBuildDir=.codex-build' --no-parallel
```

The unfiltered `test build` invocation completed successfully in **1m 19s**.
Root XML reports **1,870 tests / 474 suites**, zero failures or errors and five
skips (1,865 executed successfully). Unchanged subproject tests were up to date.
The skipped checks were:

- `QuestPublicationStoreTest.containedPathsRejectSymbolicDirectories` — the
  account cannot create symbolic links.
- `StructureGenCliCatalogTest.rejectsCatalogAncestorSymbolicLinkWhereSupported`
  and `rejectsCatalogFileSymbolicLinkWhereSupported` — symbolic-link privilege
  is unavailable.
- `BunkerStructureRegressionTest.readsAndInspectsTrackedBunkerWithoutMutatingIt`
  — requires its separate opt-in 4 GiB fixture task.
- `CloudShaderGpuTest.packagedCloudShaderCompilesAndPreservesFixturePixels`
  — the unrelated cloud-driver opt-in property is disabled.

No permissions or security settings were changed to bypass those assumptions.
## Live comparison and cleanup

The same ignored single-client profile and saved ocean position 748/70/-984
were reopened with the fixed shader. Automatic selection again chose High
at 2560 × 1494 with the existing 6 GiB limit, normal JIT and the build's two
narrow compiler exclusions. No client config changes were needed.

Once chunk loading settled, the exact repeated foam grid was no longer visible;
crest whitewater had irregular patches. Existing longer wave bands remain part
of the wave/material presentation and are not claimed to have been eliminated.
The before/after images have different animation and daylight times; they are
the same saved viewpoint, not a frame-matched pixel comparison.

Local in-game F2 captures:

- Before: `.codex-build/run-water-lod-20261102/screenshots/2026-11-02_16.06.55.png`.
- Fixed: `.codex-build/run-water-lod-20261102/screenshots/2026-11-02_17.06.36.png`.

One visible spot reading was 58 FPS at the 60 FPS cap and 5.2 ms integrated-server
tick time. These are not a measured speedup. F3+T completed at 17:06:55 and the
fixed surface returned. Save & Quit saved all dimensions normally at 17:08:21;
Quit Game logged `Stopping!` at 17:08:58. The client command completed with exit
zero and **BUILD SUCCESSFUL in 5m 55s**, including loading and pauses. No native
crash occurred in this invocation. The client and integrated server are stopped.

This fixes the isolated periodic foam mask and its unresolved sampling, rather
than certifying the complete water engine. Detailed mesh/GPU timings, continuous
LOD flight, sustained storm cases, optional renderer/shader combinations and
multiplayer remain outside this focused correction. No commits, pushes, system
Java/driver changes or security/permission changes were performed.

Local logs and diagnostic images remain ignored under
`.codex-build/water-foam-pattern-*.log` and
`.codex-build/reports/water-foam-pattern/`.
