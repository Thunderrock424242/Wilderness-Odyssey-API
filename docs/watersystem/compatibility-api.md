# Water authority and compatibility boundary

## Design rule

`WildernessWaterAuthority` and its canonical/large-body implementation own all
water state. External integrations only translate Minecraft or mod behavior into
queries and controlled requests through `WaterServices`.

The existing storage and renderer were not duplicated or moved. The public
boundary was added under the established `watersystem.water` feature package:

```text
watersystem/water/
|-- api/          public queries, units, interaction results, buoyancy
|-- authority/    adapters from the public API to the current authority
|-- compat/
|   |-- vanilla/  isolated vanilla integration and cached entity state
|   |-- neoforge/ transactional fluid capability and world-write reconciliation
|   `-- create/   Create-local water recognition
|-- config/       core and independently switchable adapter flags
|-- entity/       custom water physics consumers
|-- render/       client rendering consumers
|-- sph/          bounded mobile local water
`-- volume/       canonical storage and large-body authority
```

## Public API

Use `WaterServices.access()` for position, surface, depth, current, water-body,
and server-authoritative volume operations. Amounts use `WaterUnits`, not bucket
or NeoForge fluid constants. `WaterAccess#getWaterUnits` exposes visible,
authority-owned volume for transaction adapters; hidden displacement reservoirs
deliberately return zero through this public read.

Queries intended for render loops or entity ticks should retain a `WaterSample`
and call `WaterAccess#sample(...)`. This avoids allocating `Vec3`, `Optional`, or
record results in hot callers. The convenience methods remain appropriate for
occasional gameplay and compatibility checks.

Use `WaterServices.buoyancy()` when an object needs surface intersection,
submerged fraction, current, and surface normal. The provider accepts an `AABB`
and does not depend on `Boat`. Its four footprint corners and motion-biased
leading sample support uneven hull contact for rafts, items, mobs, and modded
vehicles.

All mutation methods reject client levels. Simulation requests inspect loaded
state without importing blocks, creating attachments, or changing the world.

## First proof adapter: entity water state

`EntityWaterCompat` registers through `WaterCompatibilityRegistry` and maintains
one weak, once-per-game-tick cache per entity. `EntityWaterState` exposes:

- custom-water contact;
- full-body and eye submersion;
- animated surface height and depth;
- current components;
- ticks in water and ticks since leaving water.

`WaveEntityPhysics` and `WaterEntryEventHandler` consume this state instead of
independently calling vanilla water detection. Server-side
`WaveEntityPhysics` now combines the multi-point buoyancy sample with
mass-aware buoyancy, fluid-relative drag, canonical currents, shoreline flow,
and bounded local-SPH velocity. It retains per-profile safety caps and
configurable global scales. It does not overwrite vanilla swimming poses, air
supply, navigation, or drowning; those future adapters should consume the same
state cache.

## Machine-fluid integration

`NeoForgeFluidHandlerAdapter` registers `Capabilities.FluidHandler.BLOCK` for
the standalone Wilderness liquid block. `AuthorityWaterFluidHandler` is a
one-cell view, not a second tank: every simulate/execute call reads and mutates
`WaterAccess` on the logical server thread. It returns the namespaced
Wilderness `FluidStack` and accepts plain component-free fluids in
`#minecraft:water`.

One world block is 4,096 canonical units but NeoForge exposes 1,000 integer
millibuckets. `WaterUnitConversions` plans against absolute before/after levels
and retains the current fixed-point offset when the target mB bin can represent
it. If an exact reversible target cannot encode that offset, the handler may
accept a smaller transfer or reject it instead of rounding volume away.

Some machines place or remove a projected fluid block without asking for a
capability. `WaterProjectionMutationMixin` therefore inspects the normal
server-side `Level#setBlock` boundary. It exits immediately unless the old or
new state is the Wilderness liquid, simulates the exact authority delta, and
lets the canonical commit own the physical projection and returned success.
Air replacement is treated as extraction; solid replacement continues to the
existing placement/displacement path. A focused redirect marks the two
`CanonicalWater` projection writes as internal so they can never recurse into a
second transfer.

Create 6.0.10 additionally compares against vanilla `Fluids.WATER` inside its
own helper. `CreateWaterPredicateMixin` extends only that predicate to the two
Wilderness fluid registry entries. This preserves normal water tags and
NeoForge capabilities without changing global fluid identity.

## Compatibility flags

The server water-simulation config now contains:

- `enableWildernessOdysseyWater` (existing master switch)
- `enableVanillaBucketCompat`
- `enableVanillaBoatCompat`
- `enableEntityWaterCompat`
- `enableEntityHydrodynamics`
- `entityBuoyancyScale`
- `entityDragScale`
- `entityMaxAddedVelocityScale`
- `enableFishingCompat`
- `enableStructureWaterMarkers`
- `enableFluidHandlerCompat`
- `enableCreateWaterCompat`

Bucket, boat, entity detection, hydrodynamics, NeoForge fluid handling, and
Create recognition default on. Fishing and structure markers remain off because
no working adapter exists yet. `enableCreateWaterCompat` depends on
`enableFluidHandlerCompat`; disabling either bridge does not disable canonical
storage, simulation, or rendering.

## Integration status

The implemented and remaining boundaries are:

| Area | Current state | Remaining boundary |
|---|---|---|
| Bucket placement/pickup | Existing narrow hooks conserve canonical volume | Move their internal transaction calls fully behind a vanilla adapter if a public bucket API is added |
| Boat/item/mob motion | Central detection plus multi-point authority sampling and bounded server hydrodynamics | Balance profiles in multiplayer and add vehicle-specific extension hooks |
| Underwater optics | Snapshot surface equation includes spectrum, tide, transient wakes, and current | Consume cached player eye state everywhere vanilla fallback remains |
| NeoForge machines | Transactional block capability backed by `WaterAccess` | Add capability GameTests for representative third-party machines |
| Create | Local water predicate plus guarded open-world projection writes | Add a live open-ended-pipe GameTest against supported Create versions |
| Tide HUD | Uses vanilla `isInWater()` as a UI visibility hint | Switch to cached player state |
| Fishing/farmland/AI | Authority helper methods exist but no isolated adapters | Add one adapter at a time with GameTests |
| Structures | No marker conversion adapter | Convert markers once during placement/post-processing; never scan structures per tick |
| Waterlogging | Imported as hosted canonical water and excluded from replacement surfaces | Keep as the documented controlled vanilla exception |

## Writing future adapters

1. Put vanilla adapters in `compat.vanilla`; put optional integrations in a
   `compat.mods.<modid>` package.
2. Implement `WaterCompatibilityAdapter` only when the adapter has a real event,
   hook, or bridge to initialize.
3. Check `ModList.get().isLoaded(modId)` before constructing a third-party
   adapter. Keep all imports of optional API classes inside that mod-specific
   package so absent mods cannot trigger classloading.
4. Query `WaterServices`; never search body collections or reproduce wave/depth
   equations.
5. Send mutations through `WaterAccess` on the logical server. Simulate before
   executing when the outside system supports negotiation.
6. Cache state transitions and fire events only when a value changes. Never fire
   an event for every render query.
7. Add a focused unit test for translation/math and a GameTest for world or
   inventory behavior before raising the adapter's compatibility level.

## Compatibility levels

`UNSUPPORTED`, `DETECTED`, `BASIC`, `INTEGRATED`, and `FULL` are defined by
`CompatibilityLevel`. `WaterCompatibilityRegistry.statuses()` exposes current
adapter state to `/wowater compat`. Entity state remains `BASIC` because
vanilla movement and breathing are not replaced. NeoForge machine transfer and
Create-local recognition report `INTEGRATED`; neither claims universal support
for mods that bypass normal block/capability entry points.

## Deliberate limitations

- The current hybrid body model identifies a large body at cached chunk-column
  granularity. `WaterBody#regionKey` is diagnostic and short-lived, not a
  persistent UUID.
- The API boundary does not solve the audit's dense V1 ocean persistence or full
  snapshot networking costs. A compressed V2 representation can replace the
  implementation behind `AuthorityWaterAccess` later without changing adapters.
- Mods that mutate chunk sections directly can bypass `Level#setBlock` and the
  NeoForge capability. Exact vanilla-fluid identity comparisons still require a
  focused adapter like Create's; there is intentionally no global identity lie.
- Integer millibuckets cannot represent every one of the 4,097 possible
  canonical occupancy values. The handler preserves exact units by refusing a
  non-reversible boundary transfer instead of silently rounding it.
- Swimming/drowning overrides, structure markers, existing-world conversion,
  and a live Create pipe integration test remain follow-up work.
