# Water System Overview

The Wilderness water system is split across several coordinated subsystems:

- Canonical chunk volume: `WaterVolumeChunk`, `CanonicalWater`, `ModAttachments.WATER_VOLUME`
- SPH bucket pours: `BucketPlaceMixin`, `SPHSimulator`, `FluidRenderer`
- Finite fluid simulation: `WildernessFluidRegistry`, `WaterSourceMixin`
- Compatibility projection: `CanonicalWaterFlowMixin`, `CanonicalWaterBucketPickupMixin`
- Client volume synchronization: `WaterVolumeChunkPayload`, `WaterVolumeSynchronizer`, `ClientWaterVolumeSnapshots`
- Ripple and splash particles: `RippleRenderer`, `WaterEntryEventHandler`
- Gerstner waves per water body: `GerstnerWaveRenderMixin`, `WaveEntityPhysics`
- Moon-phase tide system: `TideSystem`, `TideWorldUpdater`, `TideHudOverlay`
- Weather-driven sea state: `OceanSeaState`, `OceanSeaStatePayload`, `ClientOceanSeaState`
- Underwater optics: `ClientWaterImmersion`, `UnderwaterOpticsModel`, `UnderwaterEffectsRenderer`
- Boat rocking: `BoatRenderMixin`, `BoatTiltStore`

Canonical water uses 4,096 fixed-point units per full block and stores sparse
amount, velocity, flags, and temperature in each chunk attachment. SPH owns
mobile bucket volume; when a body settles, that exact volume is distributed
into canonical cells. Vanilla water levels are projections for collision,
swimming, waterlogging boundaries, and third-party compatibility rather than
the simulation's source of truth.

Canonical persistence is complete rather than capped. Network snapshots split
large sparse chunks into bounded pages and clients reassemble the newest
revision even when its packets arrive before the destination chunk. Settled SPH
conversion uses rollback and retry, preserving exact volume when nearby cells
are temporarily full.

Ocean weather is likewise server-authoritative. Rain and thunder drive a
slowly turning wind field, swell/chop energy, and breaking-wave strength. The
server sends one bounded snapshot per second; clients interpolate it and use
the same spectrum for geometry and shader detail. None of this replaces the
vanilla water registry entry, fluid tag, collision, or waterlogging contract,
which remain available to mods through the canonical compatibility projection.

Camera immersion samples that same rendered spectrum on the client. Biome tint,
depth, local canonical velocity, daylight, and sea state feed a bounded optical
model for fog distance/color and the optional built-in caustic overlay. External
shader packs keep their normal water pipeline, while canonical crests above the
flat compatibility plane receive a standard overlay fallback.
