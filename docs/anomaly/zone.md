# The Anomaly Dimension

The Anomaly is the native layer of Wilderness Odyssey's rift creatures. It is
not another Riftfall event: Riftborn, Rift Listeners, and Riftbound Wraiths can
live and spawn there even while every other dimension has clear weather.

## Dimension identity

- Level key: `wildernessodysseyapi:anomaly_dimension`
- Dimension type: `wildernessodysseyapi:anomaly_dimension`
- Biome: `wildernessodysseyapi:anomaly_forest`
- Terrain: Overworld noise settings with a fixed Anomaly Forest biome source
- Time: fixed at midnight
- Beds, respawn anchors, and raids: disabled
- Build range: `-64` through `319`, matching the Overworld noise generator

The dimension uses the same `1.0` coordinate scale and world seed as The
Before. That makes it a coordinate-aligned fracture beside The Before rather
than a literal copy: major terrain contours can feel related, while the fixed
biome, features, atmosphere, and population are different.

## Connection to The Before

Anomaly Gateways can be entered from either the Overworld or The Before.
Entering records both the source dimension and the exact gateway coordinates.
The generated gateway in the Anomaly appears near the matching X/Z location,
and using it returns the player beside the original source gateway.

This is deliberately a travel connection only. The Before keeps its existing
allowlist-based, normally empty mob policy; rift creatures do not begin spawning
there simply because the gateway can reach it.

Older player data that only contains Overworld return coordinates remains
compatible and falls back to the Overworld.

## Rift population

The data-pack biome owns the natural spawn weights:

- Riftborn: common groups of two to four
- Rift Listener: uncommon solo threat
- Riftbound Wraith: rare solo predator
- Rift Maw: never a routine natural spawn; a Listener manifests it after
  catching a player

All three natural rift entities still use normal monster darkness, collision,
and mob-cap checks. Outside the Anomaly, their placement also requires an active
dimension-local Riftfall and precipitation. Listener and Maw manifestations no
longer discard themselves in their native dimension merely because no Riftfall
is active elsewhere.

## Terrain and client atmosphere

The Anomaly Forest now includes the normal forest cave, ore, spring, tree,
flower, grass, and mushroom feature stages instead of generating as bare noise
terrain. Its natural monster list is rift-only.

Client effects are cosmetic and side-safe:

- persistent dark violet fog in the Anomaly dimension;
- a lighter version of the tint in Overworld Anomaly Forest incursions;
- sparse portal motes and occasional electrical cracks;
- Anomaly biome music takes priority while the biome owns the player location.

The biome is exposed through both `anomaly_forest` and `is_anomaly_forest`
Wilderness Odyssey tags. The existing Overworld/common compatibility tags are
retained for TerraBlender, Biolith, and Lithostitched integrations.

## In-game verification

1. Start a fresh development world with `runClient` so the updated dynamic
   dimension registries are loaded.
2. Give yourself an Anomaly Gateway and place it in the Overworld.
3. Enter it and confirm you arrive beside, rather than inside, the generated
   destination gateway.
4. Wait through several night-spawn cycles and confirm Riftborn are common,
   Listeners are uncommon, Wraiths are rare, and vanilla hostile mobs do not
   naturally populate the biome.
5. Let a Listener catch a survival player and confirm a temporary Maw can
   manifest without an active Riftfall.
6. Return and confirm the source gateway and coordinates are preserved.
7. Enter The Before through the temporal-rift flow, place another Anomaly
   Gateway, make the round trip, and confirm the player returns to The Before.
8. Confirm The Before itself remains empty unless its mob allowlist is changed.

Existing chunks keep their generated terrain. Use unexplored Anomaly chunks or
a new test world to inspect the updated forest features.
