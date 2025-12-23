package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Small driver that exercises chunk save/load workloads under profiling tools like JFR or async-profiler.
 */
public final class ChunkWorkloadDriver {
    private static final int DEFAULT_CHUNK_COUNT = 48;
    private static final int DEFAULT_ROUNDS = 2;

    private ChunkWorkloadDriver() {
    }

    public static void main(String[] args) throws Exception {
        int chunkCount = parseArg(args, 0, DEFAULT_CHUNK_COUNT);
        int rounds = parseArg(args, 1, DEFAULT_ROUNDS);

        System.out.printf("Starting chunk workload: %d chunks x %d rounds%n", chunkCount, rounds);

        ChunkStreamingConfig.ChunkConfigValues config = new ChunkStreamingConfig.ChunkConfigValues(
                true,
                128,
                256,
                0,
                120,
                120,
                80,
                120,
                6,
                6
        );

        Path root = Paths.get("build/profile/chunks");
        DiskChunkStorageAdapter adapter = new DiskChunkStorageAdapter(root, config.compressionLevel(), config.compressionCodec());

        AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        ChunkStreamManager.initialize(config, adapter);

        List<ChunkPos> positions = IntStream.range(0, chunkCount)
                .mapToObj(i -> new ChunkPos(i, i))
                .collect(Collectors.toList());

        long gameTime = 0;
        for (int round = 0; round < rounds; round++) {
            System.out.printf("Round %d/%d: saving chunks%n", round + 1, rounds);
            Instant saveStart = Instant.now();
            for (ChunkPos pos : positions) {
                CompoundTag payload = ChunkNbtFixtures.sampleChunk(pos, pos.hashCode() + round, 6);
                ChunkStreamManager.scheduleSave(pos, payload, gameTime++);
            }
            waitForSaves(gameTime);
            System.out.printf("Save round finished in %d ms%n",
                    Duration.between(saveStart, Instant.now()).toMillis());

            System.out.printf("Round %d/%d: loading chunks%n", round + 1, rounds);
            Instant loadStart = Instant.now();
            AtomicLong loadGameTime = new AtomicLong(gameTime);
            List<CompletableFuture<ChunkLoadResult>> futures = positions.stream()
                    .map(pos -> ChunkStreamManager.requestChunk(pos, ChunkTicketType.PLAYER, loadGameTime.getAndIncrement()))
                    .toList();
            gameTime = loadGameTime.get();
            for (CompletableFuture<ChunkLoadResult> future : futures) {
                future.join();
            }
            System.out.printf("Load round finished in %d ms%n",
                    Duration.between(loadStart, Instant.now()).toMillis());
        }

        ChunkStreamStats stats = ChunkStreamManager.snapshot();
        System.out.printf("Final stats: tracked=%d, hot=%d, warm=%d, pendingSaves=%d, inFlightIo=%d%n",
                stats.trackedChunks(), stats.hotCached(), stats.warmCached(), stats.pendingSaves(), stats.inFlightIo());

        AsyncTaskManager.shutdown();
        ChunkStreamManager.shutdown();
    }

    private static void waitForSaves(long gameTime) throws InterruptedException {
        int attempts = 0;
        while (attempts < 400) {
            ChunkStreamManager.tick(gameTime++);
            ChunkStreamStats stats = ChunkStreamManager.snapshot();
            if (stats.pendingSaves() == 0 && stats.inFlightIo() == 0) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(5L);
            attempts++;
        }
    }

    private static int parseArg(String[] args, int index, int fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
