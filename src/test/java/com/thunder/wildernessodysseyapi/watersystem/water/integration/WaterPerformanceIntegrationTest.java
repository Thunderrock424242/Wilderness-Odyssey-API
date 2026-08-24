package com.thunder.wildernessodysseyapi.watersystem.water.integration;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.tickengine.AdaptiveThrottle;
import com.thunder.wildernessodysseyapi.performance.tickengine.SubsystemPolicy;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterPerformanceIntegrationTest {

    @Test
    void relaxedWaterPollsEveryTick() {
        assertEquals(1, WaterPerformanceIntegration.pollInterval(
                waterThrottle(),
                TickPressure.RELAXED,
                ActivityLevel.ACTIVE,
                1.0D
        ));
    }

    @Test
    void overloadedWaterSlowsButCannotSuspend() {
        assertEquals(100, WaterPerformanceIntegration.pollInterval(
                waterThrottle(),
                TickPressure.OVERLOADED,
                ActivityLevel.ACTIVE,
                0.1D
        ));
        assertEquals(100, WaterPerformanceIntegration.pollInterval(
                waterThrottle(),
                TickPressure.RELAXED,
                ActivityLevel.DORMANT,
                1.0D
        ));
    }

    @Test
    void disablingAdaptiveThrottlePreservesTheNormalPollInterval() {
        AdaptiveThrottle throttle = waterThrottle();
        throttle.setEnabled(false);

        assertEquals(1, WaterPerformanceIntegration.pollInterval(
                throttle,
                TickPressure.OVERLOADED,
                ActivityLevel.DORMANT,
                0.1D
        ));
    }

    @Test
    void eachLevelAndTaskLaneHasADistinctCoalescingKey() {
        long firstRegional = WaterPerformanceIntegration.taskKey(1L, 0);
        long firstPersistence = WaterPerformanceIntegration.taskKey(1L, 5);
        long secondRegional = WaterPerformanceIntegration.taskKey(2L, 0);

        assertEquals(8L, firstRegional);
        assertEquals(13L, firstPersistence);
        assertEquals(16L, secondRegional);
        assertNotEquals(firstRegional, firstPersistence);
        assertNotEquals(firstPersistence, secondRegional);
    }

    @Test
    void taskKeysRejectInvalidOrOverflowingInputs() {
        assertThrows(IllegalArgumentException.class, () -> WaterPerformanceIntegration.taskKey(0L, 0));
        assertThrows(IllegalArgumentException.class, () -> WaterPerformanceIntegration.taskKey(1L, -1));
        assertThrows(IllegalArgumentException.class, () -> WaterPerformanceIntegration.taskKey(1L, 8));
        assertThrows(
                IllegalArgumentException.class,
                () -> WaterPerformanceIntegration.taskKey(Long.MAX_VALUE / 8L + 1L, 0)
        );
    }

    @Test
    void elapsedCadenceHandlesFirstRunAndTickRollbackWithoutCatchUpDebt() {
        assertTrue(WaterPerformanceIntegration.isElapsedDue(0L, Long.MIN_VALUE, 20));
        assertFalse(WaterPerformanceIntegration.isElapsedDue(19L, 0L, 20));
        assertTrue(WaterPerformanceIntegration.isElapsedDue(20L, 0L, 20));
        assertTrue(WaterPerformanceIntegration.isElapsedDue(900L, 1_000L, 20));
    }

    private static AdaptiveThrottle waterThrottle() {
        AdaptiveThrottle throttle = new AdaptiveThrottle();
        throttle.register(new SubsystemPolicy(
                "water",
                "Water",
                TickPriority.NORMAL,
                100,
                false
        ));
        return throttle;
    }
}
