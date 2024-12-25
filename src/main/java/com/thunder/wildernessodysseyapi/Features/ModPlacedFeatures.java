package com.thunder.wildernessodysseyapi.Features;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;
import java.util.Objects;

public class ModPlacedFeatures {
    public static final ResourceKey<PlacedFeature> CUSTOM_STRUCTURE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Objects.requireNonNull(ResourceLocation.tryParse("wildernessodyssey:custom_structure")) // Use tryParse to create ResourceLocation
    );

    public static final PlacedFeature CUSTOM_STRUCTURE_PLACED = new PlacedFeature(
            Holder.direct(ModConfiguredFeatures.CUSTOM_STRUCTURE),
            List.of(PlacementUtils.HEIGHTMAP_WORLD_SURFACE) // Define placement rules here
    );
}