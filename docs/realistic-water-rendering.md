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

The per-frame surface renderer owns exposed water near the camera. Vanilla
liquid geometry currently remains behind it as a distant and failure-safe
compatibility surface until replacement coverage reaches the full rendered
world. Iris/Oculus keeps the standard translucent shader path; without an
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
- Cached water/bathymetry scans, circular range culling, and renderer-mod-aware
  cell size/radius limits.
- Depth attenuation that fades deep-ocean waves into shallow water.
- Foam generated from depth and crest slope rather than absolute world height.
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
- Nearby clients receive quantized, bounded particle snapshots every four
  ticks and interpolate rather than re-running divergent SPH physics.
- Static bodies refresh less often and remote mirrors expire when tracking ends.
- Non-transient bodies persist per dimension in compact `SavedData` arrays and
  restore their identity, position, velocity, and droplet state.
- The vanilla source block remains a temporary gameplay/interoperability bridge
  while SPH owns the volumetric visual body.
- A chunk-persistent custom volume capability is required before buckets,
  swimming, redstone, and other mods can treat the replacement as canonical.

### Phase 6: synchronized weather and sea state

Base boat and entity forces run from server world time. Wind direction and a
weather-driven sea-state spectrum remain follow-up work; the deterministic
gravity-wave spectrum itself does not need per-wave network packets.

### Phase 7: canonical replacement state

- Store water volume, surface height, and velocity in chunk-persistent cells.
- Make buckets, displacement, swimming, entities, and shoreline exchange read
  and mutate that state on the logical server.
- Mesh the shared volume state on clients instead of treating vanilla liquid
  tops as the primary geometry.
- Keep a bounded vanilla-fluid adapter only for world migration and mods that
  have not integrated with the replacement API.

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
- Frame budgets are measured separately for surface rendering, SPH mesh
  rebuilds, ripple geometry, and shader passes.
