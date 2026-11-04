# Water foam pattern fix plan

> Execution: main agent inline, following the repository's single-process and
> no-routine-subagent rules. No commits or branch changes are authorized.

**Goal:** Fix the repeated bright ocean checker pattern observed in
[live acceptance](../../watersystem/mesh-detail-live-acceptance.md), preserving
wave geometry, water ownership, optics and renderer lifecycle.

**Scope:** The existing GLSL 150 surface fragment shader, a focused GPU pixel
regression fixture, and the acceptance documentation. Use existing packed world
coordinates, reduced animation phases and graphics-test conventions. No new
render pass, texture dependency, config or simulation changes.

- [x] Isolate the periodic foam mask with a real-shader pixel fixture and an
  ablated diagnostic variant. Compare resolved patterns and distant footprints.
- [x] Add failing behavior checks for exact lattice repetition and unresolved
  texture aliasing, with controls that keep whitewater visible. Check chunk and
  world-border continuity and frozen-water behavior.
- [x] Replace only the confirmed problematic mask with bounded, world-stable,
  filtered irregular breakup. Preserve crest/weather/shore eligibility and
  existing material energy composition.
- [x] Run focused GPU/render tests, then relevant water/weather regression and
  packaging checks serially. Inspect the diff and preserve prior documentation.
- [x] Run the smallest useful isolated client comparison with the same saved
  ocean view; restore test settings and stop normally. Record actual results
  and any remaining visual/performance limits.

Review focus: negative and world-border coordinates, chunk origins, reduced
time-phase wrap, unresolved footprints, low-quality fallback, frozen coverage,
nonzero eligible foam, and no unexpected work outside the fragment shader.

## Execution evidence

- Initial GPU red: three tests, two expected failures (periodic correlation and
  unresolved variation), 36s. Mask ablation reduced deviation 0.092945 to zero.
- First implementation run: the two regressions and edge/wrap guards passed;
  one newly added freeze control failed because it demanded less than 10% pixel
  contrast. The original shader also retains about 10.3% after material
  composition. A separate 35s baseline/ablation run confirmed this test
  assumption. The guard now permits the existing 12% foam remainder; no freeze
  production behavior changed.
- Temporary ablation compilation/image-writing code was removed from the
  permanent test. Focused compile/GPU/render/shader tests passed in 31s.
- Derivatives are evaluated before foreground discard; integer lattice origins
  preserve chunk and world-border precision. No Java runtime or simulation edits.
- Unfiltered `test build` passed in 1m 19s. Root: 1,870 tests, zero failures or
  errors, five documented unrelated skips. All 36 focused checks ran without
  skips; the permanent new GPU fixture has seven behavior tests.
- Live comparison at the saved ocean viewpoint showed irregular foam in place
  of the exact repeated grid. F3+T returned the fixed surface, followed by
  ordinary world save and client exit. `runClient` passed in 5m 55s, exit zero.
  The full result and limits are in `docs/watersystem/foam-pattern-fix.md`.
