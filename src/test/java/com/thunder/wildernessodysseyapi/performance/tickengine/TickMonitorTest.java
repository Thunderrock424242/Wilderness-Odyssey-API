package com.thunder.wildernessodysseyapi.performance.tickengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies confirmed escalation, hysteresis, and one-level-at-a-time recovery. */
class TickMonitorTest {

    @Test
    void isolatedSlowTickDoesNotForceEmergencyPressure() {
        TickMonitor monitor = monitor();

        monitor.recordTick(80.0D);
        monitor.recordTick(20.0D);
        monitor.recordTick(20.0D);

        assertEquals(TickPressure.RELAXED, monitor.pressure());
        assertEquals(3L, monitor.tickCount());
        assertEquals(1L, monitor.overloadedTickCount());
    }

    @Test
    void sustainedOverloadEscalatesThenRecoversGradually() {
        TickMonitor monitor = monitor();
        for (int index = 0; index < 5; index++) {
            monitor.recordTick(60.0D);
        }
        assertEquals(TickPressure.OVERLOADED, monitor.pressure());

        monitor.recordTick(20.0D);
        monitor.recordTick(20.0D);
        assertEquals(TickPressure.CRITICAL, monitor.pressure());
        monitor.recordTick(20.0D);
        monitor.recordTick(20.0D);
        assertEquals(TickPressure.HIGH, monitor.pressure());
        assertTrue(monitor.estimatedTps() <= 20.0D);
    }

    private static TickMonitor monitor() {
        return new TickMonitor(
                new TickMonitor.Thresholds(30.0D, 40.0D, 47.0D, 50.0D, 2.0D, 3, 2),
                3,
                5
        );
    }
}
