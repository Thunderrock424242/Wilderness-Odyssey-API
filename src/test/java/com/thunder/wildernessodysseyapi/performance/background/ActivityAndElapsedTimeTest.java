package com.thunder.wildernessodysseyapi.performance.background;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies pure activity classification and monotonic elapsed-game-time handling. */
class ActivityAndElapsedTimeTest {

    @Test
    void classifiesCallerDefinedDistanceBands() {
        ActivityManager manager = new ActivityManager();
        ActivityManager.DistanceThresholds thresholds = new ActivityManager.DistanceThresholds(8.0D, 32.0D, 128.0D);

        assertEquals(ActivityLevel.ACTIVE, manager.classify(true, 4.0D * 4.0D, thresholds, false));
        assertEquals(ActivityLevel.NEARBY, manager.classify(true, 20.0D * 20.0D, thresholds, false));
        assertEquals(ActivityLevel.BACKGROUND, manager.classify(true, 100.0D * 100.0D, thresholds, false));
        assertEquals(ActivityLevel.DORMANT, manager.classify(true, 200.0D * 200.0D, thresholds, false));
        assertEquals(ActivityLevel.DORMANT, manager.classify(false, 0.0D, thresholds, false));
        assertEquals(ActivityLevel.ACTIVE, manager.classify(false, Double.POSITIVE_INFINITY, thresholds, true));
    }

    @Test
    void elapsedTicksNeverExpandsOrRunsBackward() {
        assertEquals(200L, ElapsedTimeSimulation.elapsedTicks(1000L, 1200L));
        assertEquals(0L, ElapsedTimeSimulation.elapsedTicks(1200L, 1000L));
    }
}
