package com.thunder.wildernessodysseyapi.NovaAPI.chunk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class ChunkPreloadHandler {

    private static final int PRELOAD_RADIUS = 2; // Adjust radius as needed

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel world = player.serverLevel();
            preloadNearbyChunks(world, player.getX(), player.getZ());
        }
    }

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent event) {
        if (event.getLevel() instanceof ServerLevel serverWorld) {
            ChunkPreloader.processChunkQueue();
        }
    }

    private static void preloadNearbyChunks(ServerLevel world, double x, double z) {
        ChunkPos center = new ChunkPos((int) x >> 4, (int) z >> 4);

        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dz = -PRELOAD_RADIUS; dz <= PRELOAD_RADIUS; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                ChunkPreloader.requestChunkLoad(world, chunkX, chunkZ);
            }
        }

        LogUtil.getLogger().info("[NovaAPI] Requested chunk preloading near player at " + center);
    }
}