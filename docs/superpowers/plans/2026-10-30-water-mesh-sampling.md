# Shared uploaded-water surface sampling

> Execute inline with the existing planning, TDD and verification skills. User execution authorization and AGENTS.md override skill defaults for extra approval gates, delegation, branches and commits.

**Goal:** Make client immersion and surface effects reuse the actual uploaded mesh's packed depth, current, body blend, base height and subdivided triangle layout.

**Baseline:** Clean `in-dev`, HEAD `34d37be9`. The shallow-depth checkpoint is committed. No new renderer, water authority, physics owner, scheduler, dependency or persistent/network format.

## Design

Capture a compact immutable CPU copy of the BLOCK vertex buffer before the existing VBO upload consumes it. Obtain layout offsets through VertexFormat APIs, retain only height/color/body-normal payloads plus a bounded quad lookup, and keep it alongside its MeshGroup in the existing cache. This copies real packed bytes instead of independently reproducing corner averaging, subdivisions or SNORM conversion. Sampling visits only the selected triangle's three vertices, evaluates the existing CPU wave equation at those vertices and interpolates their heights. Published handoff, ownership, unload, replacement and clear remain under the current coordinator/cache.

CPU vertex evaluation must decode the same current/depth/body payloads, use the shader's linear chunk-corner spectrum interpolation (including normalized wind), and preserve lake fetch, wake selection, tide and frozen envelopes. Missing/unpublished geometry takes the flat existing fluid fallback. Continuity follows the cached triangle instead of fresh neighborhood eligibility scans.

This first phase samples triangles in the undisturbed horizontal parameter plane. Inverting horizontal Gerstner displacement and the shader's approximate large-coordinate phase arithmetic are separate work; do not claim exact rasterized world-X/Z intersection. Adaptive mesh LOD is the subsequent phase, not an extra renderer in this change.

## Files and responsibilities

- New `water/render/WaterSurfaceMeshSamples.java`: bounded packed-buffer capture, decoded vertex values and pure triangle sampling; no Minecraft world or GL ownership.
- `WaterChunkMeshCache.java`: retain/release samples with the existing cached upload, preserving public MeshGroup constructor/API.
- `WaterRenderCoordinator.java`: expose only the current level's owned, published mesh samples.
- `WaterSurfaceEquation.java`: packed vertex height and chunk-corner spectrum composition through the existing equation.
- `ClientWaterImmersion.java`: consume cached triangle samples for height/continuity; retain external/stock/mobile fallback.
- Tests: pure packed-buffer/interpolation regressions and production vertex-shader feedback for uneven depth, current, mixed body weights, frozen/tide state, subdivisions and boundaries.

## Checklist

- [x] Write focused sampling regressions and verify the absent implementation fails.
- [x] Implement pure bounded capture/interpolation and packed equation inputs; compile and run focused tests.
- [x] Wire capture to upload lifetime and published client sampling without per-frame world scans.
- [x] Compare mixed packed vertices/triangles against production GLSL; test both subdivisions and malformed/missing data.
- [x] Run broader water/weather, offline water and packaging validation serially.
- [x] Review diff/API/lifecycle/fallback/memory bounds and document actual results/limits.

## Review focus

- Decode real BLOCK bytes with supplied format offsets and native byte order; do not retain native buffer memory.
- Sampling a dry quad must not borrow a neighboring wet quad or request a chunk.
- Subdivision and NW-SE diagonal must match actual emission; nonlinear waves are evaluated before triangle interpolation.
- Raw normalized-byte body channels determine both blend normalization and continuity.
- CPU metadata disappears with the same cached upload on replacement, unload, owner change and resource teardown.

## Validation

Use the wrapper with `'-PcodexBuildDir=.codex-build' --no-parallel`; only one Gradle/development process at a time. Compile first, focused CPU/GPU tests next, then water/weather selection plus waterSimulationTest and build. Full Minecraft/multiplayer runs remain outside this phase because of recorded native instability. Do not alter JVM defaults, drivers, permissions or security settings.

## Ledger

- Source inspection confirmed corner averaging, per-subdivision interpolation and packed BLOCK inputs differ from the prior single-column sampler. Existing MeshData.vertexBuffer and VertexFormat.getOffset APIs confirmed by targeted dependency inspection.
- Ruling: retain the existing public MeshGroup record; associate CPU samples in the cache's private entry so no public constructor is removed.
- Red evidence: new API absence caused expected compile errors (27s); after API setup, the packed mixed-vertex GPU regression ran and failed its expected height assertion with stub evaluation (41s).
- Initial pure capture/triangle suite: six tests passed in 41s. Integration compile passed in 6s, followed by 25 focused tests in 44s.
- Final focused compile/tests passed in 1m57s: 27 tests / five suites, zero failures/errors/skips, including actual Minecraft BLOCK bytes and production GPU mixed vertices/triangles. Original 144 height comparisons retained; 24 packed-vertex and 12 triangle comparisons added.
- Broader water/weather + offline water + build passed in 42s: root 831 tests / 185 suites, offline 222 / 43, zero failures/errors/skips. Suites overlap. No full Minecraft runtime or GameTest launched; no optional-mod/performance certification claimed.
- Lifecycle review: samples share the private cached upload entry; remove/replace/clear release ownership, level identity and handoff gate reject stale/unpublished samples, and immersion cache identity invalidates on upload changes. Public MeshGroup and existing public sampler/equation signatures preserved. No new GL handle is owned by samples.
- Memory review: retained primitive arrays at most 13 KiB (one subdivision) / 52 KiB (two); three vertex evaluations per query, no retained native buffer. Extra CPU work and memory remain a profiling concern, not a claimed speedup.
- Final source diff reviewed inline under AGENTS.md's single-agent workflow. Documentation records the parameter-plane limit, native-runtime blocker, commands, actual counts and next adaptive-detail phase. No commits, pushes, branches, global runtime/security settings or authoritative water changes.
