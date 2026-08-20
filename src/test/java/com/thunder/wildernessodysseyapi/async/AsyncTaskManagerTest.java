package com.thunder.wildernessodysseyapi.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncTaskManagerTest {

    @AfterEach
    void stopExecutors() {
        AsyncTaskManager.shutdown();
    }

    @Test
    void saturatedExecutorRejectsWithoutRunningPayloadOnCaller() throws Exception {
        AsyncTaskManager.initialize(config(1, 1));
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        assertTrue(AsyncTaskManager.trySubmitCpuWork("blocking", () -> {
            firstStarted.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        assertTrue(AsyncTaskManager.trySubmitCpuWork("queued", () -> { }));

        AtomicBoolean payloadRan = new AtomicBoolean(false);
        boolean accepted = AsyncTaskManager.submitCpuTask("rejected", () -> {
            payloadRan.set(true);
            return Optional.empty();
        }).get();

        assertFalse(accepted);
        assertFalse(payloadRan.get());
        assertEquals(0, AsyncTaskManager.snapshot().callerRunsEvents());
        assertTrue(AsyncTaskManager.snapshot().rejectedTasks() >= 1);
        releaseFirst.countDown();
    }

    @Test
    void liveReloadPreservesAcceptedMainThreadCallbacks() throws Exception {
        AsyncTaskManager.initialize(config(1, 4));

        assertTrue(AsyncTaskManager.submitCpuTask(
                "callback",
                () -> Optional.of(server -> { })
        ).get());
        assertEquals(1, AsyncTaskManager.snapshot().mainThreadBacklog());

        AsyncTaskManager.reload(config(2, 8));

        assertEquals(1, AsyncTaskManager.snapshot().mainThreadBacklog());
        assertEquals(2, AsyncTaskManager.snapshot().configuredThreads());
    }

    @Test
    void staleResultCannotCrossAServerLifecycleBoundary() {
        AsyncTaskManager.initialize(config(1, 4));
        long oldGeneration = AsyncTaskManager.lifecycleGeneration();
        AtomicBoolean applied = new AtomicBoolean(false);

        AsyncTaskManager.shutdown();

        assertFalse(AsyncTaskManager.enqueueMainThreadTask(
                "old-server-result",
                server -> applied.set(true),
                oldGeneration
        ));
        assertFalse(applied.get());
        assertEquals(0, AsyncTaskManager.snapshot().mainThreadBacklog());
    }

    private static AsyncThreadingConfig.AsyncConfigValues config(int threads, int queueSize) {
        return new AsyncThreadingConfig.AsyncConfigValues(true, threads, queueSize, 8, 0, false);
    }
}
