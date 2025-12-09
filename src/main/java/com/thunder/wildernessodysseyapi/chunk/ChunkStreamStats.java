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
        int pendingSaves
) {
}
