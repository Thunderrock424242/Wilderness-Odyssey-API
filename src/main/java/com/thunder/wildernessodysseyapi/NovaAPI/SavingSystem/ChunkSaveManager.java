package com.thunder.wildernessodysseyapi.NovaAPI.SavingSystem;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@EventBusSubscriber(modid = "wildernessodysseyapi")
public class ChunkSaveManager {
    private static final ExecutorService CHUNK_SAVE_THREAD = Executors.newSingleThreadExecutor();
    private static final LinkedBlockingQueue<ChunkDataEvent.Save> CHUNK_QUEUE = new LinkedBlockingQueue<>();

    static {
        CHUNK_SAVE_THREAD.execute(ChunkSaveManager::processChunkSaves);
    }

    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        CHUNK_QUEUE.offer(event);
        event.setCanceled(true);
    }
}