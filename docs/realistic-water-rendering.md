# Wilderness Water Generation and Rendering

## Architecture

Wilderness water is generated as a native world resource. It is not imported,
scanned, or converted after a chunk finishes generation. The complete data flow
is:

```text
noise, aquifers, carvers, flat layers, and features
    -> generation-only state mapping
    -> Wilderness source or flowing fluid in ProtoChunk
    -> compact GeneratedWaterChunk spans
    -> LevelChunk attachment persistence and synchronization
    -> WildernessWaterAuthority
    -> immutable ClientWaterChunkSnapshot
    -> WaterRenderCoordinator
    -> cached surfaces, optical shader, ripples, and local SPH
```

Existing chunks are deliberately outside this pipeline. Standalone vanilla
water already stored in an old world remains non-authoritative until a future
compatibility phase defines an explicit upgrade policy.

## Generation boundary

`GenerationWaterStateMapper` maps only standalone `minecraft:water` writes
made by generation:

- source water becomes the Wilderness source fluid block;
- flowing water preserves its amount and falling state;
- waterlogged host blocks are unchanged;
- lava and unrelated fluids are unchanged; and
- an already mapped Wilderness state is returned unchanged.

`ProtoChunkWaterMixin` covers the normal chunk write path used by carvers,
flat-world layers, lakes, and configured/placed features.
`NoiseBasedChunkGeneratorWaterMixin` wraps the direct section write used by
noise terrain and aquifers. `SpringFeatureWaterMixin` maps the configured
fluid before both placement and tick scheduling.

The mapper is generation-only because these hooks exist on generation types
and methods rather than the runtime `LevelChunk` placement path. It is a pure
state conversion and never calls `setBlockState` itself. The wrapped write is
invoked exactly once, then metadata records the state actually returned by the
chunk write. These constraints prevent recursive conversion and duplicate
metadata entries while preserving Minecraft's terrain and aquifer decisions.

## Compact generated metadata

Every generated chunk carries a persistent, synchronized
`GeneratedWaterChunk` attachment. Each X/Z column stores compact vertical spans
instead of one object per ocean block. A span records its bottom and top,
source/flow fill, falling state, water-body classification, biome water tint,
and ocean/river/lake blend weights. The attachment also derives column
surfaces, floors, generated-cover bits for ice/feature roofs, and cross-chunk
wet-edge masks.

Adjacent compatible writes merge into one span. Overwrites split or remove only
the affected range. Duplicate writes are no-ops, and the data is compacted
before serialization. The format has an explicit version so future revisions
can upgrade old metadata without scanning blocks in completed chunks.

NeoForge attachment serialization transfers the same compact data from a
`ProtoChunk` into its promoted `LevelChunk`, saves it with the chunk, and sends
it to tracking clients. Large undisturbed oceans therefore remain metadata and
physical Wilderness fluid; they do not become millions of ticking sparse
cells.

## Authority and runtime disturbance

`WildernessWaterAuthority` is the only public ownership query. Its precedence
is:

1. a sparse runtime cell, including an explicit dry tombstone;
2. a generated span whose expected amount matches physical Wilderness fluid;
3. a provisional or orphaned Wilderness-fluid projection;
4. vanilla or externally tagged water, reported as non-authoritative; and
5. dry space.

`WaterVolumeChunk` remains the sparse simulation layer. Interacting with
generated water materializes only the affected cell and a bounded loaded
neighborhood. Stable generated water is never enrolled in a full-ocean tick
loop. A dry tombstone intentionally masks the generated baseline after a cell
is drained, including across save/reload and synchronization.

Solid displacement is conservative. Neighboring loaded capacity receives the
water first; any enclosed remainder persists at the occupied position with a
displacement-reservoir flag. Reservoirs retain their diagnostic volume and
velocity but are non-wet, non-rendered, and non-extractable until terrain
exposes and wakes them.

## Immutable client snapshots

`ClientWaterSnapshotStore` publishes one immutable
`ClientWaterChunkSnapshot` per loaded client chunk. The snapshot combines the
synchronized generated spans with the current sparse overrides and resolves
the primitive column fields rendering needs: surface, floor, fill, body type,
blend weights, and wet boundaries.

Concurrent-map value replacement makes publication atomic for the render
thread. Sparse payloads replace only their chunk snapshot, and dirty mesh work
is queued for that chunk and its border neighbors. Relevant physical client
block changes at a synchronized surface or immediately above it queue the same
bounded rebuild. Chunk unload removes the snapshot immediately. A missing
snapshot is always dry, so neither rendering nor camera immersion can project
water across an unloaded frontier.

The per-frame surface pass does not scan block states, heightmaps, or mutable
authority data. A queued mesh rebuild performs one cached exposure check per
nearby snapshot column, matching generated metadata to physical tagged water
and an unobstructed top before emitting geometry. Camera immersion performs one
physical fluid sample so an excavated air shaft cannot inherit underwater
optics from stale column metadata. Snapshot memory remains bounded by compact
generated spans plus the small disturbed sparse set.

## WaterRenderCoordinator

`WaterRenderCoordinator` is the sole owner of Wilderness translucent water
geometry. It runs after translucent terrain and coordinates:

- generated ocean, river, lake, spring, and shoreline surfaces;
- fallback custom-fluid tops during mesh preparation;
- ripples and local SPH detail;
- frustum culling and far-to-near chunk-group ordering; and
- the shared inputs used by the underwater transition.

`WaterChunkMeshCache` builds stable, world-aligned chunk groups only when a
snapshot changes. Corners average the neighboring snapshot columns and their
body blend weights, so both sides of a loaded chunk boundary select identical
surface topology. High and Cinematic groups subdivide each block top into a
stable half-block grid so GPU-displaced silhouettes remain smooth without a
per-frame CPU rebuild. Continuous Gerstner and tide displacement happens in
the vertex shader, including horizontal crest motion and analytic tangents,
with displacement tapered at dry or unloaded boundaries.
The four columns touching a shoreline vertex now form an exact world-space
anchor: if any one is dry, covered, or missing, the shader applies no Gerstner
motion, horizontal chop, wake impulse, or tide offset at that edge. The first
interior row eases to half strength and the second returns to full motion. The
same calculation is used by camera immersion, and diagonal snapshot arrivals
invalidate the halo on both neighboring meshes.
Rebuild work uses a deduplicated chunk-key queue and a bounded streaming burst,
preventing repeated neighbor entries from leaving a distant flat fallback ring.

Sparse snapshot cells retain horizontal canonical velocity. The mesh packs
bounded current, depth, and shoreline proximity into the low bits of the
existing block-vertex color channels, preserving the stock vertex format and
fallback shader. The custom vertex shader decodes those values before
interpolation. A capped set of the eight nearest tick-aged impacts and wakes is
uploaded as uniforms, deforming heights and normals without rebuilding the
cached mesh.

Fallback fluid tops remain visible while the first replacement group is absent
or rebuilding. Once the custom vertex buffer is uploaded, the coordinator
publishes a generation-tagged suppression intent before requesting the affected
terrain sections to rebuild, but keeps that custom group hidden. Vanilla,
Sodium, and legacy Embeddium section compilers attach a receipt only when the
exact build observed every expected owned top; the receipt is acknowledged only
after that build reaches renderer-owned GPU storage. The custom group becomes
visible after every affected section acknowledges the same generation. Stale,
cancelled, partial, or pre-intent builds keep the baked fallback instead of
opening a transient hole. The baked-fluid mixin still suppresses only a top
whose uploaded custom mesh and matching wet snapshot own that column. Internal
fluid faces, unloaded chunks, and unsupported buried surfaces keep the standard
translucent fluid path.

When an Iris or Oculus shader pack is active, the external shader path owns Wilderness
water through the ordinary tagged custom-fluid geometry. A narrow optional
material bridge aliases unmapped Wilderness fluid states to the numeric material
ID that the active pack assigned to vanilla water, so Complementary-style packs
apply their own water waves and optics without modifying the shader-pack archive.
Explicit pack mappings remain authoritative. In that mode the
coordinator neither draws the snapshot mesh through the stock translucent
shader nor suppresses fallback tops: without the built-in vertex program that
mesh would be flat and would hide the geometry the shader pack expects. A mode
switch clears custom ownership, refreshes terrain sections, and requeues all
loaded snapshots if control later returns to the built-in renderer. The active
pack state is queried through the modern or legacy Iris API; installing the
renderer without enabling a pack keeps the built-in GPU-wave path available.

The coordinator flushes ripples and local SPH once as one translucent detail
batch. Legacy ocean and shoreline event handlers no longer independently draw
or flush overlapping surfaces, preventing duplicate claims, z-fighting, and
ownership feedback loops.

## Optical surface shader

`WaterSceneCapture` makes one reusable scene-color and scene-depth copy per
logical frame. It uses a separate framebuffer to avoid sampling the target
being written and restores both framebuffer bindings after the blit.

The core water shader uses those textures for:

- depth-aware screen-space refraction with wave-normal distortion;
- captured-depth rejection when solid terrain is in front of a displaced wave;
- clamped screen sampling and silhouette/discontinuity rejection;
- approximate water thickness reconstructed from scene and surface depth;
- Beer-Lambert transmission and body-weighted absorption;
- Schlick Fresnel transmission/reflection balance;
- environment, sky, weather, celestial, and optional bounded SSR reflection;
- biome/body tint, daylight, rain, thunder, sea state, tide, and Gerstner input;
- canonical-current advection, depth/boundary shore breaking, and wake foam; and
- unloaded-frontier fading into fog/environment reflection.

The terrain-atlas UV is passed through unchanged. It is sampled only for RGB in
the no-scene-capture fallback; atlas alpha never decides whether custom water
geometry exists. Wave and current animation stay in world-space displacement
and procedural normals, so motion cannot wander into transparent sprite padding
and cut a repeating block grid out of the surface.

Invalid depth and unavailable scene textures fail to the environment path
instead of producing halos or framebuffer feedback. Shallow face-on water
retains transmission, deep paths absorb progressively, and grazing angles
favor reflection without forcing uniform opacity. Foreground rejection and
refraction share a view-space tolerance, preventing cave walls from leaking the
surface while avoiding distance-dependent shimmer against nearly coincident
fallback water.

The source and flowing Wilderness fluids are registered as translucent. This
keeps a safe fallback visible before a custom chunk mesh is ready and preserves
buried or otherwise unclaimed fluid faces.

## Quality tiers

All tiers retain snapshot culling, cached geometry, and the environment
reflection fallback.

`autoDetectWaterQuality` defaults to enabled. Once the client OpenGL context is
available, a one-time render-thread probe selects the effective tier from the
GPU family and reported VRAM, logical CPU capacity, installed physical RAM,
Minecraft maximum heap, and framebuffer resolution. The weakest component
caps the result: unfamiliar GPUs are treated conservatively, software renderers
select `LOW`, common Intel/AMD integrated graphics do not exceed `MEDIUM`, and
ultrawide/4K framebuffers cannot select `CINEMATIC` automatically. Detection
changes only the effective client presentation tier and never rewrites the
user's config.
Disable `autoDetectWaterQuality` to restore the manual `waterQuality` value.

- `LOW` uses reduced refraction, no SSR, and no ambient particle layer.
- `MEDIUM` adds stronger optical detail, one capped ambient particle sample,
  and reduced wake foam while keeping SSR disabled.
- `HIGH` enables a bounded SSR march, up to two ambient particles per emission,
  and a longer wake-foam tail.
- `CINEMATIC` raises the bounded optical/rebuild budgets and permits up to three
  ambient particles per emission. Renderer-mod optimization reduces that to two.

Dirty snapshot meshes are drained under both a quality-scaled count limit and
a soft render-thread time limit. Backlogs do not unlock a larger one-frame
burst; the existing custom mesh or vanilla fallback remains visible until its
replacement is ready. Local SPH density/marching-cubes extraction has a separate
rotating budget, so continuously changing nearby splashes cannot make every
visible body rebuild in the same frame or starve later bodies.

SSR is bounded by shader step count, travel distance, valid depth, and screen
limits. It is an enhancement, not a requirement for water visibility.

## Waves and underwater agreement

The mesh shader and `ClientWaterImmersion` consume the same synchronized sea
state, tide, body blend weights, time base, and world-aligned Gerstner surface
equation. Shared world coordinates make wave phase continuous at chunk borders.
The GPU performs full horizontal/vertical Gerstner displacement and derives
analytic normals from the same component arrays mirrored by
`GerstnerWaveProfile.sampleAt`. Horizontal displacement tapers into shallow/dry
boundaries to prevent bank cracks. Client immersion also samples the same
transient wake/impact height when the built-in shader owns the surface.

Shore foam is intentionally a deterministic visual approximation derived from
immutable snapshot depth and wet boundaries. The server's bounded shallow-water
grid remains authoritative for gameplay flow but is not synchronized to the
renderer; the shader does not claim otherwise.

`UnderwaterOpticsModel` remains the optical foundation for fog, color,
visibility, caustics, and distortion. Camera immersion adds entry/exit
hysteresis and near-plane tolerance around the displaced surface so repeated
surface crossings do not rapidly flicker. Local SPH volumes participate where
their synchronized bounds contain the camera.

Snapshot biome water tint now feeds the same underwater optical model. The
camera path uses the surface renderer's 72 percent body profile and 28 percent
biome tint blend, so oceans, rivers, and lakes keep distinct optical character
without losing local biome coloration.

Vanilla underwater audio remains the sole ambience owner. The entity-water
parity hook feeds Wilderness eye submersion into `LocalPlayer.isUnderWater()`,
which already drives Minecraft's entry/exit sounds, fading loop, and rare
underwater additions. No second loop is layered on top. A separate client-only
particle pass restores the suspended motes that vanilla `WaterFluid.animateTick`
normally supplies, plus high-current bubbles and breaking/current-driven surface
spray. Every candidate is checked against an immutable water snapshot and the
pass runs every two ticks under a quality and renderer-aware hard budget.

Impact rings and boat wakes now carry two lifetimes in the same capped eight
GPU impulse slots. Geometry settles quickly, while a slower foam envelope keeps
the disturbed ring visible for several seconds before a smooth release. This
adds persistent-looking wake and impact foam without a new framebuffer, an
unbounded trail buffer, canonical-water mutations, or extra network traffic.

## Clock tide information

Tide information is attached to Minecraft's vanilla clock instead of occupying
an independent always-available HUD. Hovering a clock appends the live tide,
trend, and moon phase to its normal tooltip. Holding a clock in either hand, or
looking directly at one mounted in an item frame, shows one compact line above
the hotbar; unrelated views never show tide UI. The clock item, model animation,
recipes, and vanilla timekeeping behavior remain unchanged.

The client options `showClockTideTooltip` and
`showContextualClockTideDisplay` can disable either presentation independently.
Both default to enabled.

## Diagnostics

Open the F3 debug screen and read the `WO Water` lines in the system panel:

- visible and frustum-culled mesh groups;
- submitted vertices and triangles;
- cumulative mesh rebuilds;
- client snapshot and generated-metadata memory estimates;
- total water-render CPU submission time;
- scene-copy CPU submission time and copy count; and
- bounded SSR timing when available.
- the automatic/manual quality source and GPU/CPU/memory/display limiter tiers.

The counters avoid synchronous GPU reads. Scene-copy and render values are CPU
submission costs. A ring of asynchronous timer queries reports the most recent
completed SSR-enabled optical surface pass without stalling the render thread.

## Validation

Automated validation should cover generated oceans, rivers, lakes, aquifers,
carvers, springs, and flat water; span persistence; chunk-border masks; sparse
override precedence; snapshot replacement/unload behavior; and non-recursive
generation mapping.

Client smoke testing should include shallow and deep water, downward and grazing
views, dawn through night, rain and thunder, frozen shores, rapid travel and
teleports, F3+A, high altitude, different FOV values, repeated surface
crossings, unloaded frontiers, and every quality tier. Watch the F3 water lines
for rebuild storms, unexpectedly large snapshot memory, or unbounded render
cost while testing.

## Compatibility boundary

Natural aquatic decoration (kelp, seagrass, sea pickles, and coral) and vanilla
water-animal spawn predicates recognize physically present Wilderness water.
This is intentionally narrow: it supports the flora and fauna that belong in a
newly generated water body without making externally tagged water authoritative.

Boats, items, and living entities consume multi-point authority samples for
bounded server-side buoyancy, fluid-relative drag, and current response. Boat
tilt and wake deformation remain client visual work, so they cannot override
server movement.

The standalone Wilderness liquid block exposes a transactional NeoForge fluid
capability. Create receives a focused local water predicate, and guarded
projected-block writes reconcile open-machine placement/removal with canonical
volume. These adapters preserve the namespaced fluid and normal water tags; the
mod does not globally impersonate vanilla `Fluids.WATER`.

Vanilla structures can opt in with the explicit
`wildernessodysseyapi:water` DATA marker, and operators can upgrade a bounded,
fully loaded area of an existing world with `/wowater convert [radius]`.
Waterlogged hosts remain vanilla-owned, and direct chunk-section mutations by
other mods can still bypass the supported block/capability boundaries. Further
integrations must adapt to the authority API rather than reintroducing automatic
completed-chunk scanning or a global vanilla-fluid identity lie.
