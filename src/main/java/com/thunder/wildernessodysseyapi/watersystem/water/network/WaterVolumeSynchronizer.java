package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Publishes sparse canonical water attachments around each player. */
public final class WaterVolumeSynchronizer {

    private static final int TRACKING_RADIUS_CHUNKS = 4;
    private static final Map<ServerPlayer, PlayerSyncState> PLAYER_REVISIONS = new WeakHashMap<>();

    private WaterVolumeSynchronizer() {
    }

    /** Sends loaded canonical chunks within the bounded tracking radius. */
    public static void syncLevel(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            PlayerSyncState syncState = PLAYER_REVISIONS.computeIfAbsent(
                    player,
                    ignored -> new PlayerSyncState(level.dimension())
            );
            if (!syncState.dimension.equals(level.dimension())) {
                syncState = new PlayerSyncState(level.dimension());
                PLAYER_REVISIONS.put(player, syncState);
            }
            int centerChunkX = player.chunkPosition().x;
            int centerChunkZ = player.chunkPosition().z;
            Set<Long> retainedChunks = new HashSet<>((TRACKING_RADIUS_CHUNKS * 2 + 1)
                    * (TRACKING_RADIUS_CHUNKS * 2 + 1));
            for (int offsetX = -TRACKING_RADIUS_CHUNKS; offsetX <= TRACKING_RADIUS_CHUNKS; offsetX++) {
                for (int offsetZ = -TRACKING_RADIUS_CHUNKS; offsetZ <= TRACKING_RADIUS_CHUNKS; offsetZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(
                            centerChunkX + offsetX,
                            centerChunkZ + offsetZ
                    );
                    if (chunk == null) {
                        continue;
                    }
                    long chunkKey = chunk.getPos().toLong();
                    retainedChunks.add(chunkKey);
                    var existingVolume = chunk.getExistingData(ModAttachments.WATER_VOLUME);
                    if (existingVolume.isEmpty()) {
                        continue;
                    }
                    var volume = existingVolume.get();
                    if (syncState.revisions.getOrDefault(chunkKey, Long.MIN_VALUE) == volume.revision()) {
                        continue;
                    }
                    // Large sparse chunks are paged so one exact revision never
                    // exceeds the custom-payload safety bound.
                    for (WaterVolumeChunkPayload page : WaterVolumeChunkPayload.pagesFromChunk(chunk, volume)) {
                        PacketDistributor.sendToPlayer(player, page);
                    }
                    syncState.revisions.put(chunkKey, volume.revision());
                }
            }

            // A client chunk receives a fresh attachment when it is loaded
            // again. Forget revisions outside the active window so returning to
            // an unchanged chunk still resends its canonical snapshot, while
            // also bounding this map during long-distance exploration.
            syncState.retainLoadedChunks(retainedChunks);
        }
    }

    /**
     * Forgets a chunk revision after Minecraft stops tracking it for a player.
     *
     * <p>The next watch creates a new client chunk attachment, so an unchanged
     * server revision must be sent again instead of being mistaken for state the
     * new client chunk already owns.</p>
     */
    public static void forgetChunk(ServerPlayer player, ChunkPos pos) {
        PlayerSyncState syncState = PLAYER_REVISIONS.get(player);
        if (syncState != null) {
            syncState.revisions.remove(pos.toLong());
        }
    }

    private static final class PlayerSyncState {
        private final ResourceKey<Level> dimension;
        private final Map<Long, Long> revisions = new HashMap<>();

        private PlayerSyncState(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        private void retainLoadedChunks(Set<Long> retainedChunks) {
            revisions.keySet().retainAll(retainedChunks);
        }
    }
}
