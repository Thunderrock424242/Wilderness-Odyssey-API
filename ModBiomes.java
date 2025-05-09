package com.thunder.wildernessodysseyapi.worldgen;

import com.thunder.wildernessodysseyapi.BunkerStructure.Features.ModPlacedFeatures;
import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;

public class ModBiomes {
    public static final ResourceKey<Biome> ANOMALY_REGION = ResourceKey.create(Registries.BIOME, new ResourceLocation("wildernessodysseyapi", "meteor_biome"));

    public static void register(BootstrapContext<Biome> context) {
        context.register(ANOMALY_REGION, createMeteorBiome(context));
    }

    private static Biome createMeteorBiome(BootstrapContext<Biome> context) {
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(
                context.lookup(Registries.PLACED_FEATURE),
                context.lookup(Registries.CONFIGURED_CARVER)
        );
        gen.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, ModPlacedFeatures.CUSTOM_STRUCTURE_PLACED_KEY);

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
