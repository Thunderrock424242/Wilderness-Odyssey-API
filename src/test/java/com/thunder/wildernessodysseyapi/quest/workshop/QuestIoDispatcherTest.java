package com.thunder.wildernessodysseyapi.quest.workshop;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QuestIoDispatcherTest {
    @Test
    void saturatedSharedWorkersRetainBoundedWorkWithoutRunningItOnTheSubmittingThread() throws Exception {
        var backend = new SharedBackend();
        var io = new QuestIoDispatcher(backend);
        var executed = new ArrayList<Integer>();
        for (int index = 0; index < 128; index++) {
            int value = index;
            assertTrue(io.trySubmit(() -> executed.add(value)));
        }
        assertFalse(io.trySubmit(() -> fail("Rejected work ran.")));
        assertTrue(executed.isEmpty());
        backend.accept = true;
        io.pump();
        var worker = new Thread(backend.task.get(), "test-quest-worker");
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertEquals(java.util.stream.IntStream.range(0, 128).boxed().toList(), executed);
        io.close();
        assertFalse(io.trySubmit(() -> fail("Closed work ran.")));
    }

    @Test
    void disabledModeUsesOneWorkerAndCloseCancelsPendingWorkAfterTheActiveTask() throws Exception {
        var backend = new SharedBackend();
        backend.enabled = false;
        var io = new QuestIoDispatcher(backend);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var thread = new AtomicReference<String>();
        var active = io.submit(() -> {
            thread.set(Thread.currentThread().getName());
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test worker was not released.");
            return 7;
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        var pending = io.submit(() -> { fail("Shutdown work executed."); return 0; });
        io.close();
        release.countDown();
        assertEquals(7, active.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.toCompletableFuture().get(5, TimeUnit.SECONDS));
        io.termination().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(thread.get().startsWith("WO-Quest-IO-"));
        assertNull(backend.task.get(), "Disabled mode must not submit to shared workers.");
    }

    static final class SharedBackend implements QuestIoDispatcher.Backend {
        boolean enabled = true;
        boolean accept;
        final AtomicReference<Runnable> task = new AtomicReference<>();

        @Override public boolean enabled() { return enabled; }
        @Override public boolean submit(Runnable work) {
            if (!accept) return false;
            assertTrue(task.compareAndSet(null, work), "Only one drain may be scheduled.");
            return true;
        }
    }
}
