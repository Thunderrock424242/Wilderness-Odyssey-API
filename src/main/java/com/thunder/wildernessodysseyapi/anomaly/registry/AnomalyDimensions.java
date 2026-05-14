package com.thunder.wildernessodysseyapi.anomaly.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public final class AnomalyDimensions {
    public static final ResourceKey<Level> ANOMALY_DIMENSION_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "anomaly_dimension")
    );

    public static final ResourceKey<DimensionType> ANOMALY_DIMENSION_TYPE_KEY = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "anomaly_dimension")
    );

    private AnomalyDimensions() {
    }
}
