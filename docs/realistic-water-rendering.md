# Realistic Water Rendering and Simulation

## Goal

The water system uses a hybrid model rather than attempting a full
Navier-Stokes simulation for every ocean block. A hybrid is the practical and
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

Vanilla liquid geometry remains in Minecraft's normal translucent terrain
buffer. This is intentional: Sodium/Embeddium and Iris/Oculus can continue to
recognize and shade water instead of receiving an incompatible private draw
pipeline. Without a shader pack, the displaced geometry and normals still
improve silhouette, lighting, and boat response.

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

Vanilla liquid chunks are static meshes, so their CPU-displaced shape changes
only when a section rebuilds. The next renderer should add a bounded,
camera-local dynamic detail surface rather than forcing chunk rebuilds:

- A near field with per-frame wave normals and crest highlights.
- Distance-based tessellation and a strict vertex budget.
- Shore-distance and depth inputs for shoaling, refraction, and wave damping.
- Foam generated from crest steepness and shoreline breaking, not absolute
  world height.
- A standard translucent compatibility path plus an optional built-in core
  shader when no third-party shader pack owns water shading.

### Phase 3: optical water model

- Fresnel reflection based on view angle.
- Beer-Lambert absorption using estimated water depth.
- Normal-driven sun and moon specular highlights.
- Underwater fog color and visibility derived from the same absorption values.
- Caustics limited to shallow, lit water to avoid a full-screen cost.

Shader packs should receive material-friendly water geometry and normals; they
remain responsible for their own reflection, refraction, and screen-space
effects. The built-in path supplies comparable art direction for players who do
not use shaders without pretending it can reproduce a pack's deferred renderer.

## Simulation phases

### Phase 4: shallow-water shoreline coupling

- Cache depth and shore distance at chunk scale.
- Slow and amplify incoming swell as depth decreases.
- Rotate wave direction toward the shore normal (refraction).
- Transfer breaking-wave energy into SPH shore wash and foam events.
- Keep tide edits rate-limited and server-authoritative.

### Phase 5: synchronized weather and sea state

Base boat and entity forces now run from server world time. The next gameplay
step is a compact environment state containing wind direction, sea state, and
tide phase. Clients can reproduce that deterministic spectrum at render
frequency while the server remains authoritative; individual wave samples do
not need network packets.

## Validation targets

- No visible wave phase seam at 16-block section boundaries.
- Surface normals remain unit length and match the displacement derivative.
- A single component repeats after `2 * pi / omega` seconds.
- Thin flowing water attenuates to a flat edge before intersecting terrain.
- Sodium/Embeddium and Iris/Oculus retain the standard terrain-water path.
- Boat bobbing never mutates the client entity position.
- Frame budgets are measured separately for surface rendering, SPH mesh
  rebuilds, ripple geometry, and shader passes.
