package com.thunder.wildernessodysseyapi.opc.core;

import com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig.Mode;
import com.thunder.wildernessodysseyapi.opc.coordinator.CoordinatorConfig;
import com.thunder.wildernessodysseyapi.opc.coordinator.CoordinatorGovernor.Module;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OdysseyPerformanceCoreTest {
    @AfterEach void stop() { OdysseyPerformanceCore.clear(); }

    @Test
    void modulesShareActualCostsAndRetainIndependentFairAllowances() {
        Object server = running();
        OdysseyPerformanceCore.beginTick(server, 251, System.nanoTime());
        for (Module module : Module.values()) assertEquals(500_000, OdysseyPerformanceCore.optionalBudget(module, Long.MAX_VALUE));
        OdysseyPerformanceCore.recordCost(Module.DATA, 100_000, 500_000);
        assertEquals(400_000, OdysseyPerformanceCore.optionalBudget(Module.DATA, Long.MAX_VALUE));
        assertEquals(500_000, OdysseyPerformanceCore.optionalBudget(Module.TICK, Long.MAX_VALUE));
        OdysseyPerformanceCore.recordCost(Module.TICK, 1_900_000, 500_000);
        for (Module module : Module.values()) assertEquals(0, OdysseyPerformanceCore.optionalBudget(module, Long.MAX_VALUE));
        assertEquals(2_000_000, OdysseyPerformanceCore.metrics().get("coordinatorConsumedNanos"));
    }

    @Test
    void expiredTickAndOverloadDoNotAdmitCatchUpWork() {
        Object server = running();
        OdysseyPerformanceCore.beginTick(server, 251, System.nanoTime() - 51_000_000);
        assertEquals(0, OdysseyPerformanceCore.optionalBudget(Module.DATA, Long.MAX_VALUE));
        OdysseyPerformanceCore.beginTick(server, 252, System.nanoTime());
        OdysseyPerformanceCore.observe(server, 80, 0, 0, 0);
        assertEquals(0, OdysseyPerformanceCore.optionalBudget(Module.DATA, Long.MAX_VALUE));
        OdysseyPerformanceCore.beginTick(server, 253, System.nanoTime());
        OdysseyPerformanceCore.observe(server, 20, 0, 0, 0);
        assertEquals(0, OdysseyPerformanceCore.optionalBudget(Module.DATA, Long.MAX_VALUE));
    }

    @Test
    void adaptiveOptionalExhaustionPreservesCriticalTaskSemantics() {
        Object server = running();
        OdysseyPerformanceCore.beginTick(server, 251, System.nanoTime());
        OdysseyPerformanceCore.recordCost(Module.DATA, 2_000_000, 500_000);
        var scheduler = new com.thunder.wildernessodysseyapi.opc.tickengine.TickWorkScheduler(
                new com.thunder.wildernessodysseyapi.opc.tickengine.TickEngineMetrics());
        var runs = new java.util.concurrent.atomic.AtomicInteger();
        scheduler.submit(com.thunder.wildernessodysseyapi.opc.tickengine.TickTask.once("required",
                com.thunder.wildernessodysseyapi.opc.tickengine.TickPriority.CRITICAL, 251, runs::incrementAndGet));
        scheduler.process(251, System.nanoTime() - 1,
                com.thunder.wildernessodysseyapi.opc.tickengine.TickPressure.RELAXED);
        assertEquals(1, runs.get(), "Critical callback semantics remain independent of optional admission");
    }

    private static Object running() {
        Object server = new Object();
        OdysseyPerformanceCore.start(server, new CoordinatorConfig.Values(Mode.AUTO, Mode.OFF, 2, .1, 100, 200, 600, 100, 4096), true);
        OdysseyPerformanceCore.ready(server);
        for (int tick = 1; tick <= 250; tick++) {
            OdysseyPerformanceCore.beginTick(server, tick, System.nanoTime());
            OdysseyPerformanceCore.observe(server, 20, .1, 0, 0);
        }
        return server;
    }
}
