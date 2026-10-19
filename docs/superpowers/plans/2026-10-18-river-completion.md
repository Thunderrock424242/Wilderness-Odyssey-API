# River network completion implementation plan

**Goal:** Continue the original complete river-network brief on PR 553, implementing real connected generation and the missing integrations while preserving existing water authority and saves.

**Architecture:** The existing noise generator is the only supported read-only terrain predictor. A bounded, world-aligned source-to-receiver planner certifies each complete fine route, unions admitted geometry, then clips chunk-owned slices. Exact post-carver terrain and existing structure/block/write checks remain the final placement authority. Generated spans own permanent water; regional hydrology owns weather-driven discharge; canonical parcels own detailed disturbances. Client presentation consumes these existing snapshots.

**Tech stack:** Minecraft 1.21.1, NeoForge 21.1.262, Java 21, checked-in Gradle wrapper.

**Spec:** Original user-supplied river-network brief, with the ownership and acceptance constraints in `docs/watersystem/river-network.md`.

## Global constraints

- Use the existing PR worktree and feature branch; preserve all other checkouts.
- One owned Gradle/Minecraft process at a time, `.codex-build`, `--no-parallel`.
- No force loads, recursive generation, future waits, second water inventory/scheduler/renderer, or existing-chunk carving.
- Read-only prediction cannot certify arbitrary carvers, custom generators or foreign decoration. Such sites must be conservatively rejected; no global compatibility claim.
- Original brief explicitly permits bounded conservative fallback and gated physical drying; do not fake mass/stage changes.

## Review focus

- Negative-coordinate and shared-border queries must choose identical routes and geometry.
- Unknown terrain and exhausted work budgets must reject complete routes, not truncate a flowing river.
- A native receiving water surface or sealed basin must exist at every admitted route outlet.
- Zero modeled discharge must stay zero; canonical current ownership and SI units remain unchanged.
- Old saves, partial attachment updates, disabled visuals and dedicated servers must remain valid.

## Tasks

### 1. Connected deterministic generation

- [x] Add pure world-aligned bounded source-to-receiver tests for seed, order, parallelism, confluence, endpoint and read budgets; observe RED.
- [x] Implement predictor abstraction and complete fine route certification using supported noise column APIs without chunk access.
- [x] Rasterize continuous edge geometry with bounded width/depth and gentle bends; reconcile downstream stages and receiving surfaces.
- [x] Integrate chunk-owned writes, exact natural supports/banks, structures, old/new boundary rejection and whole-work budgets.
- [x] Add real ProtoChunk and noise-generator acceptance checks; measure planning cost and retain opt-in conservative fallback where prediction cannot certify terrain.
- [ ] Complete physical route admission across distant old/protected/carved/decorated slices; current local preflight cannot guarantee this without a supported terrain certificate covering the complete route.

### 2. Basins, outlets and waterfalls

- [x] Extend local lake spill/outlet routing, climate-weighted connected source selection and bounded larger falling intervals with regression tests.
- [x] Preserve compact versioned reach persistence; add old-format and native save/reload tests.
- [ ] Add climate-selected springs/wetlands, large lake networks and tall mountain plunge geometry.

### 3. Data-driven bank ecology

- [x] Add validated datapack bank profiles, biome tag defaults and bounded generation-only vegetation/material placement.
- [x] Reuse existing habitat APIs and loaded-only river classification; avoid entity spawning or rerunning arbitrary decoration.
- [x] Add palette, reload, protected block and enabled vegetation survival/budget tests.

### 4. Client presentation

- [x] Correct generated-only cosmetic current drift and physical-unit audio through existing snapshot owners.
- [x] Add reach-aware wet-impact spray, rapids/confluence foam, local debris and per-tick quality/distance limits.
- [x] Test calm lakes, zero flow, missing/dry destinations, falling/native fallback and cleanup.
- [ ] Accept actual appearance/audio, boats, swimmers, two clients and shader/fallback combinations interactively.

### 5. Runtime climate and conservation

- [x] Trace generated reach recognition through existing weather, groundwater, seasons, regional floods, freezing and sediment owners; retain their conservation regression suite.
- [x] Fix missing weather/drinking/habitat wiring through current owners; preserve finite transactions and gate unsupported physical recession.
- [x] Keep independent generation/runtime/presentation controls and document topology-setting transitions.

### 6. Acceptance and delivery

- [x] Run focused RED/GREEN commands serially; then relevant water/render suites, full root tests, disposable GameTests and package checks.
- [x] Independently review, fix correctness issues with regression tests, and prepare the PR 553 update with actual scope and results.
- [ ] Record measured standard/heavy generation cost and separate automated proof from interactive visuals, third-party combinations and multiplayer acceptance.

## Execution ledger

- Starting commit: `de15e7afc2aad9e0f261db2573bbd4734a1df017`.
- User requested full completion and supplied implementation/safety requirements. Continue inline without further design approval requests, following repository instructions over optional skill approval stages.
- Independent read-only audits assigned client presentation and weather/ecology ownership; only the main agent runs Gradle.
- Native acceptance exposed tile-dependent whole-query fine rejection. Replaced it with complete per-source admission before tile clipping, stable bounded upstream widths and a bounded streaming union.
- RED/GREEN regressions cover early native inlets, diagonal uphill refinement, completed chunk wrappers, future falling curtains, pending-neighbor clearance and provisional geometry discarded on later budget exhaustion.
- `completion-source-native-02.log`: all 42 required tests passed, including 122 actual connected columns in four FULL chunks, matching forward/reversed physical snapshots and save/reload with generation disabled. This is one seed's standard noise/plains sample, not arbitrary terrain-mod or whole-route atomicity proof.
- `completion-final-validation.log`: final serial command passed in 2m49s: water 199/199, rendering 315/315, full root 1699 with zero failures/errors and four existing skips, all 43 required GameTests, root build and updated synthetic benchmark. Package verification matched 89 compiled classes and nine bank profiles. Native standard and Amplified prediction costs are documented separately from whole-world generation and interactive acceptance.
- Final independent source review covered per-source planning/admission, profiles/reload/vegetation, habitat, effects and audio. The pending-neighbor clearance finding was corrected and passed its regression; no further actionable finding remained in the reviewed scope.
- PR merge CI then exposed the base's Data Engine package move. Merged `in-dev` at `4533cb7f`, reused the existing OPC cache path and addressed 18 scoped inspection warnings. `base-integration-validation.log` passed in 3m 16s: water 199, rendering 343 with one GPU skip, root 1762 with five skips, 43 GameTests and root build. The review additionally required explicit canonical fixture presence to keep current-ownership regressions strong.
- Final strengthened-fixture verification (`base-fixture-final.log`) passed compilation, all 43 required GameTests and packaging in 1m 20s. The JAR matched 147 inspected compiled classes and included nine bank profiles. Remote CI must be read on the newly pushed integration revision before claiming its checks are green.
- Remaining work proceeds in order: establish a supported whole-route physical terrain certificate; build valley/headwater and larger basin/spring/wetland geometry on it; then accept native heavy terrain, graphics/boats and two-client/mod combinations. Do not enable unfinished behavior or raise default safety gates to claim completion.
