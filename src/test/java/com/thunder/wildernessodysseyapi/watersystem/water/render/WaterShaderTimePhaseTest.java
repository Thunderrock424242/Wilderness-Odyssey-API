package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies CPU-reduced shader animation remains tick-continuous at extreme ages. */
class WaterShaderTimePhaseTest {

    @Test
    void phaseRetainsTickAndPartialTickMotionNearLongLimit() {
        long gameTime = Long.MAX_VALUE - 2_048L;
        double rate = 1.37;
        float atTick = WaterShaders.stableAnimationPhase(gameTime, 0.0f, rate);
        float atHalfTick = WaterShaders.stableAnimationPhase(gameTime, 0.5f, rate);
        float atNextTick = WaterShaders.stableAnimationPhase(gameTime + 1L, 0.0f, rate);

        assertEquals(rate / 40.0, wrappedDelta(atHalfTick, atTick), 1.0e-4);
        assertEquals(rate / 20.0, wrappedDelta(atNextTick, atTick), 1.0e-4);
    }

    private static double wrappedDelta(float later, float earlier) {
        return Math.IEEEremainder(later - earlier, Math.PI * 2.0);
    }
}
