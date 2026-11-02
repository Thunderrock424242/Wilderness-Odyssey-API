# Seam-safe water mesh detail

> Execute inline with planning, TDD and verification. Preserve the previous uncommitted shared-sampling work; no commits, branches, pushes or routine subagents.

**Goal:** Reduce distant calm High/Cinematic surface geometry through the existing cache and rebuild budgets, preserving water ownership and sampling.

**Baseline:** `in-dev` HEAD `34d37be9` plus the completed uncommitted 2026-10-30 shared-mesh sampling phase. Minecraft/NeoForge 1.21.1, Java 21, BLOCK vertices and the existing Gerstner shader remain.

## Design

Use two existing detail resolutions: half-block interiors and one-block coarse surfaces. All chunk outer edges have identical one-block spacing, independent of neighboring quality/detail or rebuild order. Fine border columns transition through center fans, paired into ordinary QUADS with at most one degenerate triangle per edge column. Fixed outer edges avoid dependency waves between neighbor rebuilds. Fine interiors have four quads per column; far coarse surfaces have one. Low/Medium retain their one-block ceiling.

Keep shallow/partial/current-rich/uneven chunks fine at the permitted quality ceiling. Otherwise use horizontal camera distance, existing local sea strength, 24-block hysteresis and a 40-tick minimum fine dwell before demotion. A client enable toggle and near-distance setting (64 blocks by default) provide rollback/tuning; storm energy extends that distance up to twice the configured radius.

Check at most eight existing cache entries per frame using a retained weak iterator; weather resampling is capped to once per ten game ticks per entry. Queue changes into the existing deduplicated snapshot mesh queue without invalidating neighbors. Existing count/time rebuild budgets, old published meshes and handoff own execution. Initial builds choose detail in the same bounded build path.

The shared CPU sampler must consume transition triangles too. Store packed half-grid positions with the positive normalized body channels in the existing payload word; use at most eight triangle references per block column. Query searches at most eight triangles, evaluates only the selected three vertices, and keeps the parameter-plane limitation. No new world scan, render pass, scheduler, canonical state or packet.

## Tasks

- [x] Add failing selection/hysteresis/dwell, topology area/winding/edge/count and detail-queue regressions.
- [x] Implement pure policy and fixed-border topology; extend packed sampling for actual transition triangles while retaining earlier tests.
- [x] Integrate client config, bounded cache checks and initial/detail rebuilds with the existing queue/handoff.
- [x] Add production-shader transition edge and CPU sampling regressions; compile and run focused checks.
- [x] Run broader water/weather, offline water and build serially; review scoped diff and document actual results/limitations.

## Review focus

- Adjacent coarse/fine chunks and different quality ceilings share exactly the same edge vertices.
- Transition faces cover each column once, with correct winding and no interior holes; CPU sampling must not borrow dry triangles.
- LOD refresh never queues a neighbor halo or bypasses count/time budgets.
- Small distance/weather changes do not continually rebuild; Low/Medium never gain extra detail.
- Cache removal/clear/owner changes release pending detail state and CPU sampling data together. Public MeshGroup and existing public equation signatures remain.

## Validation / ledger

Use the checked-in wrapper, quoted `'-PcodexBuildDir=.codex-build' --no-parallel`, one Gradle/development process at a time. Hidden GPU fixtures are allowed; do not retry native-unstable full clients or change system settings. Synthetic submitted triangle counts are not FPS/MSPT certification.

- Inspection: previous shared sampler is still uncommitted and retained. It currently accepts only regular quads; transition support is required in this phase.
- Chosen fixed-border approach avoids coordinated neighbor rebuilds. Coarser-than-one-block surfaces and general horizontal Gerstner inversion remain separate work.
- Initial red: seven tests / six expected assertion failures in 43s. One later test compile failed on a nonexistent cleanup helper; corrected the test to remove its own entries. Corrected transition red ran 17 tests / two expected failures in 27s (old rectangular capture and missing detail queue).
- Core green: compile plus 17 tests passed in 30s. Integrated compile passed in 3s. Focused integration passed in 36s: 37 tests / six suites, zero failures/errors/skips.
- Broader regression/build passed in 43s: root water/weather 843 tests / 187 suites; offline water 222 / 43, zero failures/errors/skips. Suites overlap; not the entire unfiltered repository test suite.
- Geometry evidence: full wet fine fixture 1,020 QUADS / 2,040 submitted triangles, coarse 256 / 512, approximately 74.9% reduction. All border vertices, winding, column area and 162 transition sample points checked. Hidden production shader edge/normal and transition-height tests passed on RTX 4090 Laptop GPU / OpenGL 3.2.0 NVIDIA 621.01.
- Lifecycle/API review: public MeshGroup constructor/equation APIs retained; added detail queue method is client-only and reuses existing deduplication. Eight cache checks/frame, ten-tick sea cache, hysteresis/dwell and existing upload/time budgets remain bounded. Weak cursor and detail state clear with the existing cache. No public authority, persistence, packet or physical toggle changes.
- Retained primitive sample caps: coarse 14.25 KiB / fine 56.25 KiB, with no native buffer retained. Submitted counts include degenerate transition index triangles; live frame rate, temporal popping, section-compile overhead and optional mods remain unmeasured.
- Final source review performed inline under the repository single-agent workflow. Prior uncommitted sampling work preserved and extended; no commits, pushes, branch changes, new dependencies, Minecraft launches or system changes. Final report: docs/watersystem/distance-based-mesh-detail.md.
