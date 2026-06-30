# Realistic Water Rendering and Simulation

## Goal

The finished water system replaces Minecraft's visible surface and canonical
water-volume behavior. Vanilla fluid blocks are a migration and compatibility
bridge while the replacement gains complete bucket, swimming, redstone, world
save, and third-party-mod integration; they are not the final renderer or
simulation authority.

The replacement uses a hybrid numerical model rather than attempting a full
Navier-Stokes simulation for every ocean cell. That is the practical and
technically appropriate model for a large Minecraft world:

- Gerstner gravity waves model the continuous far-field ocean, river, and pond
  surfaces.
- The existing SPH solver handles local volumes such as pours, droplets,
  splashes, and shore wash.
- Tide and shoreline systems provide the slower boundary conditions that move
  water toward and away from land.

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

The per-frame surface renderer owns exposed water through the active client
view distance. Its cache
suppresses only the exact baked vanilla top faces covered by replacement
patches, preventing the flat depth surface from clipping animated troughs.
Near, medium, and far LODs tile the same world-aligned cells without gaps or
overlaps. Vanilla side faces remain as failure-safe compatibility geometry and
stay laterally anchored so shoreline triangles cannot stretch across land.
Iris/Oculus keeps the standard translucent shader path; without an
external shader pack, the optional core shader adds Fresnel, depth-colored
absorption, foam, lighting, and animated micro-normal detail.

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
  radius, and view-distance-matched medium/far LODs.
- LOD cells are recursively tiled from a shared coarse grid, preventing
  overlapping or uncovered cells where mesh spacing changes.
- Coarse optimized cells validate their complete footprint and edge grid before
  rendering, so they cannot bridge beach corners, islands, or unloaded gaps.
- Surface ownership starts from exposed plain water or tracked canonical water,
  not arbitrary waterlogged blocks. Ice-covered water, lily-pad-covered water,
  and submerged vegetation keep vanilla rendering so the replacement mesh does
  not draw dark floor-level patches in frozen or plant-heavy shorelines.
- Shallow or irregular LOD cells automatically subdivide to one-block patches;
  coarse non-planar quads are reserved for deep open water so their GPU
  triangle split cannot produce large angular shoreline artifacts.
- Only one-block patches suppress baked vanilla top faces. Coarse far-distance
  LOD patches now behave as water-colored overlays so the vanilla/cached water
  surface remains a fallback under them instead of revealing terrain through
  large transparent triangles.
- Depth attenuation that fades deep-ocean waves into shallow water.
- Foam generated from depth and crest slope rather than absolute world height.
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

### Phase 5: persistent local volumes

- Bucket pours are simulated on the logical server and identified by UUID.
- Nearby clients receive quantized, bounded position and velocity snapshots
  every four ticks and interpolate rather than re-running divergent SPH physics.
- Static bodies refresh less often and remote mirrors expire when tracking ends.
- Non-transient bodies persist per dimension in compact `SavedData` arrays and
  restore their identity, position, velocity, and droplet state.
- Dimension unload captures mobile bodies before releasing runtime references;
  unloading one dimension no longer clears simulations in every dimension.
- Pending settlement callbacks retain their owning level and are flushed before
  persistence, preventing unload-time duplication or loss.
- Settled SPH conversion is transactional: if nearby canonical cells cannot
  hold the complete body, partial writes roll back and the body retries later.
- Vanilla performs the bucket interaction first for permissions, inventory,
  sounds, and game events; persistent SPH then owns that exact mobile volume
  until it settles into the chunk-persistent canonical state.
- If the SPH body budget is completely full, the placed bucket stays as visible
  canonical projected water instead of being removed into a ghost allocation.
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
- Buckets become conserved persistent SPH volume, then materialize into exact
  canonical cell volume after settling instead of leaving a duplicate source.
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
