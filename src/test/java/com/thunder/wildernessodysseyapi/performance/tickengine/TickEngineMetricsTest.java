package com.thunder.wildernessodysseyapi.performance.tickengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies cheap aggregate timing and queue counters exposed to debug consumers. */
class TickEngineMetricsTest {

    @Test
    void aggregatesSubsystemTimingsAndFindsWorstAverage() {
        TickEngineMetrics metrics = new TickEngineMetrics();
        metrics.configure(true, 1000.0D, 1200L);
        metrics.recordExecution("weather", 2_000_000L, 1L);
        metrics.recordExecution("weather", 4_000_000L, 2L);
        metrics.recordExecution("water", 1_000_000L, 2L);
        metrics.recordDeferred("weather");
        metrics.setDeferredTasks(3);

        TickEngineMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals("weather", snapshot.worstSubsystem());
        assertEquals(3.0D, snapshot.subsystemTimings().get("weather").averageMillis(), 0.0001D);
        assertEquals(1L, snapshot.subsystemTimings().get("weather").deferredCount());
        assertEquals(3, snapshot.deferredTasks());
    }
}
