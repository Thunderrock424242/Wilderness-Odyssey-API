package com.thunder.wildernessodysseyapi.performance.coordinator;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DisposableWorkTrackerTest {
    @Test
    void pressureRejectsWithoutCallerExecution() {
        var tracker = new DisposableWorkTracker(2);
        var called = new AtomicBoolean();
        assertFalse(tracker.submit(Runnable::run, () -> called.set(true), false));
        assertFalse(called.get());
    }

    @Test
    void shutdownInterruptsOnlyExplicitlyDisposableWorkAndRejectsLateProducers() throws Exception {
        var pool = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
        var tracker = new DisposableWorkTracker(2);
        var optionalStarted = new CountDownLatch(1);
        var optionalInterrupted = new CountDownLatch(1);
        var requiredStarted = new CountDownLatch(1);
        var releaseRequired = new CountDownLatch(1);
        var requiredInterrupted = new AtomicBoolean();
        try {
            pool.execute(() -> {
                requiredStarted.countDown();
                try { releaseRequired.await(); } catch (InterruptedException exception) { requiredInterrupted.set(true); }
            });
            assertTrue(tracker.submit(pool, () -> {
                optionalStarted.countDown();
                try { new CountDownLatch(1).await(); } catch (InterruptedException exception) { optionalInterrupted.countDown(); }
            }, true));
            assertTrue(requiredStarted.await(2, TimeUnit.SECONDS));
            assertTrue(optionalStarted.await(2, TimeUnit.SECONDS));
            tracker.stop();
            assertTrue(optionalInterrupted.await(2, TimeUnit.SECONDS));
            assertFalse(requiredInterrupted.get());
            assertFalse(tracker.submit(pool, () -> fail("late producer ran"), true));
        } finally { releaseRequired.countDown(); pool.shutdownNow(); }
    }

    @Test
    void capacityAndExecutorRejectionReleaseAdmission() {
        var queued = new java.util.ArrayList<Runnable>();
        var tracker = new DisposableWorkTracker(1);
        assertTrue(tracker.submit(queued::add, () -> { }, true));
        assertFalse(tracker.submit(queued::add, () -> { }, true));
        queued.removeFirst().run();
        assertFalse(tracker.submit(task -> { throw new java.util.concurrent.RejectedExecutionException(); }, () -> { }, true));
        assertTrue(tracker.submit(queued::add, () -> { }, true));
        tracker.stop();
        queued.removeFirst().run();
        assertEquals(0, tracker.snapshot().inFlight());
    }
}
