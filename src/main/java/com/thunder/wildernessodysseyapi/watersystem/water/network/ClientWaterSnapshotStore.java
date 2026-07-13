package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Atomically publishes immutable water snapshots for the active client level.
 *
 * <p>Generated attachment sync and sparse payload delivery may arrive in
 * either order. Each update constructs a complete replacement value and uses a
 * single concurrent-map publication. Missing/unloaded keys are always dry.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientWaterSnapshotStore {

    private static final Map<Long, ClientWaterChunkSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Long> DIRTY_MESHES = new ConcurrentLinkedQueue<>();
    private static final Set<Long> DIRTY_MESH_KEYS = ConcurrentHashMap.newKeySet();
    private static volatile Level activeLevel;

    private ClientWaterSnapshotStore() {
    }

    /** Returns the immutable snapshot for a loaded chunk, or {@code null}. */
    public static ClientWaterChunkSnapshot get(Level level, int chunkX, int chunkZ) {
        return activeLevel == level ? SNAPSHOTS.get(ChunkPos.asLong(chunkX, chunkZ)) : null;
    }

    /** Returns the immutable snapshot containing a world position, or {@code null}. */
    public static ClientWaterChunkSnapshot getAtBlock(Level level, int blockX, int blockZ) {
        return get(level, blockX >> 4, blockZ >> 4);
    }

    /** Publishes a synchronized generated baseline for one loaded client chunk. */
    public static void publishGenerated(Level level, int chunkX, int chunkZ, GeneratedWaterChunk generated) {
        selectLevel(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);
        SNAPSHOTS.compute(key, (ignored, previous) -> previous == null
                ? new ClientWaterChunkSnapshot(chunkX, chunkZ, generated.snapshot(), 0L, new int[0])
                : previous.withGenerated(generated.snapshot()));
        markDirty(key);
    }

    /** Publishes one complete sparse runtime revision for a loaded client chunk. */
    public static void publishSparse(
            Level level,
            int chunkX,
            int chunkZ,
            long revision,
            int[] sparseCells
    ) {
        selectLevel(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);
        SNAPSHOTS.compute(key, (ignored, previous) -> previous == null
                ? new ClientWaterChunkSnapshot(chunkX, chunkZ, null, revision, sparseCells)
                : previous.withSparse(revision, sparseCells));
        markDirty(key);
    }

    /** Removes all snapshot and mesh ownership for an unloaded chunk. */
    public static void remove(Level level, int chunkX, int chunkZ) {
        if (activeLevel != level) {
            return;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (SNAPSHOTS.remove(key) != null) {
            markDirty(key);
        }
    }

    /** Returns and removes one chunk key whose cached mesh must be rebuilt. */
    public static Long pollDirtyMesh() {
        Long key;
        while ((key = DIRTY_MESHES.poll()) != null) {
            if (DIRTY_MESH_KEYS.remove(key)) {
                return key;
            }
        }
        return null;
    }

    /** Number of unique chunk groups still waiting for an incremental rebuild. */
    public static int pendingDirtyMeshCount() {
        return DIRTY_MESH_KEYS.size();
    }

    /** Queues every loaded snapshot after a renderer ownership-mode switch. */
    public static void markAllDirtyMeshes(Level level) {
        if (activeLevel != level) {
            return;
        }
        for (long key : SNAPSHOTS.keySet()) {
            offerDirty(key);
        }
    }

    /** Approximate primitive snapshot memory for diagnostics. */
    public static long estimatedBytes(Level level) {
        if (activeLevel != level) {
            return 0L;
        }
        long bytes = 0L;
        for (ClientWaterChunkSnapshot snapshot : SNAPSHOTS.values()) {
            bytes += snapshot.estimatedBytes();
        }
        return bytes;
    }

    /** Compact generated metadata retained by all currently loaded snapshots. */
    public static long generatedEstimatedBytes(Level level) {
        if (activeLevel != level) {
            return 0L;
        }
        long bytes = 0L;
        for (ClientWaterChunkSnapshot snapshot : SNAPSHOTS.values()) {
            bytes += snapshot.generatedEstimatedBytes();
        }
        return bytes;
    }

    /** Number of atomically published, currently loaded snapshot chunks. */
    public static int size(Level level) {
        return activeLevel == level ? SNAPSHOTS.size() : 0;
    }

    /** Publishes the attachment after Minecraft installs a synchronized client chunk. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || !level.isClientSide
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        chunk.getExistingData(ModAttachments.GENERATED_WATER)
                .ifPresent(generated -> publishGenerated(level, chunk.getPos().x, chunk.getPos().z, generated));
    }

    /** Atomically makes an unloaded chunk dry before any later render frame consumes it. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide
                && event.getChunk() instanceof LevelChunk chunk) {
            remove(level, chunk.getPos().x, chunk.getPos().z);
        }
    }

    /** Clears all immutable values when the client changes dimensions or disconnects. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == activeLevel) {
            SNAPSHOTS.clear();
            DIRTY_MESHES.clear();
            DIRTY_MESH_KEYS.clear();
            activeLevel = null;
        }
    }

    private static synchronized void selectLevel(Level level) {
        if (activeLevel != level) {
            SNAPSHOTS.clear();
            DIRTY_MESHES.clear();
            DIRTY_MESH_KEYS.clear();
            activeLevel = level;
        }
    }

    private static void markDirty(long key) {
        offerDirty(key);
        int chunkX = (int) key;
        int chunkZ = (int) (key >>> 32);
        offerLoadedNeighbor(ChunkPos.asLong(chunkX - 1, chunkZ));
        offerLoadedNeighbor(ChunkPos.asLong(chunkX + 1, chunkZ));
        offerLoadedNeighbor(ChunkPos.asLong(chunkX, chunkZ - 1));
        offerLoadedNeighbor(ChunkPos.asLong(chunkX, chunkZ + 1));
    }

    private static void offerLoadedNeighbor(long key) {
        // Missing chunks have no mesh to rebuild. Skipping them prevents a
        // streaming wave of no-op neighbor entries from delaying visible water.
        if (SNAPSHOTS.containsKey(key)) {
            offerDirty(key);
        }
    }

    private static void offerDirty(long key) {
        if (DIRTY_MESH_KEYS.add(key)) {
            DIRTY_MESHES.offer(key);
        }
    }
}
