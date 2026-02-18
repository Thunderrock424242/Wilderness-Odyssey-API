# Anomaly Zone biomes

This mod now registers four anomaly-zone biome variants:

- `wildernessodysseyapi:anomaly_plains`
- `wildernessodysseyapi:anomaly_desert`
- `wildernessodysseyapi:anomaly_tundra`
- `wildernessodysseyapi:anomaly_rainforest`

## Mob control (hard-coded)

All spawn lists are hard-coded in:

- `com.thunder.wildernessodysseyapi.worldgen.biome.AnomalyBiomeMobSettings`

You can edit each biome method to control exactly which vanilla mobs and custom mobs can spawn:

- `addPlainsSpawns`
- `addDesertSpawns`
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

## Note on world generation

The biomes are registered and tagged for overworld classification.
If you want these to generate naturally in your world seed, wire them into your biome source/world preset (datapack or TerraBlender region).
