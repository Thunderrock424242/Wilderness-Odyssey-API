# Wilderness Water System

Wilderness water is generated as a first-class world fluid. Newly generated
terrain stores the namespaced Wilderness source or flowing fluid immediately;
the runtime does not scan completed chunks, import vanilla oceans, or queue
post-generation conversion work.

The authoritative data flow is:

```text
noise, aquifers, carvers, flat layers, springs, and placed features
  -> GenerationWaterStateMapper
  -> Wilderness fluid in ProtoChunk
  -> compact GeneratedWaterChunk attachment
  -> LevelChunk promotion and attachment synchronization
  -> WildernessWaterAuthority
  -> immutable ClientWaterChunkSnapshot
  -> WaterRenderCoordinator
```

## Generation writes

`GenerationWaterStateMapper` maps only standalone `minecraft:water` states at
world-generation write boundaries. Source water becomes the Wilderness source
fluid; flowing amount and falling state are preserved. Waterlogged host blocks,
lava, runtime placements, and unrelated fluids are not mapped.

Three narrow mixins cover the generation paths without replacing Minecraft's
terrain or aquifer algorithms:

- `NoiseBasedChunkGeneratorWaterMixin` wraps the direct section write used by
  noise terrain and aquifers.
- `ProtoChunkWaterMixin` maps `ProtoChunk#setBlockState`, which covers carvers,
  flat layers, lakes, configured/placed features, and other generation writes.
- `SpringFeatureWaterMixin` maps the configured spring fluid for placement and
  its scheduled fluid tick.

Each wrapped write calls the original operation exactly once and records the
state that was actually stored. Mapping an already-namespaced state is a no-op,
so nested generation paths cannot recursively convert or duplicate metadata.

## Compact generated metadata

`GeneratedWaterChunk` is a persistent, synchronized chunk attachment. It stores
versioned vertical spans per X/Z column rather than one object per ocean block.
Each span records its Y range, fill amount, falling state, body classification,
and ocean/river/lake blend weights. The attachment also tracks surface/floor
bounds and wet edge masks for cross-chunk topology.

Metadata is written alongside generation writes and compacted before normal
chunk use. NeoForge attachment serialization carries the same data from the
`ProtoChunk` into the promoted `LevelChunk`, through saves, and to watching
clients. There is no finished-chunk rescan.

## Runtime authority

All gameplay-facing queries use `WildernessWaterAuthority`. Ownership follows
this precedence:

1. Sparse runtime state from `WaterVolumeChunk`, including dry overrides.
2. A generated span whose physical block contains the matching Wilderness fluid.
3. An orphaned or provisional namespaced Wilderness projection.
4. Vanilla or externally tagged water, reported as non-authoritative context.
5. Dry.

`GeneratedWaterChunk` is the compact baseline for untouched oceans, rivers,
lakes, aquifers, and springs. `WaterVolumeChunk` remains sparse and stores only
locally disturbed or simulated cells. Disturbing generated water materializes a
bounded neighborhood; an entire generated body is never expanded or ticked.

Solid placement first moves canonical volume into loaded neighboring capacity.
If a completely enclosed placement has nowhere to send all of its water, the
remainder is persisted as a non-rendered displacement reservoir behind the
solid. Breaking or otherwise exposing that cell wakes the reservoir so the
same units can re-enter normal flow. This prevents sealed placements from
silently deleting water without presenting hidden volume as swimmable or
machine-extractable fluid.

Previously generated chunks that still contain vanilla water are intentionally
left unchanged. The runtime does not reinterpret or scan old vanilla oceans.

## Client snapshots

`ClientWaterSnapshotStore` builds immutable `ClientWaterChunkSnapshot` values
from the synchronized generated attachment and sparse runtime payloads. Each
chunk entry is replaced atomically. Sparse changes rebuild only the affected
chunk snapshot and invalidate only neighboring mesh boundaries that can share
topology.

Rendering and camera immersion read snapshots, not mutable server authority,
heightmaps, or repeated block-column scans. An absent or unloaded snapshot is
always dry, and unload removes the snapshot before it can contribute geometry.

## Coordinated rendering

`WaterRenderCoordinator` is the sole owner of custom translucent water passes.
It coordinates cached chunk meshes, ripples, local SPH effects, fallback fluid
tops, and underwater transition data. `WaterChunkMeshCache` creates stable,
world-aligned topology from snapshots and rebuilds only dirty chunks. Shared
corner blend weights keep connected ocean, river, lake, and shoreline vertices
consistent at chunk borders.

The coordinator performs section/chunk frustum culling, stable translucent
ordering, batched submission, and an atomic fallback-to-custom ownership
handoff. It never draws into missing snapshot chunks; loaded frontiers fade into
fog/environment reflection instead of exposing unloaded terrain.

Both Wilderness source and flowing fluids use Minecraft's translucent render
type. Baked fluid tops remain the fallback until a replacement chunk mesh is
uploaded and owned by the coordinator.

## Optical pipeline

`WaterSceneCapture` copies scene color and depth once for the coordinated water
stage. The Gerstner water shader uses those shared textures for depth-aware
refraction, thickness reconstruction, Beer-Lambert absorption/transmission,
Fresnel reflection, environment lighting, weather response, and bounded
screen-space reflections at higher quality tiers.

Continuous Gerstner displacement and surface-normal distortion run on the GPU
over cached topology. World-space wave inputs, synchronized tides, sea state,
and body blend weights make neighboring chunks evaluate the same boundary
vertices. `WaterSurfaceEquation` mirrors the visible surface calculation for
`ClientWaterImmersion`, while `UnderwaterOpticsModel` smooths near-plane entry
and exit transitions.

The active vertex shader applies horizontal as well as vertical Gerstner
displacement with analytic tangents. Sparse canonical currents advect surface
detail, while a bounded set of tick-aged boat wakes and impacts deforms the
surface without rebuilding chunk meshes. Depth and wet-boundary metadata drive
shallow-water foam and breaking cues. That shoreline cue is a deterministic
client snapshot approximation; the separate server shoreline grid is not
networked to the renderer.

## Entity hydrodynamics

Boats, items, and living entities use the same `WaterBuoyancyProvider` boundary.
Four footprint corners plus a motion-biased leading sample resolve partial hull
contact. The logical server converts the resulting submerged fraction, current,
body dimensions, and approximate mass into bounded buoyancy and
fluid-relative drag. Shoreline and local SPH velocity can contribute to the
same current, while the client owns only visual boat tilt and wake effects.

Global buoyancy, drag, and added-velocity caps are configurable. The force path
uses the cached entity-water state as a dry fast path, so ordinary land mobs do
not perform five authority samples every tick.

## Diagnostics

`WaterRenderDiagnostics` tracks visible and culled mesh groups, vertices,
triangles, snapshot and generated-metadata memory, scene-copy cost, render time,
SSR cost, and mesh rebuild counts.

Server-side ownership can be inspected with:

- `/wowater inspect [pos]`
- `/wowater summary [radius]`
- `/wowater authority [radius]`
- `/wowater shipcheck [radius]`
- `/wowater compat`
- `/wowater repair [radius]` for sparse projection gaps only

These commands do not import or convert vanilla water.

## Compatibility boundary

Natural kelp, seagrass, sea pickles, coral, and vanilla water-animal spawn
predicates recognize generated Wilderness water. The Minecraft 1.21.1 singular
`tags/fluid/water.json` resource also keeps generic in-water checks working for
the custom source and flowing fluids without converting waterlogged hosts.

The namespaced liquid block exposes a server-only NeoForge fluid handler backed
directly by `WaterAccess`. Negotiation is simulated before execution, and the
4,096 authority-unit to 1,000 mB conversion plans transfers from absolute tank
levels so repeated small operations cannot create volume. Direct projected
block writes used by open-ended machines are reconciled at a guarded
`Level#setBlock` boundary; canonical projection writes are explicitly excluded
from that hook.

Create 6.0.10 receives a narrow adapter for its exact `FluidHelper.isWater`
predicate. The adapter recognizes only the Wilderness source and flowing
registry entries, and only when compatibility is enabled. The mod does not
globally impersonate `Fluids.WATER`, so other exact-identity mods still require
their own focused adapter. Mods that use `#minecraft:water` or the standard
NeoForge fluid capability work through their normal contracts.

Vanilla structures, full waterlogging replacement, direct chunk-internal writes
from other mods, and existing-world conversion remain outside this boundary.
Vanilla or externally tagged water can be reported by diagnostics, but it does
not become Wilderness-owned automatically.

## Validation

The production GameTests exercise real noise generation, `ProtoChunk` writes,
flowing-state preservation, waterlogged-host exclusion, spring placement,
generated metadata, attachment serialization, fluid-handler transactions, and
guarded projection writes. Unit tests cover compact span editing, conserved
solid displacement, shoreline scheduler fairness, multi-point buoyancy,
hydrodynamic force bounds, lossless mB conversion, current/reservoir snapshots,
vertex metadata, transient impulses, and the CPU/GLSL Gerstner mirror.

Use JDK 21 and the Gradle wrapper:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
.\gradlew.bat runClient
```

For a manual client pass, create a new world and inspect oceans, rivers, lakes,
aquifers, springs, frozen shores, chunk frontiers, and repeated surface
crossings under each water quality profile. Drive a boat across crests and
currents, drop items and mobs into shallow and deep water, place and break a
solid in an enclosed full cell, and transfer water through a NeoForge/Create
machine in both directions. Existing chunks containing vanilla water are not a
valid direct-generation test case.
