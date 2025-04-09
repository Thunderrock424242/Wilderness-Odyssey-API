package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.BunkerStructure.Features;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Objects;

/**
 * The type Mod features.
 */
public class ModFeatures {
    /**
     * The constant CONFIGURED_FEATURES.
     */
// DeferredRegisters for ConfiguredFeature and PlacedFeature
    public static final DeferredRegister<ConfiguredFeature<?, ?>> CONFIGURED_FEATURES = DeferredRegister.create(
            Registries.CONFIGURED_FEATURE,
            "wildernessodyssey"
    );

    /**
     * The constant PLACED_FEATURES.
     */
    public static final DeferredRegister<PlacedFeature> PLACED_FEATURES = DeferredRegister.create(
            Registries.PLACED_FEATURE,
            "wildernessodyssey"
    );

    /**
     * The constant CUSTOM_STRUCTURE.
     */
// Register ConfiguredFeature
    public static final ConfiguredFeature<?, ?> CUSTOM_STRUCTURE = new ConfiguredFeature<>(
            Feature.NO_OP, // Placeholder feature
            NoneFeatureConfiguration.INSTANCE
    );

    static {
        CONFIGURED_FEATURES.register(
                "custom_structure",
                () -> CUSTOM_STRUCTURE
        );

        PLACED_FEATURES.register(
                "custom_structure",
                () -> new PlacedFeature(
                        Holder.direct(CUSTOM_STRUCTURE), // Use direct holder for ConfiguredFeature
                        List.of(PlacementUtils.HEIGHTMAP_WORLD_SURFACE) // Define placement rules
                )
        );
    }

    /**
     * The constant CUSTOM_STRUCTURE_PLACED_KEY.
     */
// Expose CUSTOM_STRUCTURE_PLACED
    public static final ResourceKey<PlacedFeature> CUSTOM_STRUCTURE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Objects.requireNonNull(ResourceLocation.tryParse("wildernessodyssey:custom_structure"))
    );
}
