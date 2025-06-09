package com.thunder.wildernessodysseyapi.ModPackPatches;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Offloads CPU-intensive chunk save tasks to a background thread pool.
 * Provides a shutdown helper to cleanly terminate the executor.
 */
public class ChunkSaveOptimizer {

    /** Executor for background save tasks. */
    public static final ExecutorService SAVE_EXECUTOR = Executors.newFixedThreadPool(2);

    public ChunkSaveOptimizer() {
        // Register this instance so @SubscribeEvent methods fire
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * Called whenever a chunk is about to be saved.
     * Offloads heavy work (e.g., compression) to the background executor.
     */
    @SubscribeEvent
    public static void onChunkDataSave(ChunkDataEvent.Save event) {
        LevelChunk chunk = (LevelChunk) event.getChunk();
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        SAVE_EXECUTOR.submit(() -> {
            // 1) Perform heavy lifting here (custom compression, transforms, etc.)
            // 2) Then schedule final I/O on the server thread to avoid corruption:
            serverLevel.getServer().execute(() -> {
                // e.g. serverLevel.save(null, false, true);
            });
        });
    }

    /**
     * Shuts down the SAVE_EXECUTOR, waiting up to 30 seconds.
     * Invoke this from your main mod class’s @SubscribeEvent on ServerStoppingEvent.
     */
    public static void shutdownExecutor() {
        SAVE_EXECUTOR.shutdown();
        try {
            if (!SAVE_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                SAVE_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SAVE_EXECUTOR.shutdownNow();
        }
    }
}
