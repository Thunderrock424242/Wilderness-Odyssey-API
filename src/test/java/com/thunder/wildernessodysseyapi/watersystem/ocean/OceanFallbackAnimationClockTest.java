package com.thunder.wildernessodysseyapi.watersystem.ocean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards sub-tick fallback-ocean motion at extreme world ages. */
class OceanFallbackAnimationClockTest {

    private static final double TWO_PI = Math.PI * 2.0;

    @Test
    void preservesSubTickMotionInAnAncientWorld() {
        long ancientGameTime = 1_000_000_000L;
        long periodTicks = 420L;

        double early = OceanFallbackAnimationClock.periodicPhase(
                ancientGameTime, 0.125f, periodTicks);
        double late = OceanFallbackAnimationClock.periodicPhase(
                ancientGameTime, 0.875f, periodTicks);

        assertEquals(TWO_PI * 0.75 / periodTicks, late - early, 1.0e-12);
    }

    @Test
    void repeatsExactlyAfterWholePeriodsAtExtremeAges() {
        long ancientGameTime = Long.MAX_VALUE - 2_000_000_000L;
        long periodTicks = 96_000L;
        long wholePeriods = periodTicks * 10_000L;

        double first = OceanFallbackAnimationClock.periodicPhase(
                ancientGameTime, 0.375f, periodTicks);
        double repeated = OceanFallbackAnimationClock.periodicPhase(
                ancientGameTime + wholePeriods, 0.375f, periodTicks);

        assertEquals(first, repeated, 0.0);
    }

    @Test
    void remainsContinuousAcrossATickBoundary() {
        long ancientGameTime = 1_000_000_000L;
        long periodTicks = 24_000L;

        double before = OceanFallbackAnimationClock.periodicPhase(
                ancientGameTime, 0.999f, periodTicks);
        double after = OceanFallbackAnimationClock.periodicPhase(
                ancientGameTime + 1L, 0.0f, periodTicks);

        assertEquals(TWO_PI * 0.001 / periodTicks, after - before, 2.0e-11);
    }
}
