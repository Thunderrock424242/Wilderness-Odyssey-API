# Water System Overview

The Wilderness water system is split across several coordinated subsystems:

- Canonical chunk volume: `WaterVolumeChunk`, `CanonicalWater`, `ModAttachments.WATER_VOLUME`
- Canonical world seeding: `CanonicalWaterSeeder`, `WaterSimulationConfig`
- Namespaced water registry: `WildernessFluidRegistry`
- SPH bucket pours: `BucketPlaceMixin`, `SPHSimulator`, `FluidRenderer`
- Finite fluid simulation: `WildernessFluidRegistry`, `CanonicalWaterFlowMixin`
- Compatibility projection: `CanonicalWaterFlowMixin`, `CanonicalWaterBucketPickupMixin`
- Client volume synchronization: `WaterVolumeChunkPayload`, `WaterVolumeSynchronizer`, `ClientWaterVolumeSnapshots`
- Live diagnostics: `/wowater inspect`, `/wowater summary`, `/wowater seed`, `/wowater repair`
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
into canonical cells. Vanilla water levels are projections for collision,
swimming, waterlogging boundaries, and third-party compatibility rather than
the simulation's source of truth. The client renderer keeps vanilla water tops
visible by default as the stable fallback/base surface, then draws the animated
replacement ocean and shoreline layers above that compatibility mask.

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

Loaded world water is seeded into canonical volume from exposed plain
`minecraft:water` columns. The seeder imports a bounded depth from oceans,
rivers, lakes, and water under thin cover such as ice, but it skips waterlogged
host blocks by default so modded/vanilla block state is not destroyed. Imported
worldgen cells are flagged as stable reservoirs and do not tick until disturbed.

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
flat compatibility plane receive a standard overlay fallback.

## Debug and manual validation

Use these commands in a dev world:

- `/wowater inspect` or `/wowater inspect <pos>` reports vanilla, canonical,
  projected, and mobile-SPH water state for one block.
- `/wowater summary <radius>` counts nearby wet, vanilla, canonical, projected,
  and mobile-water blocks.
- `/wowater seed <chunkRadius>` imports loaded world water around the player.
  This is operator-only because it writes canonical chunk data.
- `/wowater repair <radius>` reprojects tracked canonical cells back to vanilla
  water blocks for compatibility. This is also operator-only.

Manual test matrix before calling a water build stable:

1. Place water on a cliff and confirm SPH appears, moves, and later settles
   into canonical/vanilla-compatible water.
2. Place water into a shallow basin and verify lateral spread uses multiple
   lower neighbors.
3. Inspect ocean, river, lake, and frozen-ocean chunks with `/wowater inspect`
   to confirm automatic seeding imports plain water while skipping waterlogged
   hosts.
4. Swim, use boats, spawn fish/squid, and use buckets against canonical water.
5. Test with built-in shaders, no shaders, and an external shader pack.
6. Press `F3+A` near beaches and frozen oceans to verify dynamic surfaces do
   not reintroduce dark triangular shoreline gaps.
7. Test at several client render distances and compare FPS/frametime near a
   beach, an open ocean, and a frozen ocean. The replacement surface should
   follow render distance through LODs without drawing block-detail water across
   the entire view.
