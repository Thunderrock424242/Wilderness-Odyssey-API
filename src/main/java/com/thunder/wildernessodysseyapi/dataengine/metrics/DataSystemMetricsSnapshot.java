package com.thunder.wildernessodysseyapi.dataengine.metrics;

import net.minecraft.resources.ResourceLocation;

/** IMMUTABLE per-subsystem metric totals captured for diagnostics. */
public record DataSystemMetricsSnapshot(
        ResourceLocation systemId,
        long updatesSubmitted,
        long updatesProcessed,
        long updateFailures,
        long processingNanos,
        long networkBatches,
        long networkEntries,
        long estimatedNetworkBytes
) {
}
