package com.thunder.wildernessodysseyapi.opc.coordinator;

import com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceCoordinatorTest {
    @AfterEach void clear() { PerformanceCoordinator.clear(); }

    @Test
    void schedulersAndSavingShareOneRemainingAllowance() {
        Object server = new Object();
        PerformanceCoordinator.start(server, settings(Mode.AUTO));
        PerformanceCoordinator.ready(server);
        for (int tick = 1; tick <= 250; tick++) {
            PerformanceCoordinator.beginTick(server, tick, System.nanoTime());
            PerformanceCoordinator.observe(server, 20, 0.1, 0, 0);
        }
        assertEquals(2_000_000, PerformanceCoordinator.constrainBudget(Long.MAX_VALUE));
        PerformanceCoordinator.recordCost(CoordinatorGovernor.Module.BACKGROUND, 1_500_000, 2_000_000);
        assertEquals(500_000, PerformanceCoordinator.budget(CoordinatorGovernor.Module.SAVE, Long.MAX_VALUE));
        PerformanceCoordinator.recordCost(CoordinatorGovernor.Module.SAVE, 500_000, 500_000);
        assertEquals(0, PerformanceCoordinator.constrainBudget(Long.MAX_VALUE));
    }

    @Test
    void disabledAndGovernorOffPreserveExistingSchedulerAllowance() {
        assertEquals(7_000_000, PerformanceCoordinator.constrainBudget(7_000_000));
        Object server = new Object();
        PerformanceCoordinator.start(server, settings(Mode.OFF));
        assertEquals(7_000_000, PerformanceCoordinator.constrainBudget(7_000_000));
        var executed = new java.util.concurrent.atomic.AtomicBoolean();
        assertTrue(PerformanceCoordinator.submitDisposable(Runnable::run, () -> executed.set(true)));
        assertTrue(executed.get());
    }

    @Test
    void stoppingAndStaleServerSignalsCannotResumeDisposableAdmission() {
        Object server = new Object();
        PerformanceCoordinator.start(server, settings(Mode.AUTO));
        PerformanceCoordinator.stopping(server);
        PerformanceCoordinator.ready(new Object());
        PerformanceCoordinator.observe(server, 20, 0, 0, 0);
        assertFalse(PerformanceCoordinator.submitDisposable(Runnable::run, () -> fail("shutdown work ran")));
        assertEquals("STOPPING", PerformanceCoordinator.state());
    }

    private static CoordinatorConfig.Values settings(Mode mode) {
        return new CoordinatorConfig.Values(mode, Mode.OFF, 2, 0.1, 100, 200, 600, 100, 4096);
    }
}
