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
