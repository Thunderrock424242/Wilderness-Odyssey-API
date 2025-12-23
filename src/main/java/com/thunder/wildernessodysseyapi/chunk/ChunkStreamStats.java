package com.thunder.wildernessodysseyapi.chunk;

/**
 * Snapshot of chunk streaming health for diagnostics.
 */
public record ChunkStreamStats(
        boolean enabled,
        int trackedChunks,
        int hotCached,
        int warmCached,
        int inFlightIo,
        int pendingSaves,
        java.util.Map<ChunkState, Integer> stateCounts,
        java.util.Map<ChunkTicketType, Integer> ticketCounts,
        int totalTickets,
        int ioQueueDepth,
        long warmCacheHits,
        long warmCacheMisses
) {
    public double warmCacheHitRate() {
        long total = warmCacheHits + warmCacheMisses;
        if (total == 0) {
            return 1.0D;
        }
        return (double) warmCacheHits / (double) total;
    }
}
