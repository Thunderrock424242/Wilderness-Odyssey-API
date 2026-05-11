package com.thunder.wildernessodysseyapi.temporalrift.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public final class TemporalRiftDimensions {
    public static final ResourceKey<Level> THE_BEFORE_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "the_before")
    );

    public static final ResourceKey<DimensionType> THE_BEFORE_TYPE_KEY = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "the_before")
    );

    private TemporalRiftDimensions() {
    }
}
