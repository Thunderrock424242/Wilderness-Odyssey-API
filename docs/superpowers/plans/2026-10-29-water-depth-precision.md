# Shallow-water depth precision implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. Steps use checkbox tracking. Repository instructions and the user's execution request take precedence over skill defaults for approval, branching, commits and delegation.

**Goal:** Retain meaningful shallow-water shoaling and crest inputs in the existing compact water mesh.

**Architecture:** Keep the BLOCK vertex format, coordinator, cached topology and Gerstner equation. Replace only the three-bit linear depth codec with eight nonuniform levels: 0, 0.5, 1.5, 3, 6, 10, 16 and 24 blocks. CPU presentation uses the same quantized depth as the shader for a uniform sampled column. Mixed-column mesh interpolation remains an existing approximation in CPU immersion, not exact rasterized triangle reconstruction.

**Tech stack:** Java 21, NeoForge/Minecraft 1.21.1, GLSL 150, JUnit 5, existing LWJGL transform feedback.

**Spec:** User attachment, “Wilderness Odyssey — Advanced Water Engine Development”, received 2026-10-29; scope/design decisions recorded below.

## Global constraints

- Work on the existing `in-dev` checkout; no commits, pushes, unrelated branches or dependencies.
- Preserve one render owner and one canonical water authority. No terrain, inventory, packet, saved-format, scheduler or physics changes.
- Keep all work bounded; one Gradle/development process at a time, isolated `.codex-build` output.
- Preserve shader fallback and client/server separation. Experimental physical regions stay disabled by default.
- Native JVM/GLFW failures remain a separate acceptance blocker; no global Java, security, driver or permissions changes.

## Review focus

- Positive sub-two-block depths must not all decode as dry.
- Malformed depths remain finite and bounded; negative/nonfinite input decodes as zero, oversized input as 24.
- All eight payload codes, nearest-level boundaries and monotonically increasing depth remain deterministic.
- Current/shore payloads and display color high bits must remain independent of depth.
- Production GPU decoding, shoaling, freeze, tide and loaded-edge behavior must agree with CPU inputs; no mesh rebuild on animation.

## Design and alternatives

Inspection at clean `in-dev` HEAD `04fb979f` confirms the existing owners described in `docs/watersystem/overview.md`. The linear depth code has a 24/7-block step, so 0.5 and 1.5 block depths become zero; that discards the shallow crest band before the shader evaluates it. CPU immersion currently uses unquantized column depth, another source of uniform-column disagreement.

Chosen implementation: a fixed shallow-focused palette in the existing low alpha bits, plus a shared CPU quantization helper. The palette preserves zero/deep endpoints and represents the existing shoaling peak at 3 blocks. This is a client-derived mesh encoding, not serialized water data.

Alternatives: a new vertex attribute would improve precision further but would compromise the stock BLOCK fallback and require a broader interface change. Adaptive mesh LOD would reduce distant cost but needs crack-free transitions, bounded rebuild scheduling and acceptance; it is a separate next phase. Neither is needed for this bounded correction.

## Task 1: Depth codec and surface input parity

**Files:** `WaterSurfaceVertexData.java`, `ClientWaterImmersion.java`, `gerstner_water.vsh`; tests `WaterSurfaceVertexDataTest.java`, `WaterSurfaceVertexGpuTest.java`, existing surface shader contracts where applicable. All Java files are in the existing water/render packages.

**Interfaces:** Preserve `encodeColor(...)` and `decodeDepthFactor(int)`. Add package-local `quantizedDepth(float)` returning blocks. The shader's `depthFactor` continues to mean decoded depth divided by 24, so fragment optics and enclosed-water fetch retain their units.

- [x] Add palette, shallow-depth, nearest-boundary, bounded malformed-input and payload-isolation regressions. Expand real production-vertex feedback across all eight palette depths, arbitrary quantized input depths, calm/storm spectra, freeze and continuity.
- [x] Run focused CPU tests and GPU feedback against the old implementation and record the expected failures.
- [x] Implement the bounded nearest-depth codec and matching GLSL decode. Use quantized depth in the existing CPU visible-surface sampler; preserve wave profiles, clocks, freeze/tide composition and fallback.
- [x] Compile and rerun focused regressions. Run broader water/weather/render regressions and build serially. Record actual counts, failures/skips and environment limits.

## Task 2: Review and report

- [x] Review scoped diff, shader interface/fallback, unloaded boundaries, generated output and public API compatibility.
- [x] Update water documentation with architecture inventory, implemented change, confirmed/possible concerns, actual validation and remaining compatibility/performance acceptance.
- [x] Record a phased seam-safe adaptive mesh follow-up rather than attempting it here.

## Execution ledger

- Inspection: clean `in-dev`, HEAD `04fb979f`; prior reservoir/storm/native-failure work reviewed. No subagents or game processes launched.
- Ruling: execute the requested focused improvement directly in this checkout, as required by the user's explicit execution order and AGENTS.md; do not add approval gates or commits from skill defaults.
- Runtime scope: use independently testable codec/shader work. A hidden GPU regression context may be used; do not retry unstable full Minecraft/multiplayer acceptance in this phase.
- Task 1 red: sandbox wrapper-lock access denied; approved normal-wrapper retry completed in 28s with 11 tests / four expected assertion failures, including actual GPU depth decoding.
- Task 1 green: compileJava passed in 3s; focused tests passed in 27s, 27 tests / five suites, zero failures/errors/skips. Production GPU feedback: 144 height comparisons on RTX 4090 Laptop GPU, OpenGL 3.2.0 / NVIDIA 621.01.
- Broader regression/build passed in 35s: root water/weather 823 tests / 184 suites; offline water 222 / 43, zero failures/errors/skips. Suites overlap; unchanged subproject tests were up to date. No new GameTest is needed for this client-only codec and no full Minecraft runtime was launched.
- Final review: self-review per repository single-agent workflow; BLOCK layout, optical units, current/shore bits and public APIs preserved. No inventory, packet, persistence, world scan, scheduler, fallback or physical-region toggle changes. Existing CPU mixed-column/rasterized-surface approximation is explicitly deferred, not claimed fixed.
- Diff review: git diff --check and new-document whitespace/conflict checks passed. Only five focused Java/shader/test files and five plan/report/index documentation files changed; generated logs/artifacts remain ignored. No commit, push or branch change.
- Task 2 complete: architecture, actual results, native-failure separation, optional-mod acceptance limits and phased mesh-input/LOD follow-up recorded in docs/watersystem/advanced-water-depth-checkpoint.md.
