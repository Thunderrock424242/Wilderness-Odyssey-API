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
|   `-- vanilla/  isolated vanilla integration and cached entity state
|-- config/       core and independently switchable adapter flags
|-- entity/       custom water physics consumers
|-- render/       client rendering consumers
|-- sph/          bounded mobile local water
`-- volume/       canonical storage and large-body authority
```

## Public API

Use `WaterServices.access()` for position, surface, depth, current, water-body,
and server-authoritative volume operations. Amounts use `WaterUnits`, not bucket
or NeoForge fluid constants.

Queries intended for render loops or entity ticks should retain a `WaterSample`
and call `WaterAccess#sample(...)`. This avoids allocating `Vec3`, `Optional`, or
record results in hot callers. The convenience methods remain appropriate for
occasional gameplay and compatibility checks.

Use `WaterServices.buoyancy()` when an object needs surface intersection,
submerged fraction, current, and surface normal. The provider accepts an `AABB`
and does not depend on `Boat`, so the same path can support rafts, items, mobs,
and modded vehicles.

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
independently calling vanilla water detection. This is detection only. It does
not yet overwrite vanilla swimming poses, air supply, navigation, or drowning.
Those later adapters should consume the same state cache.

## Compatibility flags

The server water-simulation config now contains:

- `enableWildernessOdysseyWater` (existing master switch)
- `enableVanillaBucketCompat`
- `enableVanillaBoatCompat`
- `enableEntityWaterCompat`
- `enableFishingCompat`
- `enableStructureWaterMarkers`
- `enableFluidHandlerCompat`

Existing bucket and boat paths default on to preserve behavior. Entity detection
also defaults on. Fishing, structure markers, and fluid handlers default off
because no working adapter exists yet. Disabling an adapter does not disable
canonical storage, migration, simulation, or rendering.

## Current mixed concerns and migration order

The inspection found these remaining boundaries:

| Area | Current state | Next isolated change |
|---|---|---|
| Bucket placement/pickup | Mixins are already external hooks, but still call `CanonicalWater` directly | Move transaction capture/commit into `compat.vanilla.BucketCompat`, using `WaterAccess` mutation results |
| Boat motion | Gated independently and uses central entity contact, but wave-force code still lives in `WaveEntityPhysics` | Make the boat hook consume `WaterBuoyancyProvider` for vertical support and smoothing |
| Underwater optics | Custom immersion exists, with a remaining vanilla fluid fallback | Consume cached player eye state when entity compatibility is enabled |
| Tide HUD | Uses vanilla `isInWater()` as a UI visibility hint | Switch to cached player state |
| Fishing/farmland/AI | Authority helper methods exist but no isolated adapters | Add one adapter at a time with GameTests |
| Structures | No marker conversion adapter | Convert markers once during placement/post-processing; never scan structures per tick |
| Waterlogging | Imported as hosted canonical water and excluded from replacement surfaces | Keep as the documented controlled vanilla exception |
| Fluid handlers | No machine bridge | Add a server-only `WaterFluidBridge` only when a real handler consumes it |

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
adapter state for future debug commands or UI. The entity adapter is `BASIC`:
central detection works, but vanilla movement and breathing are not yet replaced.

## Deliberate limitations

- The current hybrid body model identifies a large body at cached chunk-column
  granularity. `WaterBody#regionKey` is diagnostic and short-lived, not a
  persistent UUID.
- The API boundary does not solve the audit's dense V1 ocean persistence or full
  snapshot networking costs. A compressed V2 representation can replace the
  implementation behind `AuthorityWaterAccess` later without changing adapters.
- Bucket transaction refactoring, swimming/drowning overrides, boat vertical
  buoyancy, structure markers, and NeoForge fluid handlers remain separate
  follow-up adapters.
