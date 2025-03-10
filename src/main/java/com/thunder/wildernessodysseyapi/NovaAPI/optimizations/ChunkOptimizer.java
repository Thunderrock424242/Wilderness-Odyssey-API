package com.thunder.wildernessodysseyapi.NovaAPI.optimizations;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EventBusSubscriber
public class ChunkOptimizer {
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(4); // Background thread pool

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) return; // Skip if the server is overloaded

        ServerLevel world = event.getServer().overworld(); // Optimize only Overworld for now
        threadPool.submit(() -> optimizeChunkLoading(world));
    }

    private static void optimizeChunkLoading(ServerLevel world) {
        // Iterate over loaded chunks using ServerChunkCache's method
        for (ChunkAccess chunkAccess : world.getChunkSource().getLoadedChunksIterable()) {
            if (chunkAccess instanceof LevelChunk chunk) {
                optimizeChunk(chunk);
            }
        }
    }

    private static void optimizeChunk(LevelChunk chunk) {
        // Example: Optimize chunk updates to reduce lag
        chunk.setUnsaved(false); // Prevent unnecessary disk writes
        NovaAPI.LOGGER.debug("[Nova API] Optimized chunk at " + chunk.getPos().x + ", " + chunk.getPos().z);
    }
}