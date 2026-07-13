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

## Immutable client snapshots

`ClientWaterSnapshotStore` publishes one immutable
`ClientWaterChunkSnapshot` per loaded client chunk. The snapshot combines the
synchronized generated spans with the current sparse overrides and resolves
the primitive column fields rendering needs: surface, floor, fill, body type,
blend weights, and wet boundaries.

Concurrent-map value replacement makes publication atomic for the render
thread. Sparse payloads replace only their chunk snapshot, and dirty mesh work
is queued for that chunk and its border neighbors. Chunk unload removes the
snapshot immediately. A missing snapshot is always dry, so neither rendering
nor camera immersion can project water across an unloaded frontier.

The render path does not repeatedly query block states, heightmaps, or mutable
authority data. Snapshot memory is bounded by compact generated spans plus the
small disturbed sparse set.

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
the vertex shader, with displacement tapered at dry or unloaded boundaries.
Rebuild work uses a deduplicated chunk-key queue and a bounded streaming burst,
preventing repeated neighbor entries from leaving a distant flat fallback ring.

Fallback fluid tops remain visible while a group is absent or rebuilding. Once
the custom vertex buffer is uploaded, the coordinator requests the affected
terrain sections to rebuild and publishes custom ownership. The baked-fluid
mixin suppresses only a top that has both custom ownership and a matching wet
snapshot column. Internal fluid faces, unloaded chunks, and unsupported buried
surfaces keep the standard translucent fluid path.

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
- clamped screen sampling and silhouette/discontinuity rejection;
- approximate water thickness reconstructed from scene and surface depth;
- Beer-Lambert transmission and body-weighted absorption;
- Schlick Fresnel transmission/reflection balance;
- environment, sky, weather, celestial, and optional bounded SSR reflection;
- biome/body tint, daylight, rain, thunder, sea state, tide, and Gerstner input;
  and
- unloaded-frontier fading into fog/environment reflection.

Invalid depth and unavailable scene textures fail to the environment path
instead of producing halos or framebuffer feedback. Shallow face-on water
retains transmission, deep paths absorb progressively, and grazing angles
favor reflection without forcing uniform opacity.

The source and flowing Wilderness fluids are registered as translucent. This
keeps a safe fallback visible before a custom chunk mesh is ready and preserves
buried or otherwise unclaimed fluid faces.

## Quality tiers

All tiers retain snapshot culling, cached geometry, and the environment
reflection fallback.

- `LOW` uses reduced refraction and no SSR.
- `MEDIUM` adds stronger optical detail while keeping SSR disabled.
- `HIGH` enables a bounded 10-step SSR march and more local detail.
- `CINEMATIC` raises the rebuild budget and permits an 18-step SSR march.

SSR is bounded by shader step count, travel distance, valid depth, and screen
limits. It is an enhancement, not a requirement for water visibility.

## Waves and underwater agreement

The mesh shader and `ClientWaterImmersion` consume the same synchronized sea
state, tide, body blend weights, time base, and world-aligned Gerstner surface
equation. Shared world coordinates make wave phase continuous at chunk borders.
Height-only shoreline safety prevents horizontal displacement from opening
bank cracks.

`UnderwaterOpticsModel` remains the optical foundation for fog, color,
visibility, caustics, and distortion. Camera immersion adds entry/exit
hysteresis and near-plane tolerance around the displaced surface so repeated
surface crossings do not rapidly flicker. Local SPH volumes participate where
their synchronized bounds contain the camera.

## Diagnostics

Open the F3 debug screen and read the `WO Water` lines in the system panel:

- visible and frustum-culled mesh groups;
- submitted vertices and triangles;
- cumulative mesh rebuilds;
- client snapshot and generated-metadata memory estimates;
- total water-render CPU submission time;
- scene-copy CPU submission time and copy count; and
- bounded SSR timing when available.

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

Buckets, boats, unrelated mob mechanics, vanilla structures, waterlogging
replacement, external fluid APIs, other mods, and existing-world conversion are
not part of this generation/rendering architecture. Later compatibility work
must adapt to the authority boundary rather than reintroducing completed-chunk
scanning or migration into the core system.
