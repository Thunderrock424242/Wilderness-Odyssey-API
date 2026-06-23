# Water System Overview

The Wilderness water system is split across several coordinated subsystems:

- Canonical chunk volume: `WaterVolumeChunk`, `CanonicalWater`, `ModAttachments.WATER_VOLUME`
- SPH bucket pours: `BucketPlaceMixin`, `SPHSimulator`, `FluidRenderer`
- Finite fluid simulation: `WildernessFluidRegistry`, `WaterSourceMixin`
- Compatibility projection: `CanonicalWaterFlowMixin`, `CanonicalWaterBucketPickupMixin`
- Client volume synchronization: `WaterVolumeChunkPayload`, `WaterVolumeSynchronizer`
- Ripple and splash particles: `RippleRenderer`, `WaterEntryEventHandler`
- Gerstner waves per water body: `GerstnerWaveRenderMixin`, `WaveEntityPhysics`
- Moon-phase tide system: `TideSystem`, `TideWorldUpdater`, `TideHudOverlay`
- Boat rocking: `BoatRenderMixin`, `BoatTiltStore`

Canonical water uses 4,096 fixed-point units per full block and stores sparse
amount, velocity, flags, and temperature in each chunk attachment. SPH owns
mobile bucket volume; when a body settles, that exact volume is distributed
into canonical cells. Vanilla water levels are projections for collision,
swimming, waterlogging boundaries, and third-party compatibility rather than
the simulation's source of truth.
