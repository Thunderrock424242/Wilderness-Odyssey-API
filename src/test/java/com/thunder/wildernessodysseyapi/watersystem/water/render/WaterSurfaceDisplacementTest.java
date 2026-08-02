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

    @Test
    void combinedImpulseHeightUsesShaderCap() {
        assertEquals(0.25f, WaterSurfaceDisplacement.MAX_COMBINED_HEIGHT_OFFSET, 0.0f);
        assertEquals(0.25f, WaterSurfaceDisplacement.clampCombinedHeight(0.8f), 0.0f);
        assertEquals(-0.25f, WaterSurfaceDisplacement.clampCombinedHeight(-0.8f), 0.0f);
    }

    @Test
    void foamOutlivesDisplacementButReleasesItsBoundedSlot() {
        assertEquals(1.0f, WaterSurfaceDisplacement.persistentFoamEnvelope(42.0, 42, 82));
        assertTrue(WaterSurfaceDisplacement.persistentFoamEnvelope(62.0, 42, 82) > 0.0f);
        assertEquals(0.0f, WaterSurfaceDisplacement.persistentFoamEnvelope(82.0, 42, 82));
        assertEquals(0.0f, WaterSurfaceDisplacement.persistentFoamEnvelope(-1.0, 42, 82));
    }

    @Test
    void farWorldImpulseAnchorPreservesSubBlockRelativePosition() {
        double camera = 29_999_998.375;
        double disturbance = 29_999_999.8125;
        double anchor = WaterSurfaceDisplacement.impulseAnchor(camera);

        assertEquals(29_999_984.0, anchor, 0.0);
        assertEquals(15.8125f, (float) (disturbance - anchor), 0.0f);
    }
}
