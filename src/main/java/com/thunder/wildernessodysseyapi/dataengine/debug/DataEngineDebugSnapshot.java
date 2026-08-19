package com.thunder.wildernessodysseyapi.dataengine.debug;

/** IMMUTABLE bounded subset of server metrics synchronized to an active operator debug page. */
public record DataEngineDebugSnapshot(
        long serverTick,
        long tickBudgetNanos,
        long lastMainThreadNanos,
        long dirtyEntries,
        long queuedWork,
        long processedPerSecond,
        long coalescedPerSecond,
        long networkBatches,
        long networkEntries,
        long estimatedBytesSent,
        long cacheHits,
        long cacheMisses,
        long cacheEntries,
        long asyncTasksSubmitted,
        long asyncTasksCompleted,
        long asyncTasksRejected,
        int workerThreads,
        int workerQueueLength,
        long interestFilteredUpdates,
        long droppedOrSupersededUpdates,
        boolean backpressureActive
) {
    public double cacheHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0L ? 0.0D : (double) cacheHits / total;
    }
}
