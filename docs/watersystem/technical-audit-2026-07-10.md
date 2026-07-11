# Custom Water System Technical Audit

Date: 2026-07-10  
Target: Wilderness Odyssey API, NeoForge 1.21.1  
Verdict: **major restructuring required before normal-player release; suitable only for controlled experimental testing with backups**

## Executive conclusion

The project already has a serious hybrid-water prototype rather than a simple
shader overlay. Its strongest choices are a server-side authority facade,
namespaced fluid registration, chunk attachments, bounded migration queues,
SavedData for derived and mobile state, compact local-effect packets, visual
quality caps, and explicit unload cleanup.

The current implementation is not yet a scalable replacement for vanilla
water. The decisive problem is that automatic migration expands ordinary ocean
columns into up to 4,096 object-backed canonical cells per chunk, persists all
of them, and sends complete chunk snapshots to clients. That defeats the large-
body abstraction and makes heap, chunk NBT, and multiplayer traffic grow with
ocean volume. Authority also remains split between canonical cells and
namespaced projection blocks, while the runtime gamerule can suspend canonical
logic without converting those projections back to a coherent vanilla state.

The visual stack is promising but still CPU-driven: it scans the client world,
evaluates animated waves for thousands of patches, rebuilds marching-cubes SPH
meshes on the render thread, and lacks camera-frustum rejection for its custom
surface patches. It is a workable experimental renderer, not yet a foundation
for volumetric rendering across normal view distances.

This audit made only isolated safety corrections:

- canonical import/write operations no longer load absent chunks;
- SPH collision and ticking sleep at unloaded chunk boundaries;
- canonical network revision state is pruned and invalidated on chunk unwatch;
- SPH tick budgets now rotate fairly and allow one oversized existing body to
  progress instead of freezing forever.

## 1. Current architecture summary

### Startup and ownership

- `WildernessOdysseyAPIMainModClass` bootstraps `WildernessWaterRules`, payload
  registration, registries, configs, and live event handlers.
- `WildernessFluidRegistry` registers a source fluid, flowing fluid, liquid
  block, fluid type, and bucket under the `wildernessodysseyapi` namespace.
- `ModAttachments.WATER_VOLUME` attaches `WaterVolumeChunk` to each chunk and
  wires mutations to `ChunkAccess#setUnsaved(true)`.
- `WildernessWaterAuthority` is the intended read/write facade. It combines
  explicit canonical cells, namespaced projections, and `HybridWaterBodyModel`.

### Creation, state, and updates

- Existing tagged water is imported by `CanonicalWaterSeeder` through the
  budgeted `CanonicalWaterMigrationQueue`.
- Buckets are intercepted by `BucketPlaceMixin` and
  `CanonicalWaterBucketPickupMixin`; canonical fixed-point volume is stored in
  `WaterVolumeChunk` at 4,096 units per full block.
- Disturbed local cells are processed by `WildernessFluidRegistry#tickCell`
  under `localFlowCellsPerTick`. Stable imported cells and sleeping cells do
  not tick.
- Large bodies are sampled through `HybridWaterBodyModel`; derived columns are
  cached per dimension in `LargeWaterBodySavedData`.
- Small energetic falling slices can transfer to `SPHSimulationManager`, run
  through `SPHSimulator`, and settle transactionally back into canonical cells.
- `TideSystem`, `OceanSeaState`, `ShorelineWaterManager`, and
  `ShallowWaterGrid` provide analytic tide, weather spectrum, and bounded local
  shoreline flow. Tides do not edit world blocks.

### Persistence

- Canonical cells persist in chunk attachment NBT through
  `WaterVolumeChunk#serializeNBT`.
- Chunk finalization and water-system version are stored in
  `ChunkDataCapability`.
- Mobile authoritative SPH bodies persist per dimension through
  `SphWaterSavedData`.
- Derived large-body columns persist per dimension through
  `LargeWaterBodySavedData` and are invalidated by the global water-system
  version and a terrain hash.

### Networking

- `WaterVolumeSynchronizer` scans a four-chunk radius every ten server ticks and
  sends paged complete `WaterVolumeChunkPayload` revisions.
- `SphSnapshotSynchronizer` sends bounded server-owned SPH particle snapshots
  every four ticks to players within 96 blocks.
- `SphLocalEffectPayload` sends compact splash/shore-wash events; clients create
  visual-only SPH bodies under local quality caps.
- `OceanSeaStatePayload` sends a fixed-size weather/wave state once per second.
- All water packets are server-to-client. No water mutation packet trusts a
  client.

### Rendering

- `OceanSurfaceRenderer` builds a camera-local LOD patch cache and emits
  animated open-water quads after translucent blocks.
- `ShorelineSurfaceRenderer` fills block-scale local edges and cooperates with
  `OceanSurfaceRenderer` on baked top-face ownership.
- `GerstnerWaveRenderMixin` transforms compatible vanilla liquid vertices and
  hides only replacement-owned top faces.
- `FluidRenderer` rebuilds SPH density fields and marching-cubes meshes on the
  render thread, then emits them through `RenderType.translucent()`.
- `WaterShaders` owns optional surface and underwater core shaders;
  Iris/Oculus falls back to the normal translucent path.
- `ClientWaterImmersion` and `UnderwaterEffectsRenderer` provide animated
  immersion, fog, and caustic overlays.

### Authority assessment

The logical server is authoritative for canonical cells, local flow, mobile
gameplay SPH, sea state, and entity forces. Clients own only render caches,
interpolation, ripples, and local visual SPH. That separation is directionally
correct. The remaining authority flaw is that a namespaced Wilderness fluid
block without a canonical attachment is still classified as
`WILDERNESS_PROJECTION` and treated as authority-owned on both sides. Therefore
canonical storage is not yet the single final source of truth.

## 2. Critical problems

| ID | File and code area | Severity | Problem, trigger, and player impact | Recommended fix | Required before more feature development |
|---|---|---|---|---|---|
| C-01 | `CanonicalWaterSeeder#seedColumn`; `WaterVolumeChunk#cells`; `WaterVolumeSynchronizer#syncLevel` | Critical | Migration imports every accepted cell down to `worldSeedMaxColumnDepth` (default 16). A full ocean chunk can therefore hold 256 x 16 = 4,096 `HashMap<Integer, WaterCell>` entries. Raw serialization is 4,096 x 7 x 4 = 114,688 bytes before NBT compression, and the first 81-chunk client radius can approach 9 MiB of uncompressed canonical payload. Large oceans, high view distance, flying, and multiple players amplify heap, save, and network cost. | Store untouched large bodies as compressed column spans/body descriptors. Keep sparse cells only for local deviations. Network body/column baselines once and send local deltas. | Yes. This is the primary release blocker. |
| C-02 | `WildernessWaterRules#isEnabled`; `CanonicalWaterFlowMixin#replaceVanillaWaterTick`; `ChunkDataCapability#isWaterFinalized`; `WaterRenderingConfig#replacementWaterRenderingEnabled` | Critical | The gamerule can be changed live, but disabling it does not convert namespaced fluid projections back to vanilla or clear/reset finalized migration state. Custom fluid blocks resume native `BaseFlowingFluid` behavior while canonical logic is suspended; re-enabling can leave newly flowed projections untracked in chunks already marked finalized. Clients also have no dedicated authoritative water-mode/epoch packet. Players can see different behavior after toggles, reconnects, or dimension changes. | Make authority mode a persisted server world setting applied at startup. Allow visual-only toggles live. If live authority migration is required, implement an explicit persisted transition state machine and synchronized mode epoch. | Yes. Do not advertise the current gamerule as a safe live rollback. |
| C-03 | `WildernessWaterAuthority#sampleCellOnly`, especially `WILDERNESS_PROJECTION`; `CanonicalWater#getOrImport`; `CanonicalWaterFlowMixin` | Critical | A namespaced Wilderness fluid block is considered authority-owned even if no canonical cell exists. This happens after attachment loss/version changes, while packets are late, when water flows during a disabled period, or when another mod places the fluid directly. The block state and canonical attachment can independently claim water. | On the server, classify untracked projections as an import/recovery source, never final authority. Canonical/local override or a large-body descriptor must own durable volume. On clients, represent late snapshots as an explicit provisional projection state. | Yes. One source of truth is mandatory before extending mechanics. |
| C-04 | `SPHSimulationManager#tickLevel`; `SPHSimulator#resolveBlockCollision`; `CanonicalWater#getOrImport` and `set` | Critical, fixed in this audit | Server SPH bodies continued ticking regardless of chunk activity, and collision/settlement paths could call world access that loads absent neighboring chunks. A settled or moving body near a border could pull terrain back into memory and defeat normal unload behavior. | Implemented: server SPH sleeps unless all current particle chunks are loaded; swept collision stops at unloaded boundaries; canonical import/write helpers reject unloaded chunks. | Resolved for the audited paths; add a GameTest to prevent regression. |

## 3. High-priority problems

| ID | File and code area | Severity | Problem, trigger, and player impact | Recommended fix | Required before more feature development |
|---|---|---|---|---|---|
| H-01 | `WaterVolumeChunk#serializeNBT/#deserializeNBT`; `SphWaterSavedData#load/#save` | High | Canonical attachment data and SPH SavedData have no independent schema version. `CURRENT_WATER_SYSTEM_VERSION` versions chunk finalization and large-body cache semantics, but it cannot safely decode a future stride/layout change. Old, partial, or malformed arrays are silently truncated to whole strides. Mod updates can misread or discard durable water. | Add `format_version` to each durable payload, strict length/count bounds, per-version decoders, and a migration result that preserves unknown/failed data for operator recovery. | Yes, before changing persistence formats or shipping existing worlds. |
| H-02 | `WaterVolumeSynchronizer#syncLevel`; `WaterVolumeChunkPayload#pagesFromData` | High | Any revision change sends the complete chunk attachment, and every player scans 81 chunks twice per second. A single active cell in a dense migrated ocean chunk can cause repeated 100+ KiB snapshots. Several players in different oceans multiply encoding, allocation, and bandwidth. | Send state on `ChunkWatchEvent.Sent`, then revisioned cell deltas/tombstones. Bound bytes per player per tick, coalesce revisions, and fall back to a paged baseline only on join/resync. | Yes for multiplayer scalability. |
| H-03 | `SPHSimulator#publishRenderSnapshot/#step`; `SpatialHashGrid#insert/#queryNeighbours`; `FluidMesh#rebuild`; `MarchingCubes#extract` | High | The spatial hash uses boxed `Long` keys and boxed `Integer` particle indices despite claiming zero allocation. Each physics step performs two neighbor queries per particle, while render snapshots clone every particle each tick. Marching cubes copies a potentially large float array for every mesh revision. Multiple active bodies create heavy allocation and GC pressure. | Replace boxed collections with primitive fastutil structures, use two reusable particle snapshot buffers, cap swept collision samples, and move density/mesh preparation to immutable worker jobs with render-thread upload only. | Yes before raising SPH limits or making it common gameplay water. |
| H-04 | `OceanSurfaceRenderer#refreshCacheIfNeeded/#drawPatch`; `ShorelineSurfaceRenderer#refreshCacheIfNeeded`; `WaterRenderingConfig.WaterQuality.CINEMATIC` | High | Default Cinematic mode permits 18,000 ocean patches out to 192 blocks. All cached patches are emitted even behind the camera; there is distance LOD but no camera-frustum test or occlusion. Per-frame wave/normal/color work remains CPU-side. Flying over an ocean and high FOV/view distance can cause render-thread spikes. | Cache immutable chunk/section water meshes, frustum-cull patch groups, evaluate waves in the vertex shader, add horizon/altitude LOD, and instrument cache rebuild and draw time. | Yes before calling the renderer normal-player ready. |
| H-05 | `FluidRenderer#renderScoped`; `DensityField#rebuild`; `MarchingCubes#extract` | High | SPH density and marching-cubes extraction run synchronously in `AFTER_TRANSLUCENT_BLOCKS`. Each mesh may retain an 80^3 float field (~2 MiB) plus a growing vertex array. A rebuild can freeze the render thread; multiple meshes can retain tens of MiB even without a GPU-buffer leak. | Build capped immutable CPU meshes off-thread from render snapshots, discard stale jobs by revision, use pooled direct upload buffers, and expose retained mesh bytes. | Yes before increasing mesh/body limits. |
| H-06 | `CanonicalWaterBucketPickupMixin#commitCanonicalBucket`; `WildernessWaterAuthority#canBucketPickup`; `CanonicalWater#projectCompatibility` | High | The mixin checks only liquid block level 0, not `canBucketPickup`. A canonical cell projected as a source near the full threshold can yield a full bucket while containing less than 4,096 units. Automation and repeated partial states can violate volume conservation. | Gate pickup through the authority, require a full non-hosted cell, perform an atomic drain, and only let vanilla return the bucket if the drain succeeds. Cover player and dispenser paths. | Yes before finite water is a gameplay promise. |
| H-07 | `ShorelineWaterManager#tick` | High | A stable `HashMap` iteration order and a per-tick update cap mean the same first regions can consume the budget every tick while later regions never advance. Multiple players in separate areas can receive permanently stale shoreline flow. | Maintain a per-level round-robin cursor or queue and allocate a fair per-player/region budget. | Yes before shoreline flow affects more gameplay. |
| H-08 | `WaterBodyClassifier#doClassify`; `WildernessWaterAuthority#sample`; `HybridWaterBodyModel#findSurfaceColumn` | High | A cache miss in a non-ocean biome samples 81 positions. Dry samples can invoke large-body vertical scans, floor searches, and server SavedData lookups. Renderer cache rebuilds can trigger this on the client thread; entity and shore logic trigger it on the server. | Classify from biome/body metadata first, use a bounded flood/shape result computed once per water body, and keep rendering from initiating authority discovery. | Yes for large mixed-biome coastlines and modded biomes. |
| H-09 | `WaterVolumeSynchronizer.PLAYER_REVISIONS`; `ChunkCapabilityHandler#onChunkUnwatch` | High, fixed in this audit | Revision maps previously grew for every explored chunk and suppressed resending unchanged canonical data after the client unloaded and recreated a chunk attachment. Returning players could render with empty/stale authority. | Implemented: prune to the loaded sync window and remove the player's revision on `ChunkWatchEvent.UnWatch`, forcing a fresh baseline on the next watch. | Resolved; a multiplayer watch/unwatch test is still required. |
| H-10 | `SPHSimulationManager#tickLevel` | High, fixed in this audit | Stable list order let early bodies consume the particle budget forever. Later bodies never advanced or settled; a body larger than a lowered budget could freeze permanently. | Implemented: rotate the start cursor per level and allow one oversized body to consume the soft budget so every body eventually progresses. | Resolved; add a deterministic scheduler unit test. |

## 4. Medium-priority improvements

| ID | File and code area | Severity | Problem, trigger, and player impact | Recommended fix | Required before more feature development |
|---|---|---|---|---|---|
| M-01 | `LargeWaterBodySavedData`; `HybridWaterBodyModel#terrainHash` | Medium | The cache is bounded and versioned, but one entry is stored per block column and the terrain hash samples only the height block plus global values. Subsurface/floor edits can leave depth metadata stale until another invalidation path is introduced. | Cache chunk tiles with explicit dirty notifications from relevant block changes; keep it derived and disposable. | Before mechanics depend on exact depth/volume. |
| M-02 | `OceanSurfaceRenderer`, `ShorelineSurfaceRenderer`, `FluidRenderer`, `RippleRenderer` render events | Medium | Four independent translucent passes call `endBatch` separately. Sorting is only within each batch, not across ocean, shore, SPH, ripples, ice, entities, and particles. Fabulous target behavior and shader-pack composition are not tested. | Define one water render coordinator with ordered subpasses, explicit depth/write policy, and a Fabulous/shader-pack compatibility matrix. | Before more transparent effects. |
| M-03 | `RippleRenderer.activeRipples`; `WaterSurfaceDisplacement.DISTURBANCES/LAST_ENTITY_WAKE_TICK` | Medium | These client globals do not store level identity and are not cleared on level unload. Dimension changes with a lower game time can carry stale ripples/wakes into a new world until caps or time pruning remove them. | Store per-level state or clear both systems from `ClientTickHandler#onLevelUnload`. Advance ripple lifetime by tick/partial time, not render frames. | No, but fix before visual polish. |
| M-04 | `SPHSimulationManager` global singleton and mutable maps | Medium | Integrated server and client share one singleton. `active` and pending settlements are concurrent, but `settlementRetries` and restored-level state are not designed as a fully partitioned server/client service. Current event-thread assumptions are implicit. | Split server and client managers or store explicit side-owned level contexts. Assert logical thread on mutations. | Before asynchronous mesh/simulation work. |
| M-05 | `CanonicalWater#displaceForSolidPlacement` | Medium | The source cell is removed before displacement. If every bounded destination is full, undistributed remainder is lost. This can occur when placing structures into sealed/full water. | Retain overflow in a pending displacement record, reject/cancel placement where appropriate, or hand the exact remainder to a bounded mobile body. | Before claiming strict volume conservation. |
| M-06 | `WaterVolumeChunk#decodeCells`; `SphWaterSavedData#loadParticles` | Medium | Values are sanitized, but duplicate packed keys, trailing data, extreme volume totals, and non-finite legacy particle coordinates are not reported. Corrupt data degrades silently and can create perpetual settlement retries. | Validate counts and aggregate limits, sanitize legacy particles on load, log one rate-limited recovery warning, and expose repair diagnostics. | Before public world migration support. |
| M-07 | `UnderwaterEffectsRenderer#renderBuiltInOverlay`; `WaterShaders#isLinked` | Medium | Rendering uses direct OpenGL program checks and manual shader/blend setup. This is backend-specific and does not restore every previous state explicitly. It is the largest Vulkan/backend-port seam. | Hide shader validation and fullscreen drawing behind a renderer backend/service using Minecraft render abstractions. | Before Vulkan work or more framebuffer effects. |
| M-08 | `WaterDebugCommand` and current diagnostics | Medium | Authority/migration commands are strong, but there are no live counters for snapshot bytes/rate, SPH scheduler age, render/cache rebuild time, retained mesh bytes, pending client pages, save bytes, or sleeping/active region age. | Add opt-in rolling counters with zero/near-zero disabled overhead and a compact `/wowater perf` report. | Before performance tuning and multiplayer trials. |

## 5. Low-priority cleanup

| ID | File and code area | Severity | Problem, trigger, and player impact | Recommended fix | Required before more feature development |
|---|---|---|---|---|---|
| L-01 | `SpatialHashGrid` and `SPHSimulator` comments; `RippleRenderer` constants | Low | Comments claim zero allocation, a separate physics thread, and frame-independent behavior, but current code allocates boxed values/snapshots, runs authoritative physics on the server thread, and advances ripple radius per rendered frame. This misleads maintainers. | Update documentation after the scheduler/allocator refactor; use tick-based ripple age. | No. |
| L-02 | `WaveAnimator`, `WaveVertexConsumer`, deprecated `GerstnerWaveProfile` fields | Low | Legacy wave paths remain alongside the active Gerstner stack, increasing ambiguity about which renderer owns displacement. | Confirm references, remove dead paths, and retain one public wave-sampling API. | No. |
| L-03 | `WaterRenderingConfig#suppressVanillaWaterTopFaces` | Low | `replaceVanillaWaterTopFaces` defaults true and is ORed with the separate suppress option, so a user setting suppress false does not disable suppression while replacement remains true. Naming makes the controls look independent when they are not. | Replace both booleans with a clear enum: `OVERLAY`, `REPLACE_TOPS`, `VANILLA`. | No, but clarify before release UI/docs. |

## 6. Performance risks

### CPU

- Canonical migration scans 256 columns per chunk and up to 21 vertical cells
  per column with current depth/cover defaults, then may write up to 1,024 block
  conversions per tick.
- `WaterBodyClassifier` cache misses can fan out into 81 authority queries.
- SPH performs up to four fixed substeps per game tick, two neighbor queries per
  particle per step, swept collision samples, and a complete particle clone for
  rendering.
- Default Cinematic rendering can emit 18,000 ocean patches each frame, plus
  1,400 shoreline patches and SPH/ripple passes, with no frustum grouping.

### Memory and garbage collection

- A fully imported 16-deep ocean chunk stores 4,096 boxed-map entries. A rough
  JVM estimate is 250-450 KiB of heap per such chunk, depending on object layout.
  Hundreds of loaded ocean chunks can therefore consume hundreds of MiB.
- The raw canonical NBT/network array is approximately 112 KiB per full ocean
  chunk before compression.
- Each active `FluidMesh` can retain an 80 x 80 x 80 float density array (~2
  MiB), a reusable marching-cubes work array, and the latest copied mesh array.
- `SpatialHashGrid` boxes keys and particle indices; SPH snapshots/interpolation
  clone particle objects repeatedly.

### GPU

- No persistent custom `VertexBuffer` leak was found in the water renderer; it
  emits through Minecraft buffer sources and owns CPU arrays rather than raw GL
  buffers.
- GPU cost still scales with every emitted translucent patch and overdraw. No
  frustum/occlusion grouping, horizon impostor, or reflection/refraction target
  exists.

### Disk

- Canonical cell arrays are stored per chunk; derived large-body cache stores up
  to 16,384 x 18 integers (~1.125 MiB raw) per dimension.
- SPH SavedData captures every second and marks itself dirty even when state is
  unchanged. Default caps are modest, but maximum settings increase write churn.

### Network

- A fresh 81-chunk radius of full 16-deep ocean attachments is roughly 9 MiB of
  uncompressed payload data per player before protocol overhead/compression.
- A changed dense chunk can resend ~112 KiB twice per second. There is no byte
  budget, delta coalescing, or per-player backpressure.

## 7. Rendering risks

- The stage (`AFTER_TRANSLUCENT_BLOCKS`) is reasonable for overlays but does not
  solve ordering between independent water batches.
- Distance LOD and patch caps exist; camera-frustum and occlusion culling do not.
- Surface geometry is regenerated every frame. World-space analytic waves avoid
  chunk seams, but LOD transitions can still differ in subdivision and
  transparency order.
- Open ocean uses no depth writes in its custom RenderType, reducing dark shards
  but increasing translucent overdraw and ordering sensitivity.
- Fabulous graphics, shader reload, external shader packs, ice/water ordering,
  and high-altitude views lack automated or recorded validation.
- There is no reflection/refraction framebuffer pipeline. Current Fresnel,
  normals, color absorption, foam hints, and underwater overlay are material
  approximations.
- Volumetric water should be a separate render feature layered on the same water
  query/snapshot API. It should not be added directly to the present patch
  renderer or SPH mesh path.

## 8. Simulation risks

- The durable model is hybrid: block-sized fixed-volume local overrides plus
  derived height-field-like large bodies, analytic Gerstner/tide displacement,
  bounded shallow-water regions, and local SPH events.
- This is appropriate for Minecraft only if large bodies remain compressed.
  Current migration materializes ocean volume and undermines that goal.
- Local canonical flow conserves successful transfers and SPH settlement rolls
  back partial writes. Solid displacement and partial bucket pickup are notable
  conservation gaps.
- Local flow is bounded and sleeps; there is no whole-ocean cell ticking.
- SPH is suitable for short falls, splashes, leaks, and breaking effects, not
  rivers, tides, caves, oceans, or permanent waterfalls.
- Tides and Gerstner waves should remain visual/force boundary conditions. True
  per-cell tidal volume movement would be unstable and too expensive.
- Chunk-border flow currently stops when a neighboring chunk is unloaded. The
  system needs explicit wake-on-load edge records if cross-border continuity is
  a gameplay requirement.

## 9. Networking risks

- Direction and trust are sound: water payloads are server-to-client only and
  decoded counts are bounded.
- Join and dimension changes eventually synchronize through periodic scans and
  level-key reset; the audit fix restores unchanged chunks after unwatch.
- Full-state polling is still inefficient and lacks a clear packet/tombstone for
  a removed server body or chunk attachment. SPH mirrors rely on a two-second
  expiry.
- There is no synchronized authority-mode epoch. A dedicated packet should tell
  clients whether the world is vanilla, visual-only, migrating, or active.
- Network protocol version `6` rejects mismatched mod versions globally, but
  durable world-data versions remain independent and incomplete.

## 10. World-save risks

- Chunk attachment dirty wiring is correct and unload cleanup releases runtime
  queues without deleting SavedData.
- SPH capture before level unload/shutdown and settlement rollback are strong.
- Canonical and SPH formats need explicit versions and recovery behavior.
- The amount of stable worldgen water saved is excessive and will inflate chunk
  saves despite compression.
- Derived caches are correctly disposable, but canonical data is not. Migration
  must never erase old data until a new representation is committed and
  validated.
- Disabling the gamerule preserves data, which is good for recovery, but the
  live block/projection behavior is not a safe rollback.

## 11. Vanilla replacement and compatibility risks

### Core systems that should use custom water directly

- canonical volume, local flow, large-body surface/depth, tides/sea state;
- custom rendering and underwater optics;
- mod-owned boats/entity forces, buckets, commands, and future custom machines.

### Systems that should use an abstraction/bridge

- swimming, drowning/air supply, eye immersion, fishing, boats;
- farmland hydration, pathfinding, aquatic spawning, mob AI;
- structure/worldgen queries, waterlogging, bubble-column behavior;
- mod API queries and datapack/KubeJS integrations.

### Systems that may retain tags or projection states

- broad `#minecraft:water` detection;
- NeoForge `FluidType` boating/hydration and fluid handlers;
- vanilla collision/swimming compatibility;
- existing mod buckets, tanks, pipes, and pumps;
- vanilla tint, overlay, cauldron, and dripstone presentation.

### Systems that cannot safely be replaced yet

- arbitrary mods comparing directly to `Fluids.WATER` or `Blocks.WATER`;
- vanilla/modded waterlogging implementations bound to the vanilla fluid;
- bubble columns, conduits, fishing validity, aquatic AI/spawning, farms, and
  redstone contraptions without dedicated integration tests;
- WorldEdit/KubeJS/datapack operations that place raw vanilla water after a
  chunk has been finalized;
- fluid handler transfer semantics between canonical world volume and tanks.

The current tag and namespaced fluid bridge is useful, but it is not a complete
vanilla replacement. Compatibility checks should not be added to core
simulation classes one by one.

## 12. Recommended target architecture

1. `WaterAuthorityService` per server dimension is the only durable writer.
2. `WaterChunkDataV2` stores versioned compressed column spans/body references
   plus sparse dirty overrides, not every stable ocean cell.
3. `LargeBodyIndex` stores immutable/derived ocean, lake, and river descriptors
   and can be discarded/rebuilt.
4. `LocalFlowScheduler` owns active edge/cell queues, chunk-boundary wake records,
   sleep state, and per-player fairness.
5. `LocalEffectService` owns bounded server SPH; client splashes remain event
   driven and visual-only.
6. `WaterProjectionService` is the only code allowed to create/remove proxy
   fluid blocks and can reconcile missing or stale projections.
7. `WaterNetTracker` sends chunk-watch baselines, revisioned deltas, removals,
   water-mode epoch, and byte-budget diagnostics.
8. `ClientWaterSnapshot` is immutable and renderer-facing. Rendering never
   performs authority discovery or server-style world migration.
9. `WaterRenderCoordinator` groups ocean, shore, SPH, ripple, and underwater
   passes behind backend-neutral mesh/material interfaces.

## 13. Recommended compatibility architecture

Define a public `WaterAccess` interface with queries such as `sample`,
`surfaceHeight`, `depth`, `flow`, `isSubmerged`, `extract`, and `insert`.
Implement:

- `WildernessWaterAccess` for authoritative storage;
- `VanillaWaterAdapter` for vanilla/tagged fallback and migration input;
- `NeoForgeFluidAdapter` for buckets, tanks, pipes, and `FluidStack` transfer;
- `WaterloggingAdapter` for hosted states without replacing host blocks;
- focused integration modules for boats/entities, farming, fishing, AI/spawn,
  structures/worldgen, and commands;
- an event/API layer for other mods to register water-like fluids and react to
  authority changes without core-code conditionals.

Hardcoded vanilla checks should live only inside adapters and narrowly justified
mixins. Core simulation and render snapshots should not import `Blocks.WATER` or
`Fluids.WATER`.

## 14. Recommended experimental gamerule/config strategy

- Replace the current live master boolean with a persisted server mode:
  `VANILLA`, `VISUAL_ONLY`, or `EXPERIMENTAL_AUTHORITY`.
- Default public builds to `VANILLA` or `VISUAL_ONLY` until C-01 through C-03
  are resolved. Keep client quality/caustic/ripple controls client-only.
- Apply authority-mode changes on server restart unless an operator uses an
  explicit migration command.
- Persist `water_mode`, `format_version`, `migration_epoch`, and transition
  phase in world SavedData; synchronize all four to joining clients.
- For activation: scan and validate, write V2 authority data, then project
  blocks, then mark chunks committed. Never mark finalized first.
- For rollback: freeze writes, reconcile canonical volume to a supported proxy,
  validate chunk counts, then change mode. Preserve canonical data for recovery
  until the operator explicitly purges it.
- Reject or force server authority when a client local setting conflicts; only
  visual quality may differ per client.

## 15. Testing plan

### Unit tests

- versioned canonical/SPH codec round trips, malformed lengths, duplicate keys,
  unknown versions, and migration rollback;
- local-flow conservation, sleep/wake, full/partial bucket invariants, and solid
  displacement overflow;
- fair SPH and shoreline scheduler rotation under budgets;
- delta coalescing, tombstones, baseline paging, byte budgets, and stale packet
  rejection;
- render LOD ownership, frustum group selection, and cache invalidation.

### GameTests / dedicated integration tests

- chunk unload/reload and server restart with canonical and moving SPH water;
- cross-chunk channels, waterfalls, enclosed caves, waterlogged blocks, and
  unloaded neighbor boundaries;
- bucket player/dispenser pickup and placement, boats, swimming, drowning,
  fishing, bubble columns, farmland, aquatic spawning, and pathfinding;
- existing-world migration, interrupted migration, version upgrade, authority
  enable/disable transition, and rollback;
- structures, aquifers, datapacks, WorldEdit, and modded fluid handlers;
- dedicated-server startup with no client classes loaded.

### Multiplayer tests

- first join, late join, reconnect, respawn, death, teleport, and dimension
  change;
- chunk watch/unwatch/re-watch with an unchanged revision;
- several players in separate oceans and players converging on one active body;
- packet loss/reordering simulation, bandwidth caps, and version mismatch.

### Rendering/manual matrix

- Fast/Fancy/Fabulous, built-in shader on/off, Iris/Oculus shader pack, and
  Sodium/Embeddium-like renderers;
- F3+A/resource reload, shader reload, disconnect/reconnect, dimensions, world
  border, frozen/waterlogged shores, high altitude, high FOV, and rapid flight;
- capture CPU render time, cache rebuild time, triangle/patch counts, retained
  CPU/GPU bytes, allocation rate, and frametime percentiles.

### Current test status

- Focused Java compilation emitted all modified classes; `javap` confirmed the
  new unwatch hook, SPH scheduler state, and canonical guards.
- The Gradle task still ends with the known Windows NeoForm
  `AccessDeniedException` on `build/moddev/artifacts/neoforge-21.1.220.jar` after
  javac reports only warnings.
- 45 JUnit tests executed: 44 passed. The existing
  `SPHSimulationManagerTest#localVisualEffectDoesNotOwnCanonicalVolume` fails
  because it calls `WaterRenderingConfig` before NeoForge loads the client
  config. Fix the test fixture; do not weaken runtime config validation.
- No automated world, multiplayer, rendering, resource-reload, or GPU cleanup
  tests currently cover the replacement renderer.

## 16. Step-by-step implementation roadmap

1. **Freeze the persistence contract.** Add format versions, strict decoders,
   backups/recovery, a mode epoch, and upgrade tests.
2. **Introduce `WaterChunkDataV2`.** Represent stable oceans/rivers/lakes with
   compressed spans/body references and sparse local overrides. Build a
   non-destructive V1-to-V2 migrator.
3. **Make authority singular.** Treat untracked projections as provisional
   import/recovery inputs; route all gameplay writes through one per-dimension
   authority service.
4. **Replace polling snapshots.** Send baselines on chunk watch, then bounded
   revisioned deltas and tombstones with per-player byte budgets.
5. **Harden local simulation.** Add chunk-edge wake records, fair shoreline
   scheduling, strict bucket/displacement conservation, primitive SPH storage,
   and active-area instrumentation.
6. **Build the compatibility API.** Add `WaterAccess`, NeoForge fluid transfer,
   hosted-water adapter, and focused vanilla integration modules. Test each
   contract before removing more vanilla assumptions.
7. **Refactor rendering.** Create immutable chunk/section water snapshots,
   frustum-cull groups, move wave deformation to shaders, asynchronously prepare
   SPH meshes, and coordinate transparent passes.
8. **Gate experimental release.** Default to vanilla/visual-only, require an
   operator opt-in plus backup warning, run soak tests, and collect save/network/
   frametime diagnostics before normal-player enablement.
9. **Add advanced visuals last.** Reflections, refraction, volumetrics, foam,
   and renderer-backend abstraction come only after authority, persistence, and
   budgets are proven.

## Files changed by this audit

- `CanonicalWater.java`: reject imports and writes to unloaded chunks.
- `SPHSimulationManager.java`: fair per-level scheduling and unloaded-area sleep.
- `SPHSimulator.java`: stop swept collision at unloaded chunk boundaries.
- `WaterVolumeSynchronizer.java`: bound revision history and support unwatch
  invalidation.
- `ChunkCapabilityHandler.java`: forward chunk unwatch to the water synchronizer.
- `technical-audit-2026-07-10.md`: this report.

## Unresolved risks

- C-01 through C-03 remain architectural release blockers.
- Canonical and SPH data are not independently versioned.
- Full chunk network snapshots remain expensive.
- Bucket/displacement conservation is incomplete.
- Renderer and SPH CPU/GC costs have not been profiled in-game in this audit.
- Vanilla/mod compatibility remains tag-based and incomplete.
- Fabulous, shader-pack, multiplayer, reload, and long-running save tests remain
  manual.

## Assumptions

- The audited checkout is the intended NeoForge 1.21.1 baseline.
- Default config values in source represent the expected player configuration.
- Minecraft/NeoForge preserves registered namespaced fluid blocks when the
  gamerule is disabled; the audit does not assume automatic block conversion.
- Size estimates are uncompressed upper-order estimates; actual NBT/network
  compression and JVM object layout vary.
- No client visual inspection or dedicated-server world was launched during
  this source audit.

## Final readiness verdict

**Requires major restructuring.** The system is appropriate for controlled
experimental worlds with backups and performance diagnostics. It is not ready
to be enabled by default for normal players, large established worlds, or
general multiplayer servers. Continue visual experimentation only behind a safe
mode while persistence V2, singular authority, delta networking, and
compatibility contracts are built.
