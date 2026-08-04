package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedChunkState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomically publishes immutable client watershed conditions by chunk.
 */
public final class ClientWatershedSnapshotStore {

    private static final Map<Long, Entry> SNAPSHOTS = new ConcurrentHashMap<>();
    private static volatile Level activeLevel;

    private ClientWatershedSnapshotStore() {
    }

    /** Applies a complete bounded server window on the client network thread handoff. */
    public static void accept(Level level, WatershedRegionSyncPayload payload) {
        if (level == null || payload == null) {
            return;
        }
        selectLevel(level);
        if (!payload.enabled()) {
            SNAPSHOTS.clear();
            ClientWaterSnapshotStore.markAllDirtyMeshes(level);
            return;
        }
        for (WatershedRegionSyncPayload.ChunkSnapshot chunk : payload.chunks()) {
            WatershedChunkState state = WatershedChunkState.fromPacked(chunk.packed());
            Entry next = new Entry(state.revision(), state);
            SNAPSHOTS.compute(chunk.chunkKey(), (key, previous) -> {
                if (previous != null && previous.revision > next.revision) {
                    return previous;
                }
                if (previous == null || !previous.state.conditions().equals(next.state.conditions())) {
                    ClientWaterSnapshotStore.markChunkDirty(level, chunk.chunkX(), chunk.chunkZ());
                }
                return next;
            });
        }
    }

    /** Returns an immutable synchronized condition snapshot, or the dry fallback. */
    public static WatershedConditions get(Level level, int chunkX, int chunkZ) {
        if (activeLevel != level) {
            return WatershedConditions.NONE;
        }
        Entry entry = SNAPSHOTS.get(ChunkPos.asLong(chunkX, chunkZ));
        return entry == null ? WatershedConditions.NONE : entry.state.conditions();
    }

    /** Returns the synchronized compact state for local-flow sampling. */
    public static WatershedChunkState state(Level level, int chunkX, int chunkZ) {
        if (activeLevel != level) {
            return null;
        }
        Entry entry = SNAPSHOTS.get(ChunkPos.asLong(chunkX, chunkZ));
        return entry == null ? null : entry.state;
    }

    /** Removes one unloaded chunk's hydrologic conditions. */
    public static void remove(Level level, int chunkX, int chunkZ) {
        if (activeLevel == level) {
            SNAPSHOTS.remove(ChunkPos.asLong(chunkX, chunkZ));
        }
    }

    /** Clears all client conditions during dimension teardown or disconnect. */
    public static void clear(Level level) {
        if (activeLevel == level) {
            SNAPSHOTS.clear();
            activeLevel = null;
        }
    }

    /** Approximate primitive/client-record memory retained for diagnostics. */
    public static long estimatedBytes(Level level) {
        return activeLevel == level ? SNAPSHOTS.size() * 96L : 0L;
    }

    private static synchronized void selectLevel(Level level) {
        if (activeLevel != level) {
            SNAPSHOTS.clear();
            activeLevel = level;
        }
    }

    private record Entry(long revision, WatershedChunkState state) {
    }
}
