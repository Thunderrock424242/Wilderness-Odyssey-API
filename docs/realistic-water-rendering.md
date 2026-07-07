# Realistic Water Rendering and Simulation

## Goal

The finished water system replaces Minecraft's visible surface and canonical
water-volume behavior. Vanilla fluid blocks are a migration and compatibility
bridge while the replacement gains complete bucket, swimming, redstone, world
save, and third-party-mod integration; they are not the final renderer or
simulation authority.

The preferred compatibility model is now tag compatibility, not pretending to
be vanilla water internally. The mod owns namespaced water IDs such as
`wildernessodysseyapi:wilderness_water` and adds them to `#minecraft:water` so
biomes, structures, mob spawning, and other tag-aware checks still classify the
fluid as water. Canonical projection writes the namespaced water block for
owned/disturbed water, and deferred automatic seeding can migrate accepted
plain vanilla world water into the same namespaced block as chunks become
player-visible and during later background ticks. Existing vanilla water
therefore remains a generation/import source, not the long-term ownership
target. Exact
`Blocks.WATER` / `Fluids.WATER` assumptions are handled with targeted mixins
only when a system truly needs them.

Within the mod codebase, gameplay and renderer-ownership checks should prefer
`WaterCompatibility` or `FluidTags.WATER` over vanilla-only fluid identity.
Vanilla water constants are reserved for art/tint defaults, dripstone/cauldron
targets, and migration paths that intentionally detect remaining
`minecraft:water` blocks before converting them to Wilderness water.

The replacement uses a hybrid numerical model rather than attempting a full
Navier-Stokes simulation for every ocean cell. That is the practical and
technically appropriate model for a large Minecraft world:

- Gerstner gravity waves model the continuous far-field ocean, river, and pond
  surfaces.
- The existing SPH solver handles temporary local detail such as falling slices,
  droplets, splashes, leaks, and shore wash.
- Tide and shoreline systems provide the slower boundary conditions that move
  water toward and away from land.
- Canonical finite-volume cells own conserved block-scale water amount and the
  vanilla compatibility projection. Disturbed cells now distribute volume by
  gravity and lateral height difference, carry damped velocity, and hand
  energetic falling slices to SPH instead of faking every pour as static blocks.

All three layers should share water-body classification, time, and surface
samples so boats, particles, foam, and visuals do not drift apart.

## Implemented foundation

`GerstnerWaveProfile` now derives angular frequency from wavelength, gravity,
and effective depth using:

```text
omega^2 = gravity * waveNumber * tanh(waveNumber * depth)
```

Each sample returns vertical and horizontal displacement, an analytic normal,
and orbital velocity. Ocean swell, river ripples, pond motion, vertex geometry,
and boat response therefore use one coherent wave field.

The per-frame surface renderer follows the active client render distance through
bounded near, medium, and far LODs instead of rebuilding every distant ocean
block at full detail. Vanilla water remains the stable compatibility mask, but
validated replacement patches hide the baked vanilla top face beneath them by
default. This makes the Wilderness surface the visible water layer while
vanilla fluid state and tags remain in-world for compatibility. Coarse LOD
patches are therefore optically denser in replacement mode rather than ghost
overlays. Patch budgets keep extreme view distances from overwhelming the
client, and vanilla side faces stay laterally anchored so shoreline triangles
cannot stretch across land.
Iris/Oculus keeps the standard translucent shader path; without an
external shader pack, the optional core shader adds Fresnel, depth-colored
absorption, foam, lighting, and animated micro-normal detail.

On the server, `WildernessFluidRegistry` advances only disturbed canonical
cells. Stable imported worldgen water stays as a reservoir until gameplay
touches it; disturbed water prefers bounded downward transfer, then distributes
sideways across every lower neighbor instead of picking one arbitrary direction.
When a falling cell has enough volume and no canonical water below it, the
solver can transfer a conserved slice into `SPHSimulationManager` as a mobile
local body. That SPH body is ticked under strict budgets, synchronized only
when gameplay-critical, and later settles back into canonical cells with
averaged particle velocity preserved.

## Rendering phases

### Phase 1: physically coherent surface foundation

- Gravity/depth-based dispersion and stable wavelength bands.
- Horizontal Gerstner motion, vertical height, analytic normals, and orbital
  velocity in a shared sample.
- Correct section-local to world-coordinate conversion during liquid mesh
  construction.
- Shallow-edge attenuation to keep waves from clipping through banks.
- Render-only boat bobbing to avoid client/server position drift.
- Server-authoritative orbital and tidal forces for boats, floating items, and
  wading entities.

### Phase 2: per-frame surface detail

Vanilla liquid chunks are static meshes, so live phase is no longer baked into
them. `OceanSurfaceRenderer` provides a bounded camera-local replacement pass with:

- Per-frame world-space positions and analytic wave normals.
- Cached water/bathymetry scans, circular range culling, a block-detail inner
  radius, and bounded view-distance-matched medium/far LODs.
- LOD cells are recursively tiled from a shared coarse grid, preventing
  overlapping or uncovered cells where mesh spacing changes.
- `WaterRenderingConfig` exposes profile-specific patch budgets and distance
  caps so normal clients and Sodium/Embeddium-style renderer stacks can tune
  the same compatibility renderer without changing code.
- Coarse optimized cells validate their complete footprint and edge grid before
  rendering, so they cannot bridge beach corners, islands, or unloaded gaps.
- Surface ownership starts from replacement-safe canonical water or a
  namespaced Wilderness projection, not from pending plain Minecraft water or
  arbitrary waterlogged blocks. Ice-covered water, lily-pad-covered water, and
  submerged vegetation keep vanilla rendering so the replacement mesh does not
  draw dark floor-level patches in frozen or plant-heavy shorelines.
  Waterlogged hosts can still be imported as hosted canonical water for
  depth/optics, but hosted cells are not replacement-safe.
- Shallow or irregular LOD cells automatically subdivide to one-block patches;
  coarse non-planar quads are reserved for deep open water so their GPU
  triangle split cannot produce large angular shoreline artifacts.
- `replaceVanillaWaterTopFaces` is enabled by default so validated replacement
  patches hide the matching baked vanilla top faces. The old
  `suppressVanillaWaterTopFaces` option remains as a legacy/debug override.
  Both switches affect top faces only; vanilla side faces and fluid/tag state
  remain available for compatibility while Wilderness owns the visible surface.
- When vanilla top replacement is active, `surfaceAbsorptionStrength` and
  `surfaceOpacityStrength` enforce minimum effective values so coarse LOD water
  is not a faint transparent sheet over hidden terrain.
- When the per-frame dynamic ocean surface is enabled, baked vanilla liquid
  vertices are no longer vertically waved. The chunk mesh remains stable
  compatibility data while the replacement pass owns visible motion; covered
  water under ice also stays flat.
- Dynamic replacement vertices use Gerstner height and normals but remain
  laterally anchored. Full horizontal orbital displacement belongs to a future
  continuous offshore mesh; applying it to block-clipped shoreline topology can
  pull boundary vertices across missing neighbors and reveal triangular gaps.
- The replacement mesh treats pending vanilla/source water as migration input,
  not as final face geometry. It renders only over stable Wilderness-owned
  footprints with a one-block continuity border; flowing, partial, covered,
  shore-adjacent, and ice-adjacent cells stay visible only after canonical
  volume or a dedicated local-volume/shoreline mesh owns them.
- Vanilla and Wilderness water are visually stitched in `LiquidBlockRenderer`
  by culling internal faces between any two fluids tagged as
  `#minecraft:water`. Without that client bridge, mixed migration boundaries
  render as tall translucent underwater curtains even though both sides are
  logically water.
- `ShorelineSurfaceRenderer` supplies that local edge layer. It draws one-block
  overlay quads for Wilderness-owned exposed shore, ice-adjacent, flowing, and
  partial cells that the open-ocean mesh refuses to own. In strict replacement
  mode it publishes top-face ownership only for replacement-safe shoreline
  cells, so unsafe pending/hosted cells cannot hide vanilla and reveal black
  terrain gaps. It runs after the open-ocean pass, respects a separate
  renderer-profile radius and patch budget, and scans nearest-first.
- `ClientWaterColumnSampler` is the shared client-side volume lens for
  shoreline overlays, open-ocean depth checks, and camera immersion. It samples
  `WildernessWaterAuthority` so renderers only claim canonical or namespaced
  Wilderness projection cells; plain `minecraft:water` is reported as pending
  migration input instead of authoritative replacement water. That lets partial
  finite-volume cells look thinner, makes shallow foam/fog follow actual water
  amount, and scrolls local surface texture/wave phase in the direction of flow.
- Automatic authority migration prioritizes loaded chunks around each player up
  to the requested/server view-distance boundary. The renderers still never
  force-load distant water, but every already-visible loaded chunk is pushed
  toward canonical ownership before older background migration work.
- Canonical ownership import is decoupled from the slower block-conversion
  budget, so authority coverage can fill the visible ocean while namespaced
  Wilderness block rewrites continue safely over later ticks. If conversion
  budget runs out, the chunk is requeued from the first skipped plain-water
  column instead of being marked complete.
- Loaded and watched chunks are promoted into a visible-priority finalization
  queue once players exist. The server tick loop performs the bounded scan and
  rewrite work; chunk load/watch callbacks never rewrite ocean blocks directly.
  That is the handoff where generated plain `minecraft:water` becomes
  namespaced Wilderness water without blocking raw spawn-preparation worldgen
  or terrain streaming.
- Completed chunk handoffs persist a water-finalized marker on the chunk data.
  Render-distance and player-priority migration can therefore focus on newly
  loaded/generated chunks instead of rescanning ocean that already belongs to
  Wilderness authority.
- Exposed plain Minecraft water that is still waiting for migration stays on
  the vanilla compatibility renderer until visible-chunk finalization imports
  and projects it as Wilderness water. This keeps the handoff single-authority:
  a column is either vanilla-pending or Wilderness-owned, never a visual
  half-preview that can disagree with top-face culling.
- Depth attenuation that fades deep-ocean waves into shallow water.
- Foam generated from depth and crest slope rather than absolute world height.
- `surfaceAbsorptionStrength` and `surfaceOpacityStrength` tune how quickly
  deep water hides the blocky seafloor and how strongly the replacement water
  medium blends over terrain. The defaults favor a less see-through ocean while
  keeping shoreline overlays partially readable.
- Patch-stable material tint avoids exposing the GPU's internal triangle split;
  view-dependent Fresnel remains a per-pixel shader responsibility.
- The built-in dynamic-ocean pass writes color only, not depth, and keeps a
  blue optical floor so transparent quads do not reveal dark terrain as hard
  triangular shards around ice shelves or shallow ocean floors.
- A standard translucent compatibility path plus an optional built-in core
  shader when no third-party shader pack owns water shading.

### Phase 3: optical water model

- Fresnel reflection based on view angle.
- Beer-Lambert absorption using estimated water depth.
- Normal-driven sun and moon specular highlights.
- The built-in surface shader treats the vanilla atlas as only a subtle material
  hint. Procedural wind-aligned micro-ripples, denser depth color, brighter
  glancing Fresnel, and sharper celestial highlights carry the default look so
  Wilderness water reads as a smooth cinematic surface instead of re-skinned
  vanilla water.
- CPU optical color remains active in the shader-pack compatibility path.
- The built-in shader adds angle-dependent Fresnel, animated shimmer, lightmap
  response, and fog without becoming mandatory for startup.

Shader packs should receive material-friendly water geometry and normals; they
remain responsible for their own reflection, refraction, and screen-space
effects. The built-in path supplies comparable art direction for players who do
not use shaders without pretending it can reproduce a pack's deferred renderer.

## Simulation phases

### Phase 4: shallow-water shoreline coupling

`ShallowWaterGrid` solves depth-averaged surface elevation and X/Z velocity on
CFL-limited 32-block regions. `ShorelineWaterManager` samples bathymetry around
players, couples open boundaries to tide plus ocean swell, and supplies flow to
boats, items, wading entities, and breaking-wave SPH pulses. The old four-block
tide edit grid was removed; tides no longer place or delete water blocks.

### Phase 5: local volumetric detail and optional SPH

- Bucket placement becomes canonical Wilderness volume immediately. SPH can add
  a visual splash, but it does not own the bucket's durable water.
- Local cells are sparse, store compact fill/velocity/provenance, and sleep
  when they cannot move. The finite-volume ticker processes only the configured
  `localFlowCellsPerTick` budget.
- Server-owned SPH is reserved for tiny gameplay-critical cases such as falling
  canonical slices, small leaks, and short waterfalls. It is capped by active
  body count, particles per body, and particle tick budget.
- Client SPH is preferred for splashes, shore wash, bucket impact visuals, and
  storm/anomaly spray. The server sends compact events, not individual
  particles, and `localWaterNetworkEventsPerTick` limits how many local water
  events one dimension can emit per tick.
- The top-level client `waterQuality` setting clamps waves, ripples, SPH,
  render distance, patch counts, and mesh rebuild cadence. `LOW` and `MEDIUM`
  disable SPH; `HIGH` and `CINEMATIC` allow bounded local SPH.
- Historical non-transient SPH save data remains loadable for compatibility,
  but the target architecture is temporary local SPH that settles conserved
  volume back into canonical cells rather than permanent SPH world storage.
- Settled SPH conversion is transactional: if nearby canonical cells cannot
  hold the complete body, partial writes roll back and the body retries later.
- Vanilla fluid levels remain a deliberately lossy compatibility projection
  for swimming, waterlogging boundaries, redstone, and unintegrated mods.

### Phase 6: synchronized weather and sea state

Implemented:

- Server rain, thunder, world time, and dimension identity produce a bounded
  wind direction, wind speed, swell energy, short-wave chop, and breaking-wave
  strength through `OceanSeaState`.
- A compact snapshot is sent once per second and interpolated client-side;
  individual Gerstner components do not require network packets.
- The same wind-aligned spectrum drives the live ocean mesh, optical shader,
  boat/item/player forces, shallow-water boundaries, and shoreline SPH.
- Calm weather favors lower long swell. Rain and thunderstorms progressively
  add directional short chop, shimmer, foam, stronger shore wash, and currents.
- Vanilla water blocks and `#minecraft:water` remain the compatibility surface
  for other mods; sea state changes our simulation and rendering, not fluid IDs.

### Phase 7: canonical replacement state

Implemented foundation:

- Water volume, velocity, provenance, temperature, and revision are stored in
  sparse chunk-persistent cells using 4,096 units per full block.
- Buckets become exact canonical cell volume immediately. Flat-ground bucket
  water sleeps as a stable local reservoir; ledges, drains, leaks, and nearby
  active water wake it into finite-volume flow.
- Disturbed cells use a bounded finite-volume queue; vanilla ticks are
  suppressed only for tracked canonical cells so a second solver cannot mutate
  owned state while untracked mod or vanilla water keeps normal behavior.
- Chunk snapshots synchronize exact fractional fill to nearby clients, and the
  surface renderer, shoreline sampling, and entity forces read canonical data.
- Large sparse chunks persist every cell and synchronize in bounded pages;
  packets arriving before their client chunk are reassembled and retained.
- Bucket pickup drains canonical volume only after vanilla confirms success,
  so cancelled or incompatible pickup attempts cannot erase water.
- Vanilla fluid levels are a bounded compatibility projection and lazy import
  boundary for old chunks, waterlogged blocks, and unintegrated mods.

### Phase 8: underwater optics and immersion

Implemented foundation:

- `ClientWaterImmersion` resolves the camera against canonical fractional fill
  and the same animated Gerstner/tide surface used by visible ocean geometry.
  Fog and overlays therefore cross a visible crest or trough instead of the
  hidden flat height of the vanilla compatibility block.
- `UnderwaterOpticsModel` derives bounded fog color, optical visibility,
  clarity, caustic strength, and distortion from biome water tint, camera
  depth, bathymetry, daylight, canonical velocity, and synchronized sea state.
- Red wavelengths attenuate faster than green and blue, while storms, moving
  water, and shallow sediment reduce clarity without changing water identity.
- Synchronized mobile SPH bodies also participate in camera immersion instead
  of behaving like visible particles with no optical volume.
- The optional built-in overlay adds animated optical distortion, caustic
  interference, and soft light shafts. It falls back to the vanilla overlay
  when unavailable and leaves Iris/Oculus in control of its shader-pack path.
- Vanilla `minecraft:water`, fluid tags, swimming, waterlogging, and mod fluid
  queries remain intact; Phase 8 replaces camera presentation, not the public
  compatibility contract.

The built-in overlay is intentionally bounded and does not copy the scene
framebuffer. Full scene refraction remains the responsibility of deferred
shader packs, avoiding an incompatible second post-processing pipeline.

## Validation targets

- No visible wave phase seam at 16-block section boundaries.
- Surface normals remain unit length and match the displacement derivative.
- A single component repeats after `2 * pi / omega` seconds.
- Thin flowing water attenuates to a flat edge before intersecting terrain.
- Sodium/Embeddium and Iris/Oculus retain the standard terrain-water path.
- Boat bobbing never mutates the client entity position.
- Bucket water remains after its transient motion settles and after save/reload.
- Removing/placing tide water never produces a four-block checkerboard because
  tide simulation does not mutate world blocks.
- Multiplayer clients observe the server particle body rather than a separately
  randomized local simulation.
- Canonical snapshots larger than 16,384 cells round-trip without truncation.
- Underwater fog and overlays transition at the rendered wave surface rather
  than the flat vanilla compatibility plane.
- Frame budgets are measured separately for surface rendering, SPH mesh
  rebuilds, ripple geometry, and shader passes.
- Player-centered automatic migration drains visible loaded chunks without
  stalling world creation or forcing unloaded chunks.

## Ship-track phases

These phases are the practical path to a couple-week testable release:

1. **Ownership convergence:** plain loaded water near players is automatically
   imported, prioritized, and migrated to Wilderness water under tick budgets.
   Waterlogged hosts import as hosted canonical cells instead of being
   destroyed.
2. **Renderer stitching:** vanilla and Wilderness water are visually continuous
   while migration is in progress; the open-ocean mesh owns only
   replacement-safe exposed water.
3. **Compatibility hardening:** mod-owned checks use `WaterCompatibility` or
   tags, while targeted mixins cover unavoidable vanilla-only water checks.
   `/wowater shipcheck` is the local readiness command for separating normal
   migration work from actual projection gaps.
4. **Performance pass:** tune migration budgets, render-distance LODs, SPH
   caps, and shoreline patch budgets against real FPS/frametime captures.
5. **Release validation:** run new-world, old-world, frozen-ocean,
   vegetation-heavy, bucket, boat, mob-spawn, shader/no-shader, and
   multiplayer smoke tests before packaging.
