# Water System Overview

The Wilderness water system is split across several coordinated subsystems:

- Canonical chunk volume: `WaterVolumeChunk`, `CanonicalWater`, `ModAttachments.WATER_VOLUME`
- Hybrid large-body authority: `HybridWaterBodyModel`, `WildernessWaterAuthority`
- Canonical world seeding and migration: `CanonicalWaterSeeder`, `CanonicalWaterMigrationQueue`, `WaterSimulationConfig`
- Namespaced water registry: `WildernessFluidRegistry`
- SPH bucket pours: `BucketPlaceMixin`, `SPHSimulator`, `FluidRenderer`
- Finite fluid simulation: `WildernessFluidRegistry`, `CanonicalWaterFlowMixin`
- Compatibility projection: `CanonicalWaterFlowMixin`, `CanonicalWaterBucketPickupMixin`
- Client volume synchronization: `WaterVolumeChunkPayload`, `WaterVolumeSynchronizer`, `ClientWaterVolumeSnapshots`
- Live diagnostics: `/wowater inspect`, `/wowater summary`, `/wowater authority`, `/wowater migration`, `/wowater seed`, `/wowater repair`
- Release diagnostics: `/wowater shipcheck`
- Ripple and splash particles: `RippleRenderer`, `WaterEntryEventHandler`
- Gerstner waves per water body: `GerstnerWaveRenderMixin`, `WaveEntityPhysics`
- Replacement water surface: `OceanSurfaceRenderer`, `ShorelineSurfaceRenderer`, `WaterRenderingConfig`
- Moon-phase tide system: `TideSystem`, `TideWorldUpdater`, `TideHudOverlay`
- Weather-driven sea state: `OceanSeaState`, `OceanSeaStatePayload`, `ClientOceanSeaState`
- Underwater optics: `ClientWaterImmersion`, `UnderwaterOpticsModel`, `UnderwaterEffectsRenderer`
- Boat rocking: `BoatRenderMixin`, `BoatTiltStore`

Canonical water uses 4,096 fixed-point units per full block and stores sparse
amount, velocity, flags, and temperature in each chunk attachment. SPH owns
mobile bucket volume; when a body settles, that exact volume is distributed
into canonical cells. Large oceans, lakes, rivers, and ponds are handled as
hybrid large bodies instead of full 3D pressure grids: the authority derives a
loaded body column with bounds, base surface height, depth, estimated volume,
shoreline status, flow, tide/wave profile, water type, and optional local sparse
cell overrides. Local detailed cells still handle buckets, cave flooding,
player-dug channels, structure pools, broken dams/pipes, and dirty active
regions. Vanilla water levels are projections for collision, swimming,
waterlogging boundaries, and third-party compatibility rather than the
simulation's source of truth. `WildernessWaterAuthority` is the shared lens for
deciding whether a cell is canonical, a cheap large body, a namespaced
Wilderness projection, hosted water, or a pending vanilla migration source. The
client replacement surface samples that authority layer and treats plain
`minecraft:water` as migration input instead of the visual base surface.

Large-body column metadata is cached per dimension through Minecraft
`SavedData` in `LargeWaterBodySavedData`. That is the preferred "file cache per
world" shape for this system: it follows world saves, dimensions, backups,
server shutdown, and Minecraft's normal dirty-save lifecycle instead of writing
loose cache files beside the world. The cache stores derived surface/floor/depth
metadata keyed by block column plus a terrain hash and the water-system version,
so it is a performance hint, not a second source of water authority.

Local active water cells now sleep when they cannot move and their velocity has
damped below `localFlowSleepSpeed`. Sleeping cells stay persisted in canonical
volume but are skipped by the finite-volume ticker until `CanonicalWater`
schedules that position or a neighbor again. This keeps buckets, drains, and
freshly disturbed water responsive while preventing calm ponds and settled
local cells from consuming the `localFlowCellsPerTick` budget forever.

SPH is now an optional local high-detail layer rather than the water system
itself. The Water Authority, chunk volume, large-body columns, depth maps,
tides, and wave profiles remain the source of truth for oceans, lakes, rivers,
and persistent water storage. Client-side SPH effects are preferred for
splashes, bucket impact visuals, shore wash, storm/anomaly hits, and other
short-lived detail. Those effects are synchronized as compact
`SphLocalEffectPayload` events, not particle-by-particle snapshots; each client
applies its `sphLocalEffectQuality` setting (`OFF`, `LOW`, `MEDIUM`, or `HIGH`)
to clamp particle count, lifetime, active effect count, and tick budget.
Server-owned SPH remains available only for tiny gameplay-critical active water
such as falling canonical volume from small waterfalls or leaks. It is bounded
by `enableServerSphLocalSimulation`, `serverSphMaxActiveBodies`,
`serverSphMaxParticlesPerBody`, and `serverSphParticleTickBudget`, and settled
server SPH merges conserved volume back into canonical cells instead of
becoming permanent world storage.

The long-term replacement path is now namespaced ownership first and tag
compatibility second. `wildernessodysseyapi:wilderness_water` and
`wildernessodysseyapi:flowing_wilderness_water` are real NeoForge fluids backed
by `wildernessodysseyapi:wilderness_water_block` and a bucket item. Canonical
projection now writes that namespaced block for disturbed/owned water while
still importing existing vanilla water during migration. The fluids are added to
`#minecraft:water` so tag-aware biome, structure, spawning, swimming, and
worldgen checks can still treat them as water without registering anything
inside the `minecraft` namespace. Hardcoded `Blocks.WATER` or `Fluids.WATER`
checks remain deliberate follow-up mixin points instead of pretending tag
compatibility covers every vanilla contract.

Mod-owned gameplay checks should route through `WildernessWaterAuthority` when
they are asking "is this water?" rather than "is this specifically vanilla
water?". Its API owns checks such as `isWaterAt`, `isWOWaterAt`,
`getSurfaceHeight`, `getWaterDepth`, `getWaterSurfaceHeight`,
`getWaterAmount`, `isFullWaterCell`, `isPartialWaterCell`,
`isEntitySubmerged`, `canBucketPickup`, `canBucketPlace`, `canBoatFloatAt`,
`canFishAt`, `canHydrateFarmland`, `canFlowInto`, `addWaterVolume`, and
`removeWaterVolume`. `getSurfaceHeight` combines the body base level, tide,
wave profile, and local disturbance. `WaterCompatibility` remains the
diagnostic and conversion-boundary helper that distinguishes tag-water, vanilla
water blocks, Wilderness water blocks, and plain projection blocks for commands,
buckets, canonical flow suppression, terrain replacement, and client
water-column sampling. Direct vanilla constants are still allowed for vanilla
tint sampling, cauldron/dripstone targets, face-culling between tagged water
projections, and migration code that must know whether a block is still
`minecraft:water`.

Generated world water is finalized into canonical volume from exposed plain
`minecraft:water` columns automatically. Raw chunk-load events stay cheap and
only enqueue work because they can fire while Minecraft is preparing initial
spawn chunks or streaming terrain for an exploring player. Once players exist,
newly loaded and newly watched chunks are promoted to the front of the
migration queue instead of being rewritten inside the load/watch callback.
`CanonicalWaterMigrationQueue` then imports plain water, rewrites accepted
`minecraft:water` blocks to `wildernessodysseyapi:wilderness_water_block`, and
requeues any unfinished columns from the server tick loop under configurable
budgets for touched chunks, scanned columns, and converted blocks. A
optional player-centered priority scan can periodically promote already-loaded
chunks around each player, but it is disabled by default because player movement
should not cause mass water scanning. If enabled, it follows the player's
requested view distance, adds a small padding radius, then clamps to the
server's loaded view distance so visible water converges toward Wilderness
ownership without force-loading the whole world. The seeder imports a bounded
depth from oceans, rivers, lakes, and water under thin cover such as ice.
Chunks that complete this scan store both a persistent water-finalized flag and
the current Wilderness water-system version in their chunk data. Future
chunk-load, chunk-watch, and optional player-priority scans skip chunks whose
stored version is current unless a repair/future migration clears the flag, so
ordinary exploration does not keep paying for the same ocean columns.
Waterlogged host blocks, such as kelp, seagrass, and waterloggable modded
blocks, can be imported directly from the motion-blocking surface scan as
hosted canonical water for depth and optics, but the host block is not replaced
and the open-ocean replacement mesh does not treat that cell as an exposed
surface.
Chunk water preprocessing remains async-safe by design: chunk-load/watch hooks
may enqueue or promote immutable chunk positions, and read/apply work happens
later under server-tick budgets against already-loaded chunks. Future worker
threads may prepare purely immutable metadata, but block-state reads and all
canonical/projection writes must remain on Minecraft's logical server thread.
Canonical authority import is allowed to continue after the per-tick block
conversion budget is exhausted, but any skipped plain-water rewrites keep the
chunk in the priority queue from the first skipped column. That prevents
import-only passes from leaving permanent `minecraft:water` behind.
While that catch-up is in progress, exposed pending Minecraft water remains on
the vanilla compatibility renderer instead of becoming replacement geometry.
Coarse LOD patches, shoreline top-face hiding, and Wilderness surface motion
all require canonical or namespaced Wilderness ownership, which keeps unsafe
shore, covered, and waterlogged cells from becoming large stretched replacement
quads or black terrain gaps.
Imported worldgen cells are flagged as stable reservoirs and do not tick until
disturbed. When
`convertSeededWorldWaterToWilderness` and automatic migration are enabled, the
queued pass gradually migrates accepted plain vanilla cells to
`wildernessodysseyapi:wilderness_water_block` or its flowing Wilderness state.
Manual `/wowater seed` remains available as an operator force/repair tool.

Canonical persistence is complete rather than capped. Network snapshots split
large sparse chunks into bounded pages and clients reassemble the newest
revision even when its packets arrive before the destination chunk. Settled SPH
conversion uses rollback and retry, preserving exact volume when nearby cells
are temporarily full. Queued settlement writes are level-owned and flushed
before dimension persistence, so unload cannot duplicate or discard a body.

Disturbed canonical cells now prefer gravity, then distribute sideways across
all lower neighboring cells instead of choosing a single arbitrary direction.
Enough falling water can transfer a conserved slice into SPH as mobile water;
when that SPH body settles, it writes exact volume and averaged velocity back
into canonical cells.

Ocean weather is likewise server-authoritative. Rain and thunder drive a
slowly turning wind field, swell/chop energy, and breaking-wave strength. The
server sends one bounded snapshot per second; clients interpolate it and use
the same spectrum for geometry and shader detail. None of this replaces the
vanilla water registry entry, fluid tag, collision, or waterlogging contract,
which remain available to mods through the canonical compatibility projection.
Untracked vanilla water keeps its normal flow and source-conversion behavior;
only cells already owned by canonical volume suppress vanilla propagation.

Camera immersion samples that same rendered spectrum on the client. Biome tint,
depth, local canonical velocity, daylight, and sea state feed a bounded optical
model for fog distance/color and the optional built-in caustic overlay. External
shader packs keep their normal water pipeline, while canonical crests above the
flat compatibility plane receive the standard tagged-water overlay.

## Debug and manual validation

Use these commands in a dev world:

- `/wowater inspect` or `/wowater inspect <pos>` reports tag-water, canonical,
  projected, vanilla-block, Wilderness-block, and mobile-SPH water state for
  one block.
- `/wowater summary <radius>` counts nearby wet, tag-water, canonical, projected,
  and mobile-water blocks.
- `/wowater authority <radius>` separates Wilderness-owned water from pending
  vanilla migration sources, hosted/waterlogged cells, cheap large-body cells,
  projection gaps, mobile SPH water, and replacement-safe visible surface cells.
- `/wowater migration` reports the automatic migration queue, totals, hosted
  waterlogged imports, player-priority scan counts, skipped unloaded chunks,
  effective view-distance priority radius, promoted chunks, visible priority
  finalization work, finalized chunk skips, and the last tick's migration work.
- `/wowater visible <chunkRadius>` scans loaded chunks around the player and
  reports finalized chunks, queued chunks, unfinished chunks, and any leftover
  plain `minecraft:water` blocks still waiting for Wilderness takeover.
- `/wowater shipcheck <radius>` classifies nearby water as Wilderness-owned,
  vanilla-pending-conversion, hosted-safe, pending import, or projection gaps.
  Use it before visual bug hunting so screenshots can be tied to ownership
  state instead of guesswork.
- `/wowater seed <chunkRadius>` imports loaded world water around the player.
  This is operator-only because it writes canonical chunk data and can force
  migration of nearby plain vanilla water blocks to Wilderness water.
- `/wowater repair <radius>` reprojects tracked canonical cells back to the
  current compatibility water block. This is also operator-only.

Manual test matrix before calling a water build stable:

1. Place water on a cliff and confirm SPH appears, moves, and later settles
   into canonical/vanilla-compatible water.
2. Place water into a shallow basin and verify lateral spread uses multiple
   lower neighbors.
3. Inspect ocean, river, lake, and frozen-ocean chunks with `/wowater inspect`
   to confirm automatic migration imports plain water, gradually converts
   accepted vanilla water into Wilderness water, and imports waterlogged hosts
   as hosted cells without replacing the host block. Use `/wowater migration`
   to confirm the queue is draining and reporting hosted cell counts.
   Use `/wowater shipcheck 16` to decide whether the local area is still
   migrating, clean enough for visual testing, or needs `/wowater repair`.
4. Swim, use boats, spawn fish/squid, and use buckets against canonical water.
5. Test with built-in shaders, no shaders, and an external shader pack.
6. Press `F3+A` near beaches and frozen oceans to verify dynamic surfaces do
   not reintroduce dark triangular shoreline gaps.
7. Test at several client render distances and compare FPS/frametime near a
   beach, an open ocean, and a frozen ocean. The replacement surface should
   follow render distance through LODs without drawing block-detail water across
   the entire view.
8. Run `/wowater migration` after flying over a large ocean and confirm the
   performance line reports the expected local-flow budget, sleep speed, and
   large-body cache count/budget.
9. Place buckets, stand near shore wash, and trigger splash-heavy events at
   `sphLocalEffectQuality=OFF`, `LOW`, `MEDIUM`, and `HIGH`. OFF should keep
   the durable Wilderness water while suppressing client SPH visuals; higher
   settings should add richer local splashes without changing `/wowater`
   authority ownership.
