package com.thunder.wildernessodysseyapi.performance.background;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded workers, failure isolation, and the worker-to-server application handoff. */
class AsyncComputeManagerTest {
    private AsyncComputeManager manager;

    @AfterEach
    void shutdownManager() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void boundsActiveAndQueuedWorkerJobs() throws Exception {
        manager = new AsyncComputeManager(new BackgroundMetrics());
        manager.initialize(new AsyncComputeManager.Settings(true, 1, 1));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        assertTrue(manager.submitWithoutResult("weather", 1, ignored -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
        }));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(manager.submitWithoutResult("weather", 2, ignored -> { }));
        assertFalse(manager.submitWithoutResult("weather", 3, ignored -> { }));

        AsyncComputeManager.Snapshot snapshot = manager.snapshot();
        assertEquals(1, snapshot.workerThreads());
        assertTrue(snapshot.activeJobs() <= 1);
        assertTrue(snapshot.workerQueueSize() <= 1);
        release.countDown();
    }

    @Test
    void failedComputationIsCountedWithoutEscapingWorker() throws Exception {
        BackgroundMetrics metrics = new BackgroundMetrics();
        manager = new AsyncComputeManager(metrics);
        manager.initialize(new AsyncComputeManager.Settings(true, 1, 4));
        manager.submitWithoutResult("weather", 1, ignored -> {
            throw new IllegalStateException("expected");
        });

        await(() -> metrics.snapshot().failedAsyncJobs() == 1L && manager.snapshot().activeJobs() == 0);
        assertEquals(1L, metrics.snapshot().failedAsyncJobs());
    }

    @Test
    void computedResultWaitsForExplicitServerThreadDrain() throws Exception {
        manager = new AsyncComputeManager(new BackgroundMetrics());
        manager.initialize(new AsyncComputeManager.Settings(true, 1, 4));
        AtomicInteger applied = new AtomicInteger();
        assertTrue(manager.submit("weather", 21, value -> value * 2,
                (server, result) -> applied.set(result)));

        await(() -> manager.snapshot().applyQueueSize() == 1);
        assertEquals(0, applied.get());
        Method drain = AsyncComputeManager.class.getDeclaredMethod(
                "drainResultsForTests", int.class, long.class);
        drain.setAccessible(true);
        assertEquals(1, drain.invoke(manager, 1, Long.MAX_VALUE));
        assertEquals(42, applied.get());
    }

    @Test
    void replacedWorkerPoolCannotPublishAStaleServerResult() throws Exception {
        manager = new AsyncComputeManager(new BackgroundMetrics());
        manager.initialize(new AsyncComputeManager.Settings(true, 1, 4));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        assertTrue(manager.submit("weather", 7, value -> {
            started.countDown();
            boolean waiting = true;
            while (waiting) {
                try {
                    release.await();
                    waiting = false;
                } catch (InterruptedException ignored) {
                    // Deliberately model a calculation that does not cooperate
                    // with shutdown interruption so pool identity is exercised.
                }
            }
            return value;
        }, (server, result) -> { }));
        assertTrue(started.await(2, TimeUnit.SECONDS));

        manager.initialize(new AsyncComputeManager.Settings(true, 1, 4));
        release.countDown();
        Thread.sleep(25L);

        Method drain = AsyncComputeManager.class.getDeclaredMethod(
                "drainResultsForTests", int.class, long.class);
        drain.setAccessible(true);
        assertEquals(0, drain.invoke(manager, 1, Long.MAX_VALUE));
    }

    private static void await(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (!check.done() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(check.done());
    }

    @FunctionalInterface
    private interface Check {
        boolean done();
    }
}
