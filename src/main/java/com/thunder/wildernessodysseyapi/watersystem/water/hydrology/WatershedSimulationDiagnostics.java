package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only per-dimension timing and queue diagnostics for watershed updates.
 */
public final class WatershedSimulationDiagnostics {

    private static final Map<ServerLevel, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private WatershedSimulationDiagnostics() {
    }

    /** Publishes one completed tick's bounded simulation measurements. */
    static void publish(
            ServerLevel level,
            int queuedChunks,
            int processedChunks,
            int initializedChunks,
            int floodPlacements,
            int floodRemovals,
            int activeFloodCells,
            long elapsedNanos
    ) {
        SNAPSHOTS.put(level, new Snapshot(
                Math.max(0, queuedChunks),
                Math.max(0, processedChunks),
                Math.max(0, initializedChunks),
                Math.max(0, floodPlacements),
                Math.max(0, floodRemovals),
                Math.max(0, activeFloodCells),
                Math.max(0L, elapsedNanos)
        ));
    }

    /** Returns the latest immutable dimension snapshot. */
    public static Snapshot snapshot(ServerLevel level) {
        return SNAPSHOTS.getOrDefault(level, Snapshot.EMPTY);
    }

    /** Clears the unloading dimension's ephemeral measurements. */
    static void clear(ServerLevel level) {
        SNAPSHOTS.remove(level);
    }

    /** One completed server tick's queue, mutation, and timing counters. */
    public record Snapshot(
            int queuedChunks,
            int processedChunks,
            int initializedChunks,
            int floodPlacements,
            int floodRemovals,
            int activeFloodCells,
            long elapsedNanos
    ) {
        /** Shared result before a dimension has completed a watershed pass. */
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0, 0L);

        /** Returns elapsed watershed work in microseconds. */
        public long elapsedMicros() {
            return elapsedNanos / 1_000L;
        }
    }
}
