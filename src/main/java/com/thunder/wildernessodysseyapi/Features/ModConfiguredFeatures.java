package com.thunder.wildernessodysseyapi.Features;


import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Objects;

public class ModConfiguredFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> CUSTOM_STRUCTURE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Objects.requireNonNull(ResourceLocation.tryParse("wildernessodyssey:custom_structure")) // Use tryParse to create the ResourceLocation
    );

    public static final ConfiguredFeature<?, ?> CUSTOM_STRUCTURE = new ConfiguredFeature<>(
            Feature.NO_OP, // Replace with your custom feature if available
            NoneFeatureConfiguration.INSTANCE // Use the appropriate configuration
    );
}
