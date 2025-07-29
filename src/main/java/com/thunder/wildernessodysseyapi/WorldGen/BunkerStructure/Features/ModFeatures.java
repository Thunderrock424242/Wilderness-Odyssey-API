package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features.BunkerFeature;

import java.util.List;
import java.util.Objects;

/**
 * The type Mod features.
 */
public class ModFeatures {
    /** Registry for bunker feature */
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(
            Registries.FEATURE,
            "wildernessodyssey"
    );

    /** Configured features */
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
    // Base bunker feature
    public static final DeferredHolder<Feature<?>, BunkerFeature> BUNKER_FEATURE = FEATURES.register(
            "bunker",
            BunkerFeature::new
    );

    // Configured feature using the bunker feature
    public static final ConfiguredFeature<?, ?> CUSTOM_STRUCTURE = new ConfiguredFeature<>(
            BUNKER_FEATURE.getHolder().get(),
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
