# Anomaly Forest biome

This mod now registers one anomaly biome:

- `wildernessodysseyapi:anomaly_forest`

## Biome vibe note

- `anomaly_forest` is tuned as a forest anomaly biome with vanilla-like forest vegetation and subtle anomaly ambience.

## Mob control (hard-coded)

All spawn lists are hard-coded in:

- `com.thunder.wildernessodysseyapi.worldgen.biome.AnomalyBiomeMobSettings`

You can edit the forest spawn method to control exactly which vanilla mobs and custom mobs can spawn:

- `addForestSpawns`

Custom mob example already included:

- `ModEntities.RIFTBORN`

## Biome visual effect behavior

Client-only anomaly effects are in:

- `com.thunder.wildernessodysseyapi.client.biome.AnomalyZoneClientEffects`

Current behavior while player is inside an anomaly biome:

- Purple-biased fog tint
- Distortion-like portal particles
- Occasional electric spark "cracks"

Effects disable automatically when the player leaves anomaly biomes.

## Compatibility notes

- TerraBlender: the mod now registers an overworld region that maps forest-like vanilla climate slots to the anomaly forest, so it can participate in normal and Large Biomes world presets.
- Biolith/Lithostitched: anomaly biomes are exposed in both `minecraft:is_overworld` and common `c:is_overworld` biome tags for easier cross-mod biome matching.

## Note on world generation

The biomes are registered and tagged for overworld classification.
If you want these to generate naturally in your world seed, wire them into your biome source/world preset (datapack or TerraBlender region).
