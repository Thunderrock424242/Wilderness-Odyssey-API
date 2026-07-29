package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the pure impulse profile mirrored by the active vertex shader. */
class WaterSurfaceDisplacementTest {

    @Test
    void impulseDepressesItsCenterAndRaisesItsMovingRing() {
        float center = WaterSurfaceDisplacement.sampleImpulse(
                0.0f, 1.2f, 0.1f, 0.4f, 0.2f, 0.75f);
        float ring = WaterSurfaceDisplacement.sampleImpulse(
                1.2f, 1.2f, 0.1f, 0.4f, 0.2f, 0.75f);

        assertTrue(center < -0.09f);
        assertTrue(ring > 0.07f);
    }

    @Test
    void zeroAmplitudeProducesNoSurfaceOffset() {
        assertEquals(0.0f, WaterSurfaceDisplacement.sampleImpulse(
                0.8f, 1.2f, 0.0f, 0.4f, 0.2f, 0.75f));
    }

    @Test
    void firstWakeIsNotSuppressedBySentinelOverflow() {
        assertTrue(WaterSurfaceDisplacement.wakeIntervalElapsed(
                12L,
                Long.MIN_VALUE,
                4
        ));
    }
}
