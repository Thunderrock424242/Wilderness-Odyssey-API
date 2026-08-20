package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.network.ReactiveVegetationSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client mirror of synchronized vegetation climate keyed by loaded chunk.
 *
 * <p>The store contains immutable records only. A deduplicated dirty queue lets
 * tint-bearing surface sections rebuild gradually when a visible climate bucket
 * changes, instead of invalidating the entire world renderer at once.</p>
 */
public final class ClientVegetationClimateStore {

    // Chunk compilation can ask for colors off the client thread, so visual
    // lookups use a lock-free map while queue mutations remain synchronized.
    private static final Map<Long, VegetationClimateState> STATES = new ConcurrentHashMap<>();
    private static final ArrayDeque<Long> DIRTY_CHUNKS = new ArrayDeque<>();
    private static final Set<Long> QUEUED_DIRTY_CHUNKS = new HashSet<>();
    private static final int MAXIMUM_PENDING_SNAPSHOTS = 4_096;
    private static final Map<Long, Long> REVISIONS = new LinkedHashMap<>();
    private static final Map<Long, PendingSnapshot> PENDING = new LinkedHashMap<>();
    private static volatile Level activeLevel;

    private ClientVegetationClimateStore() {
    }

    /** Publishes one synchronized state and queues a surface rebuild only when needed. */
    public static synchronized void publish(
            Level level,
            int chunkX,
            int chunkZ,
            VegetationClimateState state
    ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);
        VegetationClimateState next = state == null ? VegetationClimateState.DEFAULT : state;
        VegetationClimateState previous = STATES.put(key, next);
        if (previous == null || previous.visualSignature() != next.visualSignature()) {
            offerDirty(key);
        }
    }

    /**
     * Accepts only current-dimension, newer payloads and safely defers an early
     * snapshot until the corresponding client chunk is installed.
     */
    public static synchronized AcceptResult accept(Level level, ReactiveVegetationSyncPayload payload) {
        if (level == null || payload == null
                || !payload.matchesDimension(level.dimension().location())) {
            return AcceptResult.WRONG_DIMENSION;
        }
        ensureLevel(level);
        long key = ChunkPos.asLong(payload.chunkX(), payload.chunkZ());
        Long previousRevision = REVISIONS.get(key);
        if (!isNewerRevision(previousRevision, payload.revision())) {
            return AcceptResult.STALE;
        }
        REVISIONS.put(key, payload.revision());
        trimOldest(REVISIONS, MAXIMUM_PENDING_SNAPSHOTS * 2);

        if (level.getChunkSource().getChunkNow(payload.chunkX(), payload.chunkZ()) == null) {
            PENDING.put(key, new PendingSnapshot(payload.revision(), payload.climateState()));
            trimOldest(PENDING, MAXIMUM_PENDING_SNAPSHOTS);
            return AcceptResult.PENDING;
        }
        PENDING.remove(key);
        publish(level, payload.chunkX(), payload.chunkZ(), payload.climateState());
        return AcceptResult.APPLIED;
    }

    /** Applies a retained latest snapshot when the client finishes installing its chunk. */
    public static synchronized void onChunkLoaded(Level level, int chunkX, int chunkZ) {
        ensureLevel(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);
        PendingSnapshot pending = PENDING.remove(key);
        if (pending != null) {
            publish(level, chunkX, chunkZ, pending.state());
        }
    }

    /** Returns the synchronized climate for one client position without loading a chunk. */
    public static Optional<VegetationClimateState> stateAt(Level level, BlockPos position) {
        if (level == null || position == null || activeLevel != level) {
            return Optional.empty();
        }
        return Optional.ofNullable(stateAtOrNull(position));
    }

    static VegetationClimateState stateAtOrNull(BlockPos position) {
        if (activeLevel == null || position == null) {
            return null;
        }
        return STATES.get(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
    }

    /** Removes one client chunk and any pending tint invalidation. */
    public static synchronized void forget(Level level, int chunkX, int chunkZ) {
        if (activeLevel != level) {
            return;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        STATES.remove(key);
        PENDING.remove(key);
        QUEUED_DIRTY_CHUNKS.remove(key);
    }

    /** Drains a small number of surface rebuild requests for the client tick. */
    public static synchronized List<Long> drainDirty(Level level, int maximumChunks) {
        if (activeLevel != level || maximumChunks <= 0) {
            return List.of();
        }
        List<Long> drained = new ArrayList<>(Math.min(maximumChunks, DIRTY_CHUNKS.size()));
        while (drained.size() < maximumChunks && !DIRTY_CHUNKS.isEmpty()) {
            long key = DIRTY_CHUNKS.removeFirst();
            if (QUEUED_DIRTY_CHUNKS.remove(key) && STATES.containsKey(key)) {
                drained.add(key);
            }
        }
        return List.copyOf(drained);
    }

    /** Clears the mirror when the owning client level unloads. */
    public static synchronized void clear(Level level) {
        if (level == null || activeLevel == level) {
            activeLevel = null;
            STATES.clear();
            DIRTY_CHUNKS.clear();
            QUEUED_DIRTY_CHUNKS.clear();
            REVISIONS.clear();
            PENDING.clear();
        }
    }

    private static void ensureLevel(Level level) {
        if (activeLevel != level) {
            activeLevel = level;
            STATES.clear();
            DIRTY_CHUNKS.clear();
            QUEUED_DIRTY_CHUNKS.clear();
            REVISIONS.clear();
            PENDING.clear();
        }
    }

    private static void offerDirty(long key) {
        if (QUEUED_DIRTY_CHUNKS.add(key)) {
            DIRTY_CHUNKS.addLast(key);
        }
    }

    static boolean isNewerRevision(Long previousRevision, long incomingRevision) {
        return incomingRevision >= 0L
                && (previousRevision == null || incomingRevision > previousRevision);
    }

    private static <T> void trimOldest(Map<Long, T> values, int maximumSize) {
        while (values.size() > maximumSize) {
            Long oldest = values.keySet().iterator().next();
            values.remove(oldest);
        }
    }

    /** Result of validating and applying one server-owned climate snapshot. */
    public enum AcceptResult {
        APPLIED,
        PENDING,
        STALE,
        WRONG_DIMENSION
    }

    private record PendingSnapshot(long revision, VegetationClimateState state) {
    }
}
