package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkStreamingIntegrationTest {
    private static final int CHUNK_COUNT = 32;
    private static final long SAVE_AVG_TARGET_MS = 200L;
    private static final long LOAD_AVG_TARGET_MS = 200L;

    @AfterEach
    void tearDown() {
        ChunkStreamManager.shutdown();
        AsyncTaskManager.shutdown();
    }

    @Test
    void bulkSaveAndLoadStayWithinLatencyBudget() throws Exception {
        Path temp = Files.createTempDirectory("chunk-stream-test");
        ChunkStreamingConfig.ChunkConfigValues config = new ChunkStreamingConfig.ChunkConfigValues(
                true,
                64,
                64,
                0,
                80,
                80,
                80,
                80,
                4,
                6
        );
        DiskChunkStorageAdapter adapter = new DiskChunkStorageAdapter(temp, config.compressionLevel());

        AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        ChunkStreamManager.initialize(config, adapter);

        List<ChunkPos> positions = IntStream.range(0, CHUNK_COUNT)
                .mapToObj(i -> new ChunkPos(i, -i))
                .collect(Collectors.toList());

        long gameTime = 0;
        long saveStart = System.nanoTime();
        for (ChunkPos pos : positions) {
            CompoundTag payload = ChunkNbtFixtures.sampleChunk(pos, pos.hashCode(), 4);
            ChunkStreamManager.scheduleSave(pos, payload, gameTime++);
        }

        waitForPendingSaves(gameTime);
        long saveDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - saveStart);
        double avgSaveMs = saveDurationMs / (double) CHUNK_COUNT;
        assertTrue(avgSaveMs < SAVE_AVG_TARGET_MS,
                "Average save latency should be under " + SAVE_AVG_TARGET_MS + "ms");

        Map<ChunkPos, Long> loadStartTimes = new HashMap<>();
        List<CompletableFuture<ChunkLoadResult>> futures = new ArrayList<>();
        for (ChunkPos pos : positions) {
            loadStartTimes.put(pos, System.nanoTime());
            futures.add(ChunkStreamManager.requestChunk(pos, ChunkTicketType.PLAYER, gameTime++));
        }

        List<Long> loadDurations = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            ChunkPos pos = positions.get(i);
            ChunkLoadResult result = futures.get(i).join();
            assertNotNull(result.payload(), "Chunk payload should be present for " + pos);
            assertFalse(result.payload().isEmpty(), "Chunk payload should not be empty");
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - loadStartTimes.get(pos));
            loadDurations.add(duration);
        }

        double averageLoadMs = loadDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        assertTrue(averageLoadMs < LOAD_AVG_TARGET_MS,
                "Average load latency should be under " + LOAD_AVG_TARGET_MS + "ms");
    }

    private void waitForPendingSaves(long gameTime) throws InterruptedException, IOException {
        int attempts = 0;
        while (attempts < 200) {
            ChunkStreamManager.tick(gameTime++);
            ChunkStreamStats stats = ChunkStreamManager.snapshot();
            if (stats.pendingSaves() == 0 && stats.inFlightIo() == 0) {
                return;
            }
            Thread.sleep(10L);
            attempts++;
        }
        throw new IOException("Timed out waiting for chunk saves to flush.");
    }
}
