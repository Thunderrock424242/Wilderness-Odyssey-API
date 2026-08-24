package com.thunder.wildernessodysseyapi.dataengine;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.resources.ResourceLocation;

/** Stable ids owned by the Data Engine itself. */
public final class DataEngineIds {
    /** Debug-HUD proof integration and its explicitly subscribed metric delta. */
    public static final ResourceLocation DEBUG_METRICS = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "data_engine_debug_metrics"
    );
    /** Batched client-visual vegetation snapshots for newly tracked chunks. */
    public static final ResourceLocation REACTIVE_VEGETATION = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "reactive_vegetation"
    );

    private DataEngineIds() {
    }
}
