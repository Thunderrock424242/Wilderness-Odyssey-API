package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies CPU-reduced shader animation remains tick-continuous at extreme ages. */
class WaterShaderTimePhaseTest {

    @Test
    void phaseRetainsTickAndPartialTickMotionNearLongLimit() {
        long gameTime = Long.MAX_VALUE - 2_048L;
        double rate = 1.37;
        float atTick = WaterAnimationClock.stablePhase(gameTime, 0.0f, rate);
        float atHalfTick = WaterAnimationClock.stablePhase(gameTime, 0.5f, rate);
        float atNextTick = WaterAnimationClock.stablePhase(gameTime + 1L, 0.0f, rate);

        assertEquals(rate / 40.0, wrappedDelta(atHalfTick, atTick), 1.0e-4);
        assertEquals(rate / 20.0, wrappedDelta(atNextTick, atTick), 1.0e-4);
    }

    @Test
    void phaseIsContinuousAcrossTickRollover() {
        long gameTime = 240_000L;
        double rate = 4.85;
        float beforeRollover = WaterAnimationClock.stablePhase(gameTime, 0.99f, rate);
        float afterRollover = WaterAnimationClock.stablePhase(gameTime + 1L, 0.0f, rate);

        assertEquals(rate * 0.01 / 20.0,
                wrappedDelta(afterRollover, beforeRollover), 1.0e-4);
    }

    @Test
    void surfaceMaterialVelocityDoesNotDependOnWorldAge() {
        for (int layer = 0; layer < 8; layer++) {
            float youngStart = WaterAnimationClock.surfacePhase(72_000L, 0.20f, layer);
            float youngEnd = WaterAnimationClock.surfacePhase(72_000L, 0.80f, layer);
            float ancientStart = WaterAnimationClock.surfacePhase(
                    1_000_000_000L, 0.20f, layer);
            float ancientEnd = WaterAnimationClock.surfacePhase(
                    1_000_000_000L, 0.80f, layer);

            assertEquals(
                    wrappedDelta(youngEnd, youngStart),
                    wrappedDelta(ancientEnd, ancientStart),
                    1.0e-4,
                    "surface layer " + layer + " changed speed with world age"
            );
        }
    }

    @Test
    void underwaterDistortionVelocityDoesNotDependOnWorldAge() {
        for (int layer = 0; layer < 4; layer++) {
            float youngStart = WaterAnimationClock.underwaterDistortionPhase(
                    72_000L, 0.25f, layer);
            float youngEnd = WaterAnimationClock.underwaterDistortionPhase(
                    72_000L, 0.75f, layer);
            float ancientStart = WaterAnimationClock.underwaterDistortionPhase(
                    Long.MAX_VALUE - 4_096L, 0.25f, layer);
            float ancientEnd = WaterAnimationClock.underwaterDistortionPhase(
                    Long.MAX_VALUE - 4_096L, 0.75f, layer);

            assertEquals(
                    wrappedDelta(youngEnd, youngStart),
                    wrappedDelta(ancientEnd, ancientStart),
                    1.0e-4,
                    "underwater layer " + layer + " changed speed with world age"
            );
        }
    }

    @Test
    void dayFractionRetainsPartialTickMotionAtExtremeWorldAge() {
        long ancientDayTime = Long.MAX_VALUE - 4_096L;
        float early = WaterAnimationClock.periodicFraction(
                ancientDayTime, 0.25f, 24_000L);
        float late = WaterAnimationClock.periodicFraction(
                ancientDayTime, 0.75f, 24_000L);

        assertEquals(0.5 / 24_000.0, late - early, 1.0e-7);
    }

    private static double wrappedDelta(float later, float earlier) {
        return Math.IEEEremainder(later - earlier, Math.PI * 2.0);
    }
}
