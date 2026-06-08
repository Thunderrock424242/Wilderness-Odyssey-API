# Water System Overview

The Wilderness water system is split across several coordinated subsystems:

- SPH bucket pours: `BucketPlaceMixin`, `SPHSimulator`, `FluidRenderer`
- Finite fluid simulation: `WildernessFluidRegistry`, `WaterSourceMixin`
- Ripple and splash particles: `RippleRenderer`, `WaterEntryEventHandler`
- Gerstner waves per water body: `GerstnerWaveRenderMixin`, `WaveEntityPhysics`
- Moon-phase tide system: `TideSystem`, `TideWorldUpdater`, `TideHudOverlay`
- Boat rocking: `BoatRenderMixin`, `BoatTiltStore`
