package com.thunder.wildernessodysseyapi.environment.glacial.client;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonSnapshot;
import com.thunder.wildernessodysseyapi.environment.glacial.network.GlacialSeasonSyncPayload;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Active-dimension client mirror and incremental surface rebuild queue. */
public final class ClientGlacialState {

    private static final Set<Long> GLACIAL_CHUNKS = ConcurrentHashMap.newKeySet();
    private static final Set<Long> DIRTY_KEYS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<Long> DIRTY = new ConcurrentLinkedQueue<>();
    private static volatile ClientLevel activeLevel;
    private static volatile GlacialSeasonSnapshot snapshot = GlacialSeasonSnapshot.POLAR_COLD;
    private static volatile long serverTick;

    private ClientGlacialState() {
    }

    /** Accepts only current-dimension data and invalidates water/surface rendering on visual changes. */
    public static synchronized void accept(ClientLevel level, GlacialSeasonSyncPayload payload) {
        if (!payload.dimension().equals(level.dimension().location())) {
            return;
        }
        selectLevel(level);
        GlacialSeasonSnapshot next = payload.snapshot();
        boolean visualsChanged = next.visualSignature() != snapshot.visualSignature();
        snapshot = next;
        serverTick = payload.serverTick();
        if (visualsChanged) {
            ClientWaterSnapshotStore.markAllDirtyMeshes(level);
            GLACIAL_CHUNKS.forEach(ClientGlacialState::markDirty);
        }
    }

    /** Returns the current immutable presentation state. */
    public static GlacialSeasonSnapshot snapshot(ClientLevel level) {
        return activeLevel == level ? snapshot : GlacialSeasonSnapshot.POLAR_COLD;
    }

    /** Last authoritative server tick received by this dimension. */
    public static long serverTick(ClientLevel level) {
        return activeLevel == level ? serverTick : 0L;
    }

    /** Tracks one loaded glacial chunk as a future surface invalidation target. */
    public static synchronized void track(ClientLevel level, int chunkX, int chunkZ) {
        selectLevel(level);
        GLACIAL_CHUNKS.add(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Forgets one unloaded chunk and any queued rebuild. */
    public static void forget(ClientLevel level, int chunkX, int chunkZ) {
        if (activeLevel != level) {
            return;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        GLACIAL_CHUNKS.remove(key);
        DIRTY_KEYS.remove(key);
    }

    /** Returns one unique chunk queued for surface section rebuilding. */
    public static Long pollDirty(ClientLevel level) {
        if (activeLevel != level) {
            return null;
        }
        Long key;
        while ((key = DIRTY.poll()) != null) {
            if (DIRTY_KEYS.remove(key) && GLACIAL_CHUNKS.contains(key)) {
                return key;
            }
        }
        return null;
    }

    /** Clears all active-dimension presentation state. */
    public static synchronized void clear(ClientLevel level) {
        if (activeLevel == level) {
            GLACIAL_CHUNKS.clear();
            DIRTY_KEYS.clear();
            DIRTY.clear();
            snapshot = GlacialSeasonSnapshot.POLAR_COLD;
            serverTick = 0L;
            activeLevel = null;
        }
    }

    private static void selectLevel(ClientLevel level) {
        if (activeLevel != level) {
            GLACIAL_CHUNKS.clear();
            DIRTY_KEYS.clear();
            DIRTY.clear();
            snapshot = GlacialSeasonSnapshot.POLAR_COLD;
            serverTick = 0L;
            activeLevel = level;
        }
    }

    private static void markDirty(long key) {
        if (DIRTY_KEYS.add(key)) {
            DIRTY.offer(key);
        }
    }
}
