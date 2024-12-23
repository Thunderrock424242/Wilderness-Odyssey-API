package com.thunder.wildernessodysseyapi.structure;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModStructures {
    public static final DeferredRegister<PlacedFeature> PLACED_FEATURES = DeferredRegister.create(
            ForgeRegistries.Keys.PLACED_FEATURES,
            "wildernessodyssey"
    );

    // Register the custom structure as a PlacedFeature
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
