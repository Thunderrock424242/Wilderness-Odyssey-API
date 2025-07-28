package com.thunder.wildernessodysseyapi.WorldGen.worldgen;

import com.thunder.wildernessodysseyapi.WorldGen.worldgen.features.anomolyzonePlacedFeature;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * The type Mod biomes.
 */
public class ModBiomes {
    /**
     * The constant ANOMALY_ZONE.
     */
    public static final ResourceKey<Biome> ANOMALY_ZONE =
            ResourceKey.create(Registries.BIOME, ResourceLocation.tryBuild(MOD_ID, "anomaly_zone"));

    /**
     * Custom biome key for the meteor impact zone.
     */
    public static final ResourceKey<Biome> METEOR_IMPACT_ZONE =
            ResourceKey.create(Registries.BIOME, ResourceLocation.tryBuild(MOD_ID, "meteor_impact_zone"));

    /**
     * Register.
     *
     * @param context the context
     */
    public static void register(BootstrapContext<Biome> context) {
        // Look up our placed feature (must already be registered in the registry)
        Holder<PlacedFeature> craterHolder =
                context.lookup(Registries.PLACED_FEATURE).getOrThrow(anomolyzonePlacedFeature.METEOR_CRATER_PLACED);

        // Create a BiomeGenerationSettings.Builder with the two required holders:
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(
                context.lookup(Registries.PLACED_FEATURE),
                context.lookup(Registries.CONFIGURED_CARVER)
        );

        // Add our meteor crater into the biome’s LOCAL_MODIFICATIONS step:
        gen.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, (ResourceKey<PlacedFeature>) craterHolder);

        // Finally, build the Biome object:
        Biome anomaly = new Biome.BiomeBuilder()
                .temperature(0.7F)
                .downfall(0.3F)
                .hasPrecipitation(true)
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(gen.build())
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .skyColor(0x8888FF)
                        .waterColor(0x4444FF)
                        .waterFogColor(0x222266)
                        .fogColor(0x9999AA)
                        .build())
                .build();

        context.register(ANOMALY_ZONE, anomaly);
    }
}
