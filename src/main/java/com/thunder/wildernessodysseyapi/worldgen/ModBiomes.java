package com.thunder.wildernessodysseyapi.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;

public class ModBiomes {
    public static final ResourceKey<Biome> METEOR_BIOME = ResourceKey.create(Registries.BIOME, new ResourceLocation("yourmodid", "meteor_biome"));

    public static void register(BootstapContext<Biome> context) {
        context.register(METEOR_BIOME, createMeteorBiome());
    }

    private static Biome createMeteorBiome() {
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder();
        gen.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, ModPlacedFeatures.METEOR_CRATER_PLACED);

        return new Biome.BiomeBuilder()
                .temperature(0.7F).downfall(0.3F).hasPrecipitation(true)
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(gen.build())
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .skyColor(0x8888FF)
                        .waterColor(0x4444FF)
                        .waterFogColor(0x222266)
                        .fogColor(0x9999AA)
                        .build())
                .build();
    }
}
