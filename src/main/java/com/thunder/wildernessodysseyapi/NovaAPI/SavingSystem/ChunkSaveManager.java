package com.thunder.wildernessodysseyapi.NovaAPI.SavingSystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

import java.util.Optional;
import java.util.concurrent.*;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;
import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class ChunkSaveManager {
    private static final ExecutorService CHUNK_SAVE_THREAD = Executors.newSingleThreadExecutor();
    private static final ConcurrentLinkedQueue<LevelChunk> chunkSaveQueue = new ConcurrentLinkedQueue<>();

    public static void queueChunkSave(LevelChunk chunk) {
        chunkSaveQueue.add(chunk);
    }

    public static void processChunkSaves() {
        while (!chunkSaveQueue.isEmpty()) {
            LevelChunk chunk = chunkSaveQueue.poll();
            if (chunk != null) {
                saveChunk(chunk);
            }
        }
    }
    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverWorld) {
            ChunkPos pos = event.getChunk().getPos();
            LevelChunk chunk = serverWorld.getChunk(pos.x, pos.z);

            if (chunk != null) {
                ChunkSaveManager.queueChunkSave(chunk);
                LOGGER.info("[NovaAPI] Queued chunk for async saving: " + pos);
            }
        }
    }

    private static void saveChunk(LevelChunk chunk) {
        try {
            ServerLevel world = (ServerLevel) chunk.getLevel();
            ServerChunkCache chunkCache = world.getChunkSource();
            ChunkMap chunkMap = chunkCache.chunkMap;

            ChunkPos chunkPos = chunk.getPos();

            // Asynchronously read the chunk data
            CompletableFuture<Optional<CompoundTag>> futureChunkData = chunkMap.read(chunkPos);

            // Wait for the read operation to complete and get the data
            Optional<CompoundTag> optionalTag = futureChunkData.get();
            CompoundTag chunkData = optionalTag.orElse(new CompoundTag()); // If no data, create new

            // Write the updated chunk data
            chunkMap.write(chunkPos, chunkData);

            LOGGER.info("[NovaAPI] Successfully saved chunk at " + chunkPos);
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.error("[NovaAPI] Error reading chunk data at " + chunk.getPos(), e);
            Thread.currentThread().interrupt(); // Restore interrupt flag
        } catch (Exception e) {
            LOGGER.error("[NovaAPI] Error saving chunk at " + chunk.getPos(), e);
        }
    }

    public static void shutdown() {
        CHUNK_SAVE_THREAD.shutdown();
    }
}