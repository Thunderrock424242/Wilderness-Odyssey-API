# Starter bunker terrain-integration checklist

Use this checklist when editing the `wildernessodysseyapi:bunker` template so the top floor blends into terrain more naturally.

## 1) Edit the bunker NBT template

- Put a **blue wool** leveling marker at the exact point where you want terrain contact to happen.
  - Prefer a marker near the center of the bunker footprint.
  - Keep it on the intended top-floor contact band (not on decorative roof details).
- Shape the outer top-floor edge as a transition zone:
  - Use irregular edges instead of a perfect rectangle.
  - Add a 1-3 block-wide ring of terrain-friendly blocks around the exterior edge.
  - Avoid thin floating lips at corners.

## 2) Validate anchoring in-game

Use placement commands while iterating on the NBT:

- `/modpackstructures reload`
- `/modpackstructures place wildernessodysseyapi:modpack/<your_bunker_id> <x> <y> <z> true`

`alignToSurface=true` ensures anchored placement is used while testing.

## 3) Tune terrain blending config for bunker tests

In `serverconfig/wildernessodysseyapi-server.toml`:

- `enableAutoTerrainBlend = true`
- `enableSmartAutoTerrainBlend = true`
- Start with:
  - `autoTerrainBlendMaxDepth = 6`
  - `autoTerrainBlendRadius = 2`

If bunker edges still look abrupt, increase blend radius first, then max depth.

## 4) What code now does automatically for the starter bunker

When the starter bunker is placed, the placer now also runs a dedicated perimeter pass that blends surface blocks in a short ring (3 blocks wide) around the bunker footprint. This helps the top floor visually merge with surrounding terrain instead of ending in a hard border.

The generated ocean island is also landscaped as an overgrown jungle ruin:

- The raised bunker platform uses grass and dirt instead of exposed dirt across the whole island.
- The sloped perimeter remains sandstone and sand so the jungle ends in a readable beach.
- Broad terrain hummocks, seeded ground patches, mossy boulders, and fallen logs break up the flat platform.
- Jungle trees, shrubs, hanging vines, bamboo, and undergrowth share randomized grove centers, producing dense pockets and irregular clearings instead of an even perimeter ring.
- The bunker NBT contains a 157 x 76 x 182 underground facility. The landscape scan protects only its above-ground shell, allowing vegetation over the buried footprint while keeping trunks out of the visible entrance and roof.
- A protected clearing and a worn path on the above-ground shell's negative-Z/front side keep the door and player approach accessible.

Pack authors can tune or disable this pass in `serverconfig/wildernessodysseyapi-server.toml`:

- `starterIslandJungleEnabled = true`
- `starterIslandJungleDensity = 0.75`

These changes apply when the starter bunker is first generated. Existing worlds whose starter bunker has already been placed are intentionally not redecorated.

## Starter water placement boundary

The initial starter island and bunker are constructed through `ServerLevel`
after the selected columns have generated. Those writes use a synchronous,
bounded starter-generation scope in `WorldFluidMutationReconciler`. It covers
the selected island shore, buried bunker, and landscaping within build height.
The context is level-specific and restored in `finally`, including on failure.

In this scope, final terrain replaces generated water instead of performing a
runtime water-displacement transaction. Successful writes update the existing
`GenerationWaterStateMapper` metadata and remove sparse water overrides at the
same position. This prevents ocean fill from piling conserved water over the
island, and prevents carved bunker rooms from releasing hidden displacement
parcels. Authored template water is still mapped into generated Wilderness
water. The bunker NBT asset is unchanged.

The water boundary only accepts an already-loaded target chunk. It uses the
existing loaded shape view for synchronous callbacks, which returns the bounded
boundary approximation for absent neighboring columns instead of loading them.
The starter constructor retains ownership of generating its selected island
columns; the water adapter adds no tickets, futures, worker, or chunk scan.
Normal block flags and callbacks still run. Ordinary player/machine writes,
writes outside the selected footprint, and later gameplay retain finite-volume
conservation and the existing live-water admission guards.

The October 12 regression pass reproduced island-fill displacement and retained
interior parcels before the fix. Five GameTests cover terrain fill, interior
carving and subsequent server ticks, authored water, cleanup after an exception,
and writes outside the generation footprint. All 46 required GameTests passed,
as did all 130 water simulation unit tests and four focused
`SpawnBunkerPlacerTest` checks, including build-height and shore bounds. The
targeted root `:test` task also completed normal JAR packaging. The final water server log is
`.codex-build/starter-water-regression-green.log`.

The tested artifact is `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`,
SHA-256 `D72D3F48CB3F61A7394CE30768280AEA8CE4DB9065E12B98B1050135FBB56409`.
The packaged bunker asset still matches the source SHA-256
`81D61F883D056FC9BB8D9F0AC7BDC3E653CBA9B23D1FB468844CF3E4331E6900`.
This shared-checkout artifact includes concurrent changes from other work;
the checks above establish this starter-water repair's scope.

This prevents flooding during new starter placement. Updating the mod does not
automatically remove water already saved by an older placement; automatic
cleanup cannot safely distinguish those parcels from later player water.
Validate the updated artifact in a fresh test world: the raised platform and
authored dry bunker rooms should stay dry, while the surrounding ocean and any
template-authored water remain present. Full-size starter placement in the
original modpack remains a live-client acceptance check.
