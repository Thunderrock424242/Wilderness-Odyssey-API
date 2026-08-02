package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies underwater optics combine snapshot biome color with water-body identity. */
class ClientWaterImmersionTintTest {

    @Test
    void consumesSnapshotBiomeTintUsingTheSurfaceOpticalBlend() {
        float[] redBiomeOcean = ClientWaterImmersion.bodyTint(1.0f, 0.0f, 0.0f, 0xFF0000);
        float[] blueBiomeOcean = ClientWaterImmersion.bodyTint(1.0f, 0.0f, 0.0f, 0x0000FF);

        assertEquals(0.018f * 0.72f + 0.28f, redBiomeOcean[0], 1.0e-6f);
        assertEquals(0.25f * 0.72f, redBiomeOcean[1], 1.0e-6f);
        assertEquals(0.62f * 0.72f, redBiomeOcean[2], 1.0e-6f);
        assertTrue(redBiomeOcean[0] > blueBiomeOcean[0]);
        assertTrue(blueBiomeOcean[2] > redBiomeOcean[2]);
    }

    @Test
    void retainsBodyTypeWeightingForTheSameBiomeTint() {
        float[] ocean = ClientWaterImmersion.bodyTint(1.0f, 0.0f, 0.0f, 0x3F76E4);
        float[] river = ClientWaterImmersion.bodyTint(0.0f, 1.0f, 0.0f, 0x3F76E4);
        float[] lake = ClientWaterImmersion.bodyTint(0.0f, 0.0f, 1.0f, 0x3F76E4);

        assertNotEquals(ocean[1], river[1], 1.0e-6f);
        assertNotEquals(river[1], lake[1], 1.0e-6f);
        assertTrue(ocean[2] > river[2]);
        assertTrue(river[2] > lake[2]);
    }

    @Test
    void normalizesBlendedBodyWeightsBeforeApplyingBiomeColor() {
        float[] normalized = ClientWaterImmersion.bodyTint(2.0f, 2.0f, 0.0f, 0x000000);
        float expectedGreen = (0.25f * 0.5f + 0.35f * 0.5f) * 0.72f;

        assertEquals(expectedGreen, normalized[1], 1.0e-6f);
    }

    @Test
    void fallsBackToLakeIdentityWhenAColumnHasNoUsableBodyWeights() {
        float[] tint = ClientWaterImmersion.bodyTint(0.0f, 0.0f, 0.0f, 0x000000);

        assertEquals(0.045f * 0.72f, tint[0], 1.0e-6f);
        assertEquals(0.38f * 0.72f, tint[1], 1.0e-6f);
        assertEquals(0.52f * 0.72f, tint[2], 1.0e-6f);
    }
}
