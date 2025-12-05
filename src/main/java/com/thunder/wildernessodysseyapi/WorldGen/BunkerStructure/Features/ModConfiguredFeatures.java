package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.Objects;

/**
 * The type Mod configured features.
 */
public class ModConfiguredFeatures {
    /**
     * The constant CUSTOM_STRUCTURE_KEY.
     */
    public static final ResourceKey<ConfiguredFeature<?, ?>> CUSTOM_STRUCTURE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Objects.requireNonNull(ResourceLocation.tryParse("wildernessodyssey:custom_structure")) // Use tryParse to create the ResourceLocation
    );

    private ModConfiguredFeatures() {
    }

    public static ConfiguredFeature<?, ?> getCustomStructure() {
        return ModFeatures.CUSTOM_STRUCTURE.get();
    }
}
