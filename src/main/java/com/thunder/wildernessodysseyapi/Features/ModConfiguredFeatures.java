package com.thunder.wildernessodysseyapi.Features;


import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class ModConfiguredFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> CUSTOM_STRUCTURE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            new ResourceLocation("wildernessodyssey", "custom_structure")
    );

    public static final ConfiguredFeature<?, ?> CUSTOM_STRUCTURE = new ConfiguredFeature<>(
            Feature.STRUCTURE, // Replace with your feature type
            NoneFeatureConfiguration.INSTANCE // Use the appropriate configuration
    );
}
