package com.thunder.wildernessodysseyapi.ModPackPatches;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChunkSaveOptimizer {

    // A small executor for background tasks.
    private static final ExecutorService SAVE_EXECUTOR = Executors.newFixedThreadPool(2);

    public ChunkSaveOptimizer() {
        // Register this class on the Forge event bus to receive events.
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * Called whenever a chunk is about to be saved.
     * We offload CPU-intensive tasks (e.g., compression) to a background thread.
     */
    @SubscribeEvent
    public void onChunkDataSave(ChunkDataEvent.Save event) {
        LevelChunk chunk = (LevelChunk) event.getChunk();
        // Ensure we're on a server world (not client).
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // If you need the chunk's NBT data, you can get it here:
        // CompoundTag chunkData = event.getData();

        // Schedule heavy compression or processing on a background thread.
        SAVE_EXECUTOR.submit(() -> {
            // 1) Perform heavy lifting (custom compression, data transformations, etc.).
            // 2) Then schedule final disk I/O on the server thread to avoid file corruption.
            serverLevel.getServer().execute(() -> {
                // The final write:
                // - Typically, Minecraft already wrote the chunk data,
                //   but if you’re intercepting it to do custom writes, do it here.
                // - Or forcibly flush chunk data if needed:
                //   serverLevel.save(null, false, true);
            });
        });
    }

    /**
     * When the server is stopping (dedicated or integrated server), shut down our executor.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SAVE_EXECUTOR.shutdown();
        try {
            // Wait for any queued saves to finish (up to 30 seconds).
            if (!SAVE_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                SAVE_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SAVE_EXECUTOR.shutdownNow();
        }
    }
}