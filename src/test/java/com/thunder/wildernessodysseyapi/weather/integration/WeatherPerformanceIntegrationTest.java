package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.tickengine.AdaptiveThrottle;
import com.thunder.wildernessodysseyapi.performance.tickengine.SubsystemPolicy;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherPerformanceIntegrationTest {

    @Test
    void relaxedActiveWeatherKeepsItsConfiguredCadence() {
        assertEquals(60, WeatherPerformanceIntegration.pollInterval(
                weatherThrottle(),
                TickPressure.RELAXED,
                ActivityLevel.ACTIVE,
                1.0D,
                60
        ));
    }

    @Test
    void overloadedWeatherSlowsButCannotSuspend() {
        assertEquals(100, WeatherPerformanceIntegration.pollInterval(
                weatherThrottle(),
                TickPressure.OVERLOADED,
                ActivityLevel.ACTIVE,
                0.1D,
                60
        ));
        assertEquals(100, WeatherPerformanceIntegration.pollInterval(
                weatherThrottle(),
                TickPressure.RELAXED,
                ActivityLevel.DORMANT,
                1.0D,
                60
        ));
    }

    @Test
    void adaptivePolicyNeverSpeedsUpAnOperatorConfiguredSlowInterval() {
        assertEquals(1_200, WeatherPerformanceIntegration.pollInterval(
                weatherThrottle(),
                TickPressure.OVERLOADED,
                ActivityLevel.DORMANT,
                0.1D,
                1_200
        ));
    }

    @Test
    void disablingAdaptiveThrottlePreservesTheConfiguredInterval() {
        AdaptiveThrottle throttle = weatherThrottle();
        throttle.setEnabled(false);

        assertEquals(60, WeatherPerformanceIntegration.pollInterval(
                throttle,
                TickPressure.OVERLOADED,
                ActivityLevel.DORMANT,
                0.1D,
                60
        ));
    }

    @Test
    void snapshotKeysUseASeparateCoalescingLaneFromLevelMaintenance() {
        assertEquals(-1L, WeatherPerformanceIntegration.snapshotObjectKey(1L));
        assertEquals(-42L, WeatherPerformanceIntegration.snapshotObjectKey(42L));
        assertThrows(
                IllegalArgumentException.class,
                () -> WeatherPerformanceIntegration.snapshotObjectKey(0L)
        );
    }

    @Test
    void asyncWeatherTimeoutIsBoundedAndHandlesTickRollback() {
        assertFalse(WeatherPerformanceIntegration.calculationTimedOut(1_199L, 1_000L));
        assertTrue(WeatherPerformanceIntegration.calculationTimedOut(1_200L, 1_000L));
        assertTrue(WeatherPerformanceIntegration.calculationTimedOut(900L, 1_000L));
        assertFalse(WeatherPerformanceIntegration.calculationTimedOut(10_000L, Long.MIN_VALUE));
    }

    private static AdaptiveThrottle weatherThrottle() {
        AdaptiveThrottle throttle = new AdaptiveThrottle();
        throttle.register(new SubsystemPolicy(
                "weather",
                "Weather",
                TickPriority.NORMAL,
                100,
                false
        ));
        return throttle;
    }
}
