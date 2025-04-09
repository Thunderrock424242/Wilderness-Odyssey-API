package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.BunkerStructure;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The type Mod structures.
 */
public class ModStructures {
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
// Register the custom BunkerStructure as a PlacedFeature
    public static final DeferredHolder<PlacedFeature, PlacedFeature> CUSTOM_STRUCTURE = PLACED_FEATURES.register(
            "custom_structure",
            () -> new PlacedFeature(
                    Holder.direct(new ConfiguredFeature<>(
                            Feature.NO_OP, // Replace with your feature
                            NoneFeatureConfiguration.INSTANCE
                    )),
                    List.of() // Placement modifiers (empty for now)
            )
    );
}
