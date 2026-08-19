package com.thunder.wildernessodysseyapi.dataengine.async;

import com.thunder.wildernessodysseyapi.dataengine.metrics.DataEngineMetrics;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataWorkerPoolTest {
    private static final ResourceLocation SYSTEM = ResourceLocation.fromNamespaceAndPath("test", "async");

    @Test
    void workerResultReturnsThroughValidatedServerApplyStage() throws Exception {
        CompletedTaskQueue completed = new CompletedTaskQueue(8);
        DataEngineMetrics metrics = new DataEngineMetrics(true);
        AtomicInteger applied = new AtomicInteger();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            DataWorkerPool pool = new DataWorkerPool(
                    completed,
                    metrics,
                    4,
                    (label, work) -> {
                        executor.execute(work);
                        return true;
                    }
            );

            assertTrue(pool.submit(SYSTEM, "test", UpdatePriority.NORMAL, true, new AsyncDataTask<Integer>() {
                @Override
                public Integer compute() {
                    return 21 * 2;
                }

                @Override
                public boolean isStillValid(Integer result) {
                    return result == 42;
                }

                @Override
                public void apply(Integer result) {
                    applied.set(result);
                }
            }));

            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            CompletedTaskQueue.CompletedTask task;
            do {
                task = completed.pollNonCritical();
                if (task == null) {
                    Thread.onSpinWait();
                }
            } while (task == null && System.nanoTime() < deadline);

            assertNotNull(task);
            assertEquals(0, applied.get());
            task.run();
            assertEquals(42, applied.get());
            assertEquals(1L, metrics.snapshot().asyncTasksCompleted());
        }
    }
}
