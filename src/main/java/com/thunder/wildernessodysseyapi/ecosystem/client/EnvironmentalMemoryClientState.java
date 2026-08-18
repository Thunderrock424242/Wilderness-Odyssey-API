package com.thunder.wildernessodysseyapi.ecosystem.client;

import com.thunder.wildernessodysseyapi.ecosystem.network.EnvironmentalMemoryDebugPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

/** Client cache for the latest debug-only environmental-memory cell snapshot. */
public final class EnvironmentalMemoryClientState {

    private static volatile EnvironmentalMemoryDebugPayload latest;
    private static volatile long latestReceivedNanos;
    private static final long SNAPSHOT_EXPIRY_NANOS = 3_000_000_000L;

    private EnvironmentalMemoryClientState() {
    }

    /** Accepts the server-owned snapshot on the client game thread. */
    public static void accept(EnvironmentalMemoryDebugPayload payload) {
        latest = payload;
        latestReceivedNanos = System.nanoTime();
    }

    /** Returns a snapshot only when it still describes the displayed dimension and chunk. */
    public static Optional<EnvironmentalMemoryDebugPayload> current(
            ResourceLocation dimension,
            ChunkPos cell
    ) {
        EnvironmentalMemoryDebugPayload snapshot = latest;
        if (snapshot == null
                || System.nanoTime() - latestReceivedNanos > SNAPSHOT_EXPIRY_NANOS
                || !snapshot.dimension().equals(dimension)
                || snapshot.chunkX() != cell.x
                || snapshot.chunkZ() != cell.z) {
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }
}
