package com.thunder.wildernessodysseyapi.ecosystem.integration;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.tickengine.AdaptiveThrottle;
import com.thunder.wildernessodysseyapi.performance.tickengine.SubsystemPolicy;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcosystemPerformanceIntegrationTest {

    @Test
    void relaxedActiveServerUsesTheFiveTickMaintenanceCadence() {
        AdaptiveThrottle throttle = ecosystemThrottle();

        int interval = EcosystemPerformanceIntegration.maintenanceInterval(
                throttle,
                TickPressure.RELAXED,
                ActivityLevel.ACTIVE,
                1.0D
        );

        assertEquals(EcosystemPerformanceIntegration.DATA_ENGINE_POLL_INTERVAL_TICKS, interval);
        assertFalse(throttle.shouldRun(104L, 100L, interval));
        assertTrue(throttle.shouldRun(105L, 100L, interval));
    }

    @Test
    void overloadSuspendsOptionalWorkWithoutCreatingCatchUpDebt() {
        AdaptiveThrottle throttle = ecosystemThrottle();

        int overloaded = EcosystemPerformanceIntegration.maintenanceInterval(
                throttle,
                TickPressure.OVERLOADED,
                ActivityLevel.ACTIVE,
                0.1D
        );
        assertEquals(Integer.MAX_VALUE, overloaded);
        assertFalse(throttle.shouldRun(200L, 100L, overloaded));

        int recovered = EcosystemPerformanceIntegration.maintenanceInterval(
                throttle,
                TickPressure.RELAXED,
                ActivityLevel.ACTIVE,
                1.0D
        );
        assertTrue(throttle.shouldRun(201L, 100L, recovered));
    }

    @Test
    void playerlessServerLeavesOptionalEcosystemWorkDormant() {
        AdaptiveThrottle throttle = ecosystemThrottle();

        int interval = EcosystemPerformanceIntegration.maintenanceInterval(
                throttle,
                TickPressure.RELAXED,
                ActivityLevel.DORMANT,
                1.0D
        );

        assertEquals(Integer.MAX_VALUE, interval);
    }

    private static AdaptiveThrottle ecosystemThrottle() {
        AdaptiveThrottle throttle = new AdaptiveThrottle();
        throttle.register(new SubsystemPolicy(
                "ecosystem",
                "Ecosystem",
                TickPriority.BACKGROUND,
                200,
                true
        ));
        return throttle;
    }
}
