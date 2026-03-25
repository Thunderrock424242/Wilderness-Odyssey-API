# Anomaly Zone biomes

This mod now registers three anomaly-zone biome variants:

- `wildernessodysseyapi:anomaly_plains`
- `wildernessodysseyapi:anomaly_tundra`
- `wildernessodysseyapi:anomaly_rainforest`

## Biome vibe note

- `anomaly_plains` is tuned as a jungle + forest + plains hybrid (rolling plains, forest flowers/grass, and sparse jungle trees).

## Mob control (hard-coded)

All spawn lists are hard-coded in:

- `com.thunder.wildernessodysseyapi.worldgen.biome.AnomalyBiomeMobSettings`

You can edit each biome method to control exactly which vanilla mobs and custom mobs can spawn:

- `addPlainsSpawns`
- `addTundraSpawns`
- `addRainforestSpawns`

Custom mob example already included:

- `ModEntities.PURPLE_STORM_MONSTER`

## Biome visual effect behavior

Client-only anomaly effects are in:

- `com.thunder.wildernessodysseyapi.client.biome.AnomalyZoneClientEffects`

Current behavior while player is inside an anomaly biome:

- Purple-biased fog tint
- Distortion-like portal particles
- Occasional electric spark "cracks"

Effects disable automatically when the player leaves anomaly biomes.

## Compatibility notes

- TerraBlender: the mod now registers an overworld region that maps vanilla climate slots to anomaly biomes, so anomaly biomes can participate in normal and Large Biomes world presets.
- Biolith/Lithostitched: anomaly biomes are exposed in both `minecraft:is_overworld` and common `c:is_overworld` biome tags for easier cross-mod biome matching.

## Note on world generation

The biomes are registered and tagged for overworld classification.
If you want these to generate naturally in your world seed, wire them into your biome source/world preset (datapack or TerraBlender region).
