package com.thunder.wildernessodysseyapi.NovaAPI.optimizations;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EventBusSubscriber
public class ChunkOptimizer {
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(4); // Background thread pool

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ServerLevel world = event.getServer().overworld(); // Optimize only Overworld for now

        threadPool.submit(() -> optimizeChunkLoading(world));
    }

    private static void optimizeChunkLoading(ServerLevel world) {
        world.getChunkSource().chunkMap.getChunks().forEach(chunkHolder -> {
            LevelChunk chunk = chunkHolder.getChunkNow();
            if (chunk != null) {
                optimizeChunk(chunk);
            }
        });
    }

    private static void optimizeChunk(LevelChunk chunk) {
        // Example: Optimize chunk updates to reduce lag
        chunk.setUnsaved(false); // Prevent unnecessary disk writes
        NovaAPI.LOGGER.debug("[Nova API] Optimized chunk at " + chunk.getPos().x + ", " + chunk.getPos().z);
    }
}