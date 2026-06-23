package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
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
            for (int offsetX = -TRACKING_RADIUS_CHUNKS; offsetX <= TRACKING_RADIUS_CHUNKS; offsetX++) {
                for (int offsetZ = -TRACKING_RADIUS_CHUNKS; offsetZ <= TRACKING_RADIUS_CHUNKS; offsetZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(
                            centerChunkX + offsetX,
                            centerChunkZ + offsetZ
                    );
                    if (chunk == null) {
                        continue;
                    }
                    var existingVolume = chunk.getExistingData(ModAttachments.WATER_VOLUME);
                    if (existingVolume.isEmpty()) {
                        continue;
                    }
                    var volume = existingVolume.get();
                    long chunkKey = chunk.getPos().toLong();
                    if (syncState.revisions.getOrDefault(chunkKey, Long.MIN_VALUE) == volume.revision()) {
                        continue;
                    }
                    PacketDistributor.sendToPlayer(player, WaterVolumeChunkPayload.fromChunk(chunk, volume));
                    syncState.revisions.put(chunkKey, volume.revision());
                }
            }
        }
    }

    private static final class PlayerSyncState {
        private final ResourceKey<Level> dimension;
        private final Map<Long, Long> revisions = new HashMap<>();

        private PlayerSyncState(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }
}
