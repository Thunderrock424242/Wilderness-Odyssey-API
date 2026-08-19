package com.thunder.wildernessodysseyapi.performance.background;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded analytics batching, delayed submission, and saturation recovery. */
class AnalyticsBatcherTest {
    private AsyncComputeManager asyncManager;

    @AfterEach
    void shutdownWorkers() {
        if (asyncManager != null) {
            asyncManager.shutdown();
        }
    }

    @Test
    void submitsImmutableBatchAfterMaximumDelay() throws Exception {
        asyncManager = new AsyncComputeManager(new BackgroundMetrics());
        asyncManager.initialize(new AsyncComputeManager.Settings(true, 1, 4));
        AnalyticsBatcher batcher = new AnalyticsBatcher(new BackgroundMetrics());
        batcher.configure(new AnalyticsBatcher.Settings(true, 4, 5, 8));
        CountDownLatch processed = new CountDownLatch(1);
        AtomicReference<List<String>> received = new AtomicReference<>();
        AnalyticsBatcher.Channel<String> channel = batcher.registerChannel("analytics", "events", events -> {
            received.set(events);
            processed.countDown();
        });
        assertTrue(batcher.queue(channel, "camp-entered", 10L));

        assertEquals(0, batcher.flushDue(14L, asyncManager, Long.MAX_VALUE));
        assertEquals(1, batcher.flushDue(15L, asyncManager, Long.MAX_VALUE));
        assertTrue(processed.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("camp-entered"), received.get());
        assertEquals(0, batcher.queuedEvents());
    }

    @Test
    void rejectsEventsBeyondGlobalCapacity() {
        AnalyticsBatcher batcher = new AnalyticsBatcher(new BackgroundMetrics());
        batcher.configure(new AnalyticsBatcher.Settings(true, 4, 5, 1));
        AnalyticsBatcher.Channel<Integer> channel = batcher.registerChannel("analytics", "events", ignored -> { });

        assertTrue(batcher.queue(channel, 1, 0L));
        assertFalse(batcher.queue(channel, 2, 0L));
        assertEquals(1, batcher.queuedEvents());
    }

    @Test
    void requeuesDueBatchWhenWorkerSubmissionIsRejected() {
        asyncManager = new AsyncComputeManager(new BackgroundMetrics());
        asyncManager.initialize(new AsyncComputeManager.Settings(false, 1, 1));
        AnalyticsBatcher batcher = new AnalyticsBatcher(new BackgroundMetrics());
        batcher.configure(new AnalyticsBatcher.Settings(true, 1, 1, 4));
        AnalyticsBatcher.Channel<Integer> channel = batcher.registerChannel("analytics", "events", ignored -> { });
        assertTrue(batcher.queue(channel, 1, 0L));

        assertEquals(0, batcher.flushDue(1L, asyncManager, Long.MAX_VALUE));
        assertEquals(1, batcher.queuedEvents());
    }
}
