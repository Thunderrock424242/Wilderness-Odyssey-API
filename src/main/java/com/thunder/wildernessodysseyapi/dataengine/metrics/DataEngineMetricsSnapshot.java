package com.thunder.wildernessodysseyapi.dataengine.metrics;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** IMMUTABLE diagnostic view of measured Data Engine counters and gauges. */
public record DataEngineMetricsSnapshot(
        boolean enabled,
        long updatesSubmitted,
        long updatesProcessed,
        long updatesCoalesced,
        long updateFailures,
        long processedPerSecond,
        long coalescedPerSecond,
        long dirtyEntries,
        long queuedWork,
        long queuePeak,
        long networkBatches,
        long networkEntries,
        long estimatedBytesSent,
        long cacheHits,
        long cacheMisses,
        long cacheEntries,
        long asyncTasksSubmitted,
        long asyncTasksCompleted,
        long asyncTasksRejected,
        long asyncQueueLength,
        long lastMainThreadProcessingNanos,
        long totalMainThreadProcessingNanos,
        long totalWorkerProcessingNanos,
        long interestFilteredUpdates,
        long droppedOrSupersededBackgroundUpdates,
        long backpressureEvents,
        boolean backpressureActive,
        Map<ResourceLocation, DataSystemMetricsSnapshot> systems
) {
    /** Returns the measured cache hit rate, or zero before the first lookup. */
    public double cacheHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0L ? 0.0D : (double) cacheHits / total;
    }
}
