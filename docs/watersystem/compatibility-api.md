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
one weak, once-per-game-tick cache per entity. Four inset footprint corners, a
motion-leading point, and one eye point prevent wide hulls and shoreline contact
from depending on the entity center. `EntityWaterState` exposes:

- custom-water contact;
- full-body and eye submersion;
- animated surface height and depth;
- current components;
- ticks in water and ticks since leaving water.

`EntityWaterParityMixin` supplies this cached animated contact to vanilla
`isInWater`, water-tag eye checks, and NeoForge's eye fluid-type path. Swimming,
air consumption, drowning, and water travel therefore follow the animated
authority surface. It replaces a vanilla result only when authority observed a
nearby surface; normal vanilla and third-party tagged water keep their original
behavior.

`WaveEntityPhysics` and `WaterEntryEventHandler` consume the same state instead
of independently calling vanilla water detection. Server-side
`WaveEntityPhysics` now combines the multi-point buoyancy sample with
mass-aware buoyancy, fluid-relative drag, canonical currents, shoreline flow,
and bounded local-SPH velocity. It retains per-profile safety caps and
configurable global scales.

## Vanilla parity adapters

The namespaced fluid remains in `#minecraft:water`, and its NeoForge fluid type
already opts into hydration. Consequently vanilla open-water fishing, swim-node
classification, and farmland hydration need no replacement logic. Narrow
mixins cover only the remaining exact `Blocks.WATER` comparisons:

- full Wilderness sources can form and continue vanilla bubble columns;
- fishing approach and splash particles render over Wilderness water when
  `enableFishingCompat` is enabled;
- floating ground, flying, and ground-navigation surface scans recognize the
  standalone Wilderness block.

Structure templates can opt into one-time conversion by placing a structure
block in DATA mode with metadata `wildernessodysseyapi:water`. With
`enableStructureWaterMarkers` enabled, the processed placement entry becomes a
Wilderness source block after normal rotation and processors. The stored
template is unchanged, and no recurring structure scan is performed.

## Buckets and vanilla containers

Player and dispenser pickup share one server-side transaction. Authority-owned
water produces a bucket only when the cell contains exactly 4,096 visible units;
the drain is committed before the item is awarded, and an unexpected partial
drain is restored. Partial finite cells are left unchanged instead of being
rounded up into a full bucket. Unowned vanilla water remains on the vanilla path.

The resulting item is the vanilla water bucket so recipe, inventory, and
third-party exact-item checks retain their normal behavior. The namespaced
Wilderness bucket remains available for machine transfers and has matching
dispenser placement, cauldron filling, waterlogging, and bucketable-fish
behavior. It is also published in the common `c:buckets/water` item tag for
tag-aware recipes and machines. Waterlogging deliberately stores vanilla water in the host block;
only the container call sees the namespaced fluid as vanilla water. Placement
refuses to consume a full bucket into a cell that already contains finite
authority-owned water, preventing hidden volume loss. Disabling the translation
flag still rejects pickup and overwriting placement for owned projections; it
never turns off conservation protection, and unowned vanilla water remains on
the normal path.

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

- `enableWildernessOdysseyWater` (initial value for the persisted authority latch)
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

Bucket, boat, entity detection, hydrodynamics, fishing effects, explicit
structure markers, NeoForge fluid handling, and Create recognition default on.
Structure conversion still requires the exact DATA marker, so ordinary
structure blocks are unaffected. `enableCreateWaterCompat` depends on
`enableFluidHandlerCompat`; disabling either bridge does not disable canonical
storage, simulation, or rendering.

## Integration status

The implemented boundaries and their final live-validation targets are:

| Area | Current state | Intentional boundary or live validation |
|---|---|---|
| Bucket placement/pickup | Exact 4,096-unit player/dispenser transaction with vanilla bucket output and rollback safety | Live-test unusual third-party dispensers that bypass `DispensibleContainerItem` |
| Boat/item/mob motion | Multi-point authority sampling, hull-oriented drag, buoyancy, planing, slamming, angular response, and a public physics-profile registry | Balance on a populated multiplayer server and register profiles for unusual third-party hulls |
| Underwater optics | Cached eye/body sampling plus the same spectrum, tide, wake, current, and body-color inputs used by the visible surface | Verify the GPU/shader-pack matrix on supported hardware |
| NeoForge machines | Transactional block capability backed by `WaterAccess` | Add capability GameTests for representative third-party machines |
| Create | Local water predicate plus guarded open-world projection writes | Add a live open-ended-pipe GameTest against supported Create versions |
| Tide display | No persistent HUD; a vanilla clock shows tide, trend, and moon context while held or inspected | Verify UI scaling and controller/modded-tooltip combinations |
| Fishing/farmland/AI | Tag/FluidType-native gameplay plus focused exact-check adapters | Run representative modpack mob and fishing soak tests |
| Structures | Explicit DATA marker conversion after normal processors | Author templates with `wildernessodysseyapi:water`; no per-tick scan |
| Waterlogging | Custom bucket translates to vanilla water only at the host-container boundary; the host stores ordinary water | Keep as the documented controlled vanilla exception |

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
adapter state to `/wowater compat`. Entity state reports `INTEGRATED` because
vanilla movement and NeoForge breathing consume its animated cache. NeoForge
machine transfer and Create-local recognition also report `INTEGRATED`; none
claims universal support for mods that bypass normal block/capability entry
points.

## Deliberate limitations

- The current hybrid body model identifies a large body at cached chunk-column
  granularity. `WaterBody#regionKey` is diagnostic and short-lived, not a
  persistent UUID.
- The compact generated baseline solves dense V1 ocean expansion, while sparse
  runtime cells now use revision deltas and tombstones after a paged baseline.
  A future representation can still replace `AuthorityWaterAccess` without
  changing adapters.
- Mods that mutate chunk sections directly can bypass `Level#setBlock` and the
  NeoForge capability. Exact vanilla-fluid identity comparisons still require a
  focused adapter like Create's; there is intentionally no global identity lie.
- Integer millibuckets cannot represent every one of the 4,097 possible
  canonical occupancy values. The handler preserves exact units by refusing a
  non-reversible boundary transfer instead of silently rounding it.
- Existing-world conversion is deliberately operator-scoped: `/wowater convert
  [radius]` visits only a capped loaded cube and never starts an automatic
  completed-chunk scan. A disabled persisted authority can only be activated by
  `/wowater mode set on <radius>`, which requires complete loaded coverage and
  verifies conversion before committing the setting. Automatic disable is
  refused because unloaded canonical/generated state needs a world-wide
  rollback tool. A live Create pipe integration test remains follow-up work.
  Structure markers deliberately require explicit template metadata and can
  still be disabled independently in server config.
