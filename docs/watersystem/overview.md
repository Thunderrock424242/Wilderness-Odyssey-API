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
left unchanged during normal runtime. Operators may run `/wowater convert
[radius]` to convert only an explicitly bounded cube of currently loaded exact
vanilla water blocks. The command never loads or scans completed chunks.

The authority mode is persisted in overworld SavedData the first time a world
runs this architecture. The config and gamerule select that initial value; bare
gamerule changes are restored to the persisted value so namespaced fluid can
never resume native flow while canonical storage is paused. An operator can
activate a previously disabled world with `/wowater mode set on <radius>`: every
column in the bounded cube must already be loaded, exact vanilla water is
converted, the result is verified, and only then is ownership persisted. Live
disable is refused because canonical/generated water in unloaded chunks cannot
be proven safe or rolled back by a bounded command.

## Client snapshots

`ClientWaterSnapshotStore` builds immutable `ClientWaterChunkSnapshot` values
from the synchronized generated attachment and sparse runtime payloads. A newly
watched chunk receives a bounded paged baseline. Later changes use contiguous
revision deltas containing only final cell upserts and packed-position
tombstones; expired history falls back to another paged baseline. Per-player
cell and payload budgets spread dense baselines across synchronization passes.
Each chunk entry is replaced atomically, and sparse changes invalidate only the
neighboring mesh boundaries that can share topology.

Sparse chunk and mobile-SPH persistence have independent format versions,
declared counts, hard structural bounds, finite-value validation, and legacy
decoders for the former unversioned layouts. Unsupported or malformed formats
are rejected instead of being silently truncated and re-saved.

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

## Local weather and the water cycle

Localized atmosphere cells now drive a matching regional sea-state field. The
server samples the public weather query at bounded water cells around players,
builds wind, swell, chop, and breaking-wave targets, then approaches those
targets with separate storm-build and calm-decay times. Water gameplay reads
the server field at the actual world position. Clients receive the nearby cell
window and interpolate both between updates and across cell boundaries, so a
storm front can roughen one coast while water beyond the front remains calm.

This is a two-way connection rather than a second weather or water authority:

```text
Wilderness water bodies -> moisture/thermal context -> atmosphere simulation
atmosphere query -> regional sea state -> waves, shore behavior, immersion
atmosphere query -> persistent finite-body ledger -> WaterAccess transfers
atmosphere query -> packed watershed cells -> dynamic river conditions/flood budgets
```

Rain and hail can add conserved volume to finite Wilderness lakes and rivers.
Settled snow contributes only after thawing, while hot, dry, windy conditions
can remove volume through evaporation. Large oceans remain neutral reservoirs,
and every realized change passes through `WaterAccess`; the persistent ledger
only carries sub-cell or temporarily unrealizable balance. Sampling and
transfers are player-bounded, rate-limited, deduplicated by chunk, and never
force an unloaded chunk to load.

The default watershed phase supersedes the older probe ledger with compact
chunk conditions for rainfall memory, saturation, runoff, downstream discharge,
bounded river/lake offsets, sediment, clarity, current, debris, and localized
flood risk. It retains runoff when a cached downstream chunk is unavailable and
never loads that neighbor. Exact temporary overflow uses separately flagged
canonical sparse cells plus an exact-position recession ledger; permanent or
player-placed water cannot pass that two-key removal gate. See
[`watersheds-and-flooding.md`](watersheds-and-flooding.md) for the model,
budgets, safety tags, configuration, and phase-one basin limitation.

Freezing also respects water ownership. Vanilla or externally tagged water may
still be replaced by frosted ice, but Wilderness-owned water is not directly
replaced because doing so would split the projected block from canonical
volume. Instead, the synchronized frozen fraction progressively damps custom
surface motion and applies the visual ice response. This keeps the liquid
ledger authoritative; custom frozen water is therefore visual rather than a
walkable solid until a dedicated frozen-volume projection is implemented.

If localized weather ownership is disabled for a dimension, the regional field
is cleared and consumers fall back to Minecraft's dimension-wide rain and
thunder levels. Full configuration and verification details are documented in
[`weather-coupling.md`](weather-coupling.md).

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
- `/wowater watershed [pos]`
- `/wowater summary [radius]`
- `/wowater authority [radius]`
- `/wowater shipcheck [radius]`
- `/wowater compat`
- `/wowater mode` for persisted authority status
- `/wowater mode set on <radius>` for bounded, verified activation
- `/wowater mode set off` to report why a world-wide rollback is required
- `/wowater repair [radius]` for sparse projection gaps only
- `/wowater convert [radius]` for an explicit loaded-only vanilla-water upgrade

Only the operator-only `convert` command imports vanilla water. Its radius is
capped at 24 blocks and unloaded chunk columns are skipped.

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

Buckets use that same focused-boundary approach. Player and dispenser pickup
award a vanilla water bucket only after an exact 4,096-unit authority drain;
partial finite cells remain unchanged. The namespaced bucket supports dispenser
placement, cauldrons, waterlogging, bucketable fish, and the common
`c:buckets/water` item tag, while waterlogged host blocks continue to store
ordinary vanilla water for broad mod compatibility.

Vanilla structures can opt in with the exact
`wildernessodysseyapi:water` DATA marker. Existing worlds can be upgraded only
through the explicit, loaded-only conversion command above; there is no
automatic completed-chunk scan. Waterlogged hosts remain a controlled vanilla
exception, and direct chunk-section writes from other mods can still bypass the
supported block/capability boundary. Externally tagged water can be reported by
diagnostics, but it does not become Wilderness-owned automatically.

## Validation

The production GameTests exercise real noise generation, `ProtoChunk` writes,
flowing-state preservation, waterlogged-host exclusion, spring placement,
generated metadata, attachment serialization, fluid-handler transactions,
guarded projection writes, exact player/dispenser bucket transactions, custom
bucket waterlogging/cauldron/fish parity, vanilla gameplay parity, bubble
columns, and structure markers. Unit tests cover compact span editing, conserved solid
displacement, staged authority activation, strict persistence and paged/delta
network decoding, shoreline scheduler fairness, multi-point buoyancy and hull
forces, lossless mB conversion, clock tide display, ambience budgets, current
and reservoir snapshots, vertex precision, persistent foam, and CPU/GLSL
surface contracts.

Weather-water unit coverage additionally exercises localized wind and storm
response, asymmetric sea-state build/decay, bounded regional payload decoding,
finite-body rainfall/evaporation/snowmelt flux, persistent fractional balances,
and the frozen-surface shader contract.

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
machine in both directions. Also test player and dispenser pickup from full and
partial finite cells, then use the namespaced bucket with a dispenser, cauldron,
waterloggable block, and bucketable fish. Existing chunks containing vanilla
water are not a valid direct-generation test case. For the coupled pass, use
`/weather clear`, `/weather rain`, and `/weather thunder` while standing near a
coast to confirm the vanilla command bridge drives smooth wave buildup and
decay. Then use `/wilderness weather force rain` and `/wilderness weather clear`
in separate local areas to create a boundary. Confirm two distant players can
see different sea conditions, crossing the boundary is smooth, finite inland
water responds gradually without loading neighboring chunks, and frozen custom
water remains volume-consistent.
