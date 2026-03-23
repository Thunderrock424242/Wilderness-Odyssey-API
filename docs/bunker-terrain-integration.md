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
