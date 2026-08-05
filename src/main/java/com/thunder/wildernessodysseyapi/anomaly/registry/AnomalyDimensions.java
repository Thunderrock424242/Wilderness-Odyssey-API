package com.thunder.wildernessodysseyapi.anomaly.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/** Registry keys for the data-driven Anomaly dimension and its dimension type. */
public final class AnomalyDimensions {
    /** The server level in which rift creatures are native. */
    public static final ResourceKey<Level> ANOMALY_DIMENSION_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "anomaly_dimension")
    );

    /** The fixed-night dimension type used by the Anomaly level. */
    public static final ResourceKey<DimensionType> ANOMALY_DIMENSION_TYPE_KEY = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "anomaly_dimension")
    );

    private AnomalyDimensions() {
    }
}
