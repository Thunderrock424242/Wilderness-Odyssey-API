package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.List;
import java.util.Objects;

/**
 * The type Mod placed features.
 */
public class ModPlacedFeatures {
    /**
     * The constant CUSTOM_STRUCTURE_PLACED_KEY.
     */
    public static final ResourceKey<PlacedFeature> CUSTOM_STRUCTURE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Objects.requireNonNull(ResourceLocation.tryParse("wildernessodyssey:custom_structure")) // Use tryParse to create ResourceLocation
    );

    private ModPlacedFeatures() {
    }

    public static PlacedFeature customStructurePlaced() {
        Holder<ConfiguredFeature<?, ?>> configuredHolder = ModFeatures.CUSTOM_STRUCTURE.getHolder()
                .orElseThrow(() -> new IllegalStateException("Custom structure not registered"));

        return new PlacedFeature(
                configuredHolder,
                List.<PlacementModifier>of(PlacementUtils.HEIGHTMAP_WORLD_SURFACE) // Define placement rules here
        );
    }
}
