package com.thunder.wildernessodysseyapi.anomaly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyAtmospherePolicyTest {
    @Test
    void blendsEdgeSamplesWithoutInventingIntensityOutsideTheBiome() {
        assertEquals(0.0, AnomalyAtmospherePolicy.blend(new double[]{0, 0, 0}), 1.0E-8);
        assertEquals(0.3, AnomalyAtmospherePolicy.blend(new double[]{0.9, 0, 0}), 1.0E-8);
        assertEquals(1.0, AnomalyAtmospherePolicy.blend(new double[]{2, 1}), 1.0E-8);
        assertEquals(0.0, AnomalyAtmospherePolicy.blend(new double[]{Double.NaN}), 1.0E-8);
    }

    @Test
    void realRiftInfluenceFallsSmoothlyToZeroAtItsBoundary() {
        assertEquals(1.0, AnomalyAtmospherePolicy.riftInfluence(0), 1.0E-8);
        assertEquals(0.5, AnomalyAtmospherePolicy.riftInfluence(48), 1.0E-8);
        assertEquals(0.0, AnomalyAtmospherePolicy.riftInfluence(96), 1.0E-8);
        assertEquals(0.0, AnomalyAtmospherePolicy.riftInfluence(Double.NaN), 1.0E-8);
    }

    @Test
    void unchangedSummariesWaitForRefreshButChangesAndClockResetSend() {
        assertFalse(AnomalyAtmospherePolicy.shouldSend(100, 120, false));
        assertTrue(AnomalyAtmospherePolicy.shouldSend(100, 120, true));
        assertTrue(AnomalyAtmospherePolicy.shouldSend(100, 300, false));
        assertTrue(AnomalyAtmospherePolicy.shouldSend(100, 80, false));
    }
}
