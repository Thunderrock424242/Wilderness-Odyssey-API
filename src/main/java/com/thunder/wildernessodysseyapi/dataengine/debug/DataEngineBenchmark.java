package com.thunder.wildernessodysseyapi.dataengine.debug;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.cache.DataCache;
import com.thunder.wildernessodysseyapi.dataengine.network.DataDelta;
import com.thunder.wildernessodysseyapi.dataengine.network.DataPacketBatch;
import com.thunder.wildernessodysseyapi.dataengine.queue.DataUpdateQueue;
import com.thunder.wildernessodysseyapi.dataengine.queue.QueuedUpdate;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Small isolated synthetic workload used by the permission-gated debug command. */
public final class DataEngineBenchmark {
    private static final ResourceLocation BENCHMARK_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "data_engine_benchmark"
    );
    private static final int SUBMISSIONS = 512;
    private static final int UNIQUE_KEYS = 64;
    private static final int SYNTHETIC_BATCH_ENTRIES = 16;

    private DataEngineBenchmark() {
    }

    /** Runs a bounded in-memory workload; it does not enqueue gameplay or network state. */
    public static Result run() {
        long startedNanos = System.nanoTime();
        DataUpdateQueue queue = new DataUpdateQueue(1_024);
        int[] finalValues = new int[UNIQUE_KEYS];
        int coalesced = 0;
        for (int index = 0; index < SUBMISSIONS; index++) {
            int key = index % UNIQUE_KEYS;
            int value = index;
            DataUpdateQueue.SubmissionResult result = queue.submit(QueuedUpdate.dirty(
                    BENCHMARK_ID,
                    key,
                    UpdatePriority.NORMAL,
                    0L,
                    () -> finalValues[key] = value
            ));
            if (result == DataUpdateQueue.SubmissionResult.COALESCED) {
                coalesced++;
            }
        }
        AtomicInteger processed = new AtomicInteger();
        queue.processAvailable(50_000_000L, update -> {
            update.run();
            processed.incrementAndGet();
        });

        AtomicInteger hits = new AtomicInteger();
        AtomicInteger misses = new AtomicInteger();
        DataCache<Integer, Integer> cache = new DataCache<>(
                128,
                hits::incrementAndGet,
                misses::incrementAndGet,
                ignored -> { }
        );
        for (int key = 0; key < UNIQUE_KEYS; key++) {
            cache.put(key, finalValues[key]);
        }
        for (int index = 0; index < SUBMISSIONS; index++) {
            cache.get(index % UNIQUE_KEYS, 0L);
        }
        for (int key = UNIQUE_KEYS; key < UNIQUE_KEYS + 16; key++) {
            cache.get(key, 0L);
        }

        // Exercise the same bounded packet container without sending network
        // traffic or needing a live player connection.
        int batchCount = createSyntheticBatches(finalValues);
        long elapsedNanos = System.nanoTime() - startedNanos;
        return new Result(
                SUBMISSIONS,
                coalesced,
                processed.get(),
                hits.get(),
                misses.get(),
                batchCount,
                elapsedNanos
        );
    }

    private static int createSyntheticBatches(int[] finalValues) {
        List<DataDelta> pending = new ArrayList<>(SYNTHETIC_BATCH_ENTRIES);
        int batches = 0;
        for (int key = 0; key < finalValues.length; key++) {
            int value = finalValues[key];
            pending.add(new DataDelta(
                    BENCHMARK_ID,
                    key,
                    1L,
                    UpdatePriority.NORMAL,
                    new byte[]{
                            (byte) (value >>> 24),
                            (byte) (value >>> 16),
                            (byte) (value >>> 8),
                            (byte) value
                    }
            ));
            if (pending.size() == SYNTHETIC_BATCH_ENTRIES) {
                new DataPacketBatch(pending);
                pending.clear();
                batches++;
            }
        }
        if (!pending.isEmpty()) {
            new DataPacketBatch(pending);
            batches++;
        }
        return batches;
    }

    /** Actual observations from one bounded benchmark invocation. */
    public record Result(
            int submitted,
            int coalesced,
            int processed,
            int cacheHits,
            int cacheMisses,
            int batchCount,
            long elapsedNanos
    ) {
        public double cacheHitRate() {
            int total = cacheHits + cacheMisses;
            return total == 0 ? 0.0D : (double) cacheHits / total;
        }
    }
}
