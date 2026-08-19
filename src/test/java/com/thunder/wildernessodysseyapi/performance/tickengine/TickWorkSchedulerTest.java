package com.thunder.wildernessodysseyapi.performance.tickengine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies tick-aware ordering, pressure gates, coalescing, capacity, and deferral. */
class TickWorkSchedulerTest {

    @Test
    void ordersPrioritiesAndDefersOptionalWorkAfterBudgetExhaustion() {
        TickWorkScheduler scheduler = scheduler();
        List<String> order = new ArrayList<>();
        scheduler.submit(TickTask.once("analytics", TickPriority.IDLE, 0L, () -> order.add("idle")));
        scheduler.submit(TickTask.once("weather", TickPriority.NORMAL, 0L, () -> order.add("normal")));
        scheduler.submit(TickTask.once("labs", TickPriority.GAMEPLAY, 0L, () -> order.add("gameplay")));

        TickWorkScheduler.ProcessingReport report = scheduler.process(
                1L, System.nanoTime() - 1L, TickPressure.RELAXED);

        assertEquals(List.of("gameplay"), order);
        assertEquals(1, report.processed());
        assertEquals(1, report.deferred());
        assertEquals(2, report.remaining());
    }

    @Test
    void overloadedPressureRunsOnlyCriticalAndGameplayTasks() {
        TickWorkScheduler scheduler = scheduler();
        AtomicInteger executions = new AtomicInteger();
        scheduler.submit(TickTask.once("weather", TickPriority.NORMAL, 0L, executions::incrementAndGet));
        scheduler.submit(TickTask.once("labs", TickPriority.GAMEPLAY, 0L, executions::incrementAndGet));

        TickWorkScheduler.ProcessingReport report = scheduler.process(1L, Long.MAX_VALUE, TickPressure.OVERLOADED);

        assertEquals(1, executions.get());
        assertEquals(1, report.remaining());
    }

    @Test
    void coalescesDuplicateKeysAndEnforcesCapacity() {
        TickEngineMetrics metrics = new TickEngineMetrics();
        TickWorkScheduler scheduler = new TickWorkScheduler(metrics);
        scheduler.configure(new TickWorkScheduler.Settings(true, 10, 1, 1));
        TickTask first = new TickTask("weather", TickPriority.NORMAL, 0L, 0L,
                "region-4", "", () -> TickTask.Result.COMPLETE);
        TickTask duplicate = new TickTask("weather", TickPriority.NORMAL, 0L, 0L,
                "region-4", "", () -> TickTask.Result.COMPLETE);

        assertTrue(scheduler.submit(first));
        assertTrue(scheduler.submit(duplicate));
        assertFalse(scheduler.submit(TickTask.once("water", TickPriority.NORMAL, 0L, () -> { })));
        assertEquals(1, scheduler.queuedTasks());
    }

    @Test
    void retainsCooperativeTaskUntilCompleteAndDropsStaleTask() {
        TickWorkScheduler scheduler = scheduler();
        AtomicInteger attempts = new AtomicInteger();
        scheduler.submit(new TickTask("weather", TickPriority.BACKGROUND, 0L, 0L, "", "", () ->
                attempts.incrementAndGet() == 1 ? TickTask.Result.DEFER : TickTask.Result.COMPLETE));
        scheduler.submit(new TickTask("analytics", TickPriority.IDLE, 0L, 2L, "", "",
                () -> TickTask.Result.COMPLETE));

        TickWorkScheduler.ProcessingReport first = scheduler.process(1L, Long.MAX_VALUE, TickPressure.HIGH);
        TickWorkScheduler.ProcessingReport second = scheduler.process(3L, Long.MAX_VALUE, TickPressure.RELAXED);

        assertEquals(1, first.deferred());
        assertEquals(1, second.stale());
        assertEquals(0, second.remaining());
        assertEquals(2, attempts.get());
    }

    private static TickWorkScheduler scheduler() {
        TickWorkScheduler scheduler = new TickWorkScheduler(new TickEngineMetrics());
        scheduler.configure(new TickWorkScheduler.Settings(true, 64, 128, 128));
        return scheduler;
    }
}
