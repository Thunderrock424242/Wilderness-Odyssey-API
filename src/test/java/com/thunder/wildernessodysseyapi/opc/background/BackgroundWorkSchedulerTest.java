package com.thunder.wildernessodysseyapi.opc.background;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies ordering, queue bounds, cooperative deferral, and time-budget stopping. */
class BackgroundWorkSchedulerTest {

    @Test
    void processesHigherPrioritiesBeforeIdleWork() {
        BackgroundWorkScheduler scheduler = schedulerWithDefaults();
        List<String> order = new ArrayList<>();
        scheduler.submit(BackgroundTask.once("analytics", WorkPriority.IDLE, 0L, () -> order.add("idle")));
        scheduler.submit(BackgroundTask.once("network", WorkPriority.GAMEPLAY, 0L, () -> order.add("gameplay")));
        scheduler.submit(BackgroundTask.once("safety", WorkPriority.CRITICAL, 0L, () -> order.add("critical")));

        scheduler.process(1L, Long.MAX_VALUE);

        assertEquals(List.of("critical", "gameplay", "idle"), order);
    }

    @Test
    void liveServerAllowanceStopsEvenCriticalOptionalTasks() {
        BackgroundWorkScheduler scheduler = schedulerWithDefaults();
        AtomicBoolean serverHasTime = new AtomicBoolean(true);
        AtomicInteger executions = new AtomicInteger();
        scheduler.submit(BackgroundTask.once("safety", WorkPriority.CRITICAL, 0L, () -> {
            executions.incrementAndGet();
            serverHasTime.set(false);
        }));
        scheduler.submit(BackgroundTask.once("safety", WorkPriority.CRITICAL, 0L, executions::incrementAndGet));

        BackgroundWorkScheduler.ProcessingReport report = scheduler.process(
                1L,
                Long.MAX_VALUE,
                serverHasTime::get
        );

        assertEquals(1, executions.get());
        assertEquals(1, report.processed());
        assertEquals(1, report.remaining());
    }

    @Test
    void enforcesGlobalAndPerSubsystemQueueLimits() {
        BackgroundWorkScheduler scheduler = schedulerWithDefaults();
        scheduler.configure(new BackgroundWorkScheduler.Settings(true, 10, Long.MAX_VALUE, 2, 1));

        assertTrue(scheduler.submit(BackgroundTask.once("weather", WorkPriority.NORMAL, 0L, () -> { })));
        assertFalse(scheduler.submit(BackgroundTask.once("weather", WorkPriority.NORMAL, 0L, () -> { })));
        assertTrue(scheduler.submit(BackgroundTask.once("water", WorkPriority.NORMAL, 0L, () -> { })));
        assertFalse(scheduler.submit(BackgroundTask.once("labs", WorkPriority.NORMAL, 0L, () -> { })));
        assertEquals(2, scheduler.queuedTasks());
    }

    @Test
    void deferredTaskRetainsCapacityUntilItCompletes() {
        BackgroundWorkScheduler scheduler = schedulerWithDefaults();
        AtomicInteger attempts = new AtomicInteger();
        scheduler.submit(new BackgroundTask("weather", WorkPriority.BACKGROUND, 0L, 0L, "region-4", () ->
                attempts.incrementAndGet() == 1
                        ? BackgroundTask.Result.DEFER
                        : BackgroundTask.Result.COMPLETE));

        BackgroundWorkScheduler.ProcessingReport first = scheduler.process(1L, Long.MAX_VALUE);
        BackgroundWorkScheduler.ProcessingReport second = scheduler.process(2L, Long.MAX_VALUE);

        assertEquals(1, first.deferred());
        assertEquals(1, first.remaining());
        assertEquals(0, second.remaining());
        assertEquals(2, attempts.get());
    }

    @Test
    void stopsAfterCurrentTaskConsumesBudget() {
        AtomicLong clock = new AtomicLong();
        BackgroundMetrics metrics = new BackgroundMetrics();
        BackgroundWorkScheduler scheduler = new BackgroundWorkScheduler(metrics, clock::get);
        scheduler.configure(new BackgroundWorkScheduler.Settings(true, 10, 5L, 10, 10));
        scheduler.submit(BackgroundTask.once("weather", WorkPriority.NORMAL, 0L, () -> clock.set(6L)));
        scheduler.submit(BackgroundTask.once("weather", WorkPriority.NORMAL, 0L, () -> { }));

        BackgroundWorkScheduler.ProcessingReport report = scheduler.process(1L, 5L);

        assertEquals(1, report.processed());
        assertEquals(1, report.deferred());
        assertEquals(1, report.remaining());
    }

    @Test
    void externalControlCanSuspendBackgroundAndIdleQueues() {
        BackgroundWorkScheduler scheduler = schedulerWithDefaults();
        scheduler.submit(BackgroundTask.once("analytics", WorkPriority.IDLE, 0L, () -> { }));
        scheduler.submit(BackgroundTask.once("weather", WorkPriority.BACKGROUND, 0L, () -> { }));
        scheduler.setExternalControl(new BackgroundBudgetControl(1.0D, Long.MAX_VALUE, false, false));

        BackgroundWorkScheduler.ProcessingReport report = scheduler.process(1L, Long.MAX_VALUE);

        assertEquals(0, report.processed());
        assertEquals(2, report.remaining());
    }

    @Test
    void idleWorkGetsAFairTurnAcrossSingleTaskPasses() {
        BackgroundWorkScheduler scheduler = schedulerWithDefaults();
        scheduler.configure(new BackgroundWorkScheduler.Settings(true, 1, Long.MAX_VALUE, 128, 128));
        AtomicInteger idle = new AtomicInteger();
        scheduler.submit(BackgroundTask.once("idle", WorkPriority.IDLE, 0, idle::incrementAndGet));
        for (int tick = 0; tick < 16; tick++) {
            scheduler.submit(BackgroundTask.once("critical", WorkPriority.CRITICAL, tick, () -> { }));
            scheduler.process(tick, Long.MAX_VALUE);
        }
        assertEquals(1, idle.get(), "Persistent high-priority arrivals must not starve permitted idle work");
    }

    @Test
    void perpetualIdleDeferralDoesNotStarveTheIntermediatePriority() {
        BackgroundWorkScheduler scheduler = schedulerWithDefaults();
        scheduler.configure(new BackgroundWorkScheduler.Settings(true, 1, Long.MAX_VALUE, 128, 128));
        AtomicInteger background = new AtomicInteger();
        scheduler.submit(new BackgroundTask("idle", WorkPriority.IDLE, 0, 0, "", () -> BackgroundTask.Result.DEFER));
        scheduler.submit(BackgroundTask.once("background", WorkPriority.BACKGROUND, 0, background::incrementAndGet));
        for (int tick = 0; tick < 32; tick++) {
            scheduler.submit(BackgroundTask.once("critical", WorkPriority.CRITICAL, tick, () -> { }));
            scheduler.process(tick, Long.MAX_VALUE);
        }
        assertEquals(1, background.get());
    }

    @Test
    void adaptiveModePreservesCriticalBypassAndLiveAllowance() {
        Object server = new Object();
        var mode = com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig.Mode.AUTO;
        com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore.start(server,
                new com.thunder.wildernessodysseyapi.opc.coordinator.CoordinatorConfig.Values(mode, mode, 2, .1, 100, 200, 600, 100, 4096), true);
        try {
            BackgroundWorkScheduler scheduler = schedulerWithDefaults();
            AtomicInteger runs = new AtomicInteger();
            scheduler.submit(BackgroundTask.once("critical", WorkPriority.CRITICAL, 0, runs::incrementAndGet));
            assertEquals(0, scheduler.process(0, 0, () -> false).processed());
            assertEquals(1, scheduler.process(0, 0, () -> true).processed());
            assertEquals(1, runs.get());
        } finally {
            com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore.clear();
        }
    }

    private static BackgroundWorkScheduler schedulerWithDefaults() {
        BackgroundWorkScheduler scheduler = new BackgroundWorkScheduler(new BackgroundMetrics());
        scheduler.configure(new BackgroundWorkScheduler.Settings(true, 64, Long.MAX_VALUE, 128, 128));
        return scheduler;
    }
}
