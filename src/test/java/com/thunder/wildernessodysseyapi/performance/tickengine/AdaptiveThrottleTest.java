package com.thunder.wildernessodysseyapi.performance.tickengine;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies subsystem policy, activity, suspension, and disabled-engine behavior. */
class AdaptiveThrottleTest {

    @Test
    void suspendsOptionalBackgroundSystemDuringOverload() {
        AdaptiveThrottle throttle = new AdaptiveThrottle();
        throttle.register(new SubsystemPolicy("ecosystem", "Ecosystem", TickPriority.BACKGROUND, 200, true));

        assertEquals(Integer.MAX_VALUE, throttle.intervalFor(
                "ecosystem", 20, TickPressure.OVERLOADED, ActivityLevel.BACKGROUND, 1.0D));
    }

    @Test
    void nonSuspendableGameplaySystemRetainsBoundedUpdateRate() {
        AdaptiveThrottle throttle = new AdaptiveThrottle();
        throttle.register(new SubsystemPolicy("labs", "Labs", TickPriority.GAMEPLAY, 20, false));

        int interval = throttle.intervalFor(
                "labs", 1, TickPressure.OVERLOADED, ActivityLevel.DORMANT, 0.1D);

        assertEquals(20, interval);
        assertTrue(throttle.shouldRun(120L, 100L, interval));
    }

    @Test
    void disabledThrottleReturnsNormalInterval() {
        AdaptiveThrottle throttle = new AdaptiveThrottle();
        throttle.register(new SubsystemPolicy("weather", "Weather", TickPriority.NORMAL, 100, true));
        throttle.setEnabled(false);

        assertEquals(5, throttle.intervalFor(
                "weather", 5, TickPressure.OVERLOADED, ActivityLevel.DORMANT, 0.0D));
    }
}
