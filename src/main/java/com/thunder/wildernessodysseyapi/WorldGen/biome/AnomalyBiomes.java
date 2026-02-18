package com.thunder.wildernessodysseyapi.WorldGen.biome;

import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class AnomalyBiomes {
    private static final int ANOMALY_SKY_COLOR = 0x7A59C7;
    private static final int ANOMALY_FOG_COLOR = 0x7A59C7;
    private static final int ANOMALY_WATER_COLOR = 0x6D4FC8;
    private static final int ANOMALY_WATER_FOG_COLOR = 0x4D2C88;

    private AnomalyBiomes() {
    }

    public static Biome anomalyPlains() {
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        AnomalyBiomeMobSettings.addPlainsSpawns(spawns);

        BiomeGenerationSettings.Builder generation = generationBuilder();
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generation);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generation);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generation);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generation);
        BiomeDefaultFeatures.addDefaultSprings(generation);
        BiomeDefaultFeatures.addSurfaceFreezing(generation);
        BiomeDefaultFeatures.addDefaultOres(generation);
        BiomeDefaultFeatures.addDefaultSoftDisks(generation);
        BiomeDefaultFeatures.addPlainGrass(generation);
        BiomeDefaultFeatures.addPlainVegetation(generation);
        BiomeDefaultFeatures.addDefaultMushrooms(generation);
        BiomeDefaultFeatures.addDefaultExtraVegetation(generation);

        return baseBiome(true, 0.8F, 0.4F, spawns, generation);
    }

    public static Biome anomalyDesert() {
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        AnomalyBiomeMobSettings.addDesertSpawns(spawns);

        BiomeGenerationSettings.Builder generation = generationBuilder();
        BiomeDefaultFeatures.addFossilDecoration(generation);
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generation);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generation);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generation);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generation);
        BiomeDefaultFeatures.addDefaultSprings(generation);
        BiomeDefaultFeatures.addSurfaceFreezing(generation);
        BiomeDefaultFeatures.addDefaultOres(generation);
        BiomeDefaultFeatures.addDefaultSoftDisks(generation);
        BiomeDefaultFeatures.addDesertVegetation(generation);
        BiomeDefaultFeatures.addDesertExtraVegetation(generation);
        BiomeDefaultFeatures.addDesertExtraDecoration(generation);

        return baseBiome(false, 2.0F, 0.0F, spawns, generation);
    }

    public static Biome anomalyTundra() {
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        AnomalyBiomeMobSettings.addTundraSpawns(spawns);

        BiomeGenerationSettings.Builder generation = generationBuilder();
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generation);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generation);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generation);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generation);
        BiomeDefaultFeatures.addDefaultSprings(generation);
        BiomeDefaultFeatures.addSurfaceFreezing(generation);
        BiomeDefaultFeatures.addDefaultOres(generation);
        BiomeDefaultFeatures.addDefaultSoftDisks(generation);
        BiomeDefaultFeatures.addSnowyTrees(generation);
        BiomeDefaultFeatures.addDefaultFlowers(generation);
        BiomeDefaultFeatures.addTaigaGrass(generation);
        BiomeDefaultFeatures.addDefaultMushrooms(generation);
        BiomeDefaultFeatures.addDefaultExtraVegetation(generation);

        return baseBiome(true, -0.45F, 0.8F, spawns, generation);
    }

    public static Biome anomalyRainforest() {
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        AnomalyBiomeMobSettings.addRainforestSpawns(spawns);

        BiomeGenerationSettings.Builder generation = generationBuilder();
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generation);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generation);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generation);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generation);
        BiomeDefaultFeatures.addDefaultSprings(generation);
        BiomeDefaultFeatures.addSurfaceFreezing(generation);
        BiomeDefaultFeatures.addDefaultOres(generation);
        BiomeDefaultFeatures.addDefaultSoftDisks(generation);
        BiomeDefaultFeatures.addBambooVegetation(generation);
        BiomeDefaultFeatures.addLightBambooVegetation(generation);
        BiomeDefaultFeatures.addJungleTrees(generation);
        BiomeDefaultFeatures.addWarmFlowers(generation);
        BiomeDefaultFeatures.addJungleGrass(generation);
        BiomeDefaultFeatures.addDefaultMushrooms(generation);
        BiomeDefaultFeatures.addJungleVines(generation);

        return baseBiome(true, 0.95F, 0.9F, spawns, generation);
    }

    private static Biome baseBiome(boolean hasPrecipitation, float temperature, float downfall,
                                   MobSpawnSettings.Builder spawns, BiomeGenerationSettings.Builder generation) {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(hasPrecipitation)
                .temperature(temperature)
                .downfall(downfall)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .waterColor(ANOMALY_WATER_COLOR)
                        .waterFogColor(ANOMALY_WATER_FOG_COLOR)
                        .fogColor(ANOMALY_FOG_COLOR)
                        .skyColor(ANOMALY_SKY_COLOR)
                        .grassColorOverride(0x6E4FA7)
                        .foliageColorOverride(0x7C57B7)
                        .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                        .backgroundMusic(defaultMusic())
                        .build())
                .mobSpawnSettings(spawns.build())
                .generationSettings(generation.build())
                .temperatureAdjustment(temperature < 0.0F
                        ? Biome.TemperatureModifier.FROZEN
                        : Biome.TemperatureModifier.NONE)
                .build();
    }

    private static Music defaultMusic() {
        return Musics.GAME;
    }

    private static BiomeGenerationSettings.Builder generationBuilder() {
        HolderGetter<PlacedFeature> placedFeatures = lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> worldCarvers = lookup(Registries.CONFIGURED_CARVER);
        return new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
    }

    @SuppressWarnings("unchecked")
    private static <T> HolderGetter<T> lookup(ResourceKey<? extends Registry<T>> registryKey) {
        Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(registryKey.location());
        if (registry == null) {
            throw new IllegalStateException("Missing built-in registry for " + registryKey.location());
        }
        return ((Registry<T>) registry).asLookup();
    }
}
