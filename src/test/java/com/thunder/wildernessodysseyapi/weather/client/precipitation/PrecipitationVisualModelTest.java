package com.thunder.wildernessodysseyapi.weather.client.precipitation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecipitationVisualModelTest {

    @Test
    void topOfColumnLeansUpwindSoFallingPrecipitationLandsDownwind() {
        float rain = PrecipitationVisualModel.topWindOffset(0.8, 32.0, 10.0, false);
        float snow = PrecipitationVisualModel.topWindOffset(0.8, 32.0, 10.0, true);

        assertEquals(-5.44F, rain, 1.0E-6F);
        assertEquals(-8.0F, snow, 1.0E-6F);
    }

    @Test
    void nearOpacityUsesTheSampledColumnInsteadOfCameraWeather() {
        assertEquals(0.0F, PrecipitationVisualModel.nearAlpha(0.0, 3.0, 10, false));
        assertTrue(PrecipitationVisualModel.nearAlpha(0.8, 3.0, 10, false) > 0.5F);
    }

    @Test
    void distantShaftsFadeBeforeTheirBoundedFarEdge() {
        float near = PrecipitationVisualModel.distantAlpha(1.0, 16.0, 10, 90);
        float middle = PrecipitationVisualModel.distantAlpha(1.0, 55.0, 10, 90);
        float far = PrecipitationVisualModel.distantAlpha(1.0, 90.0, 10, 90);

        assertTrue(near > middle);
        assertTrue(middle > far);
        assertEquals(0.0F, far, 1.0E-6F);
    }

    @Test
    void distantRadiusNeverExceedsTheShaftBudget() {
        int spacing = 6;
        int radius = PrecipitationVisualModel.boundedDistantRadiusBlocks(96, spacing, 768);
        int radiusCells = radius / spacing;

        assertTrue(radius <= 96);
        assertTrue(PrecipitationVisualModel.latticePointCount(radiusCells) <= 768);
    }

    @Test
    void columnNoiseIsDeterministicAndBounded() {
        double first = PrecipitationVisualModel.columnNoise(27, -91, 42L);
        double repeated = PrecipitationVisualModel.columnNoise(27, -91, 42L);

        assertEquals(first, repeated, 0.0);
        assertTrue(first >= 0.0 && first < 1.0);
    }

    @Test
    void nearColumnSelectionIsStableAndDensityBounded() {
        boolean first = PrecipitationVisualModel.shouldRenderNearColumn(14, -22, 0.7, 0.8);
        boolean repeated = PrecipitationVisualModel.shouldRenderNearColumn(14, -22, 0.7, 0.8);

        assertEquals(first, repeated);
        assertTrue(PrecipitationVisualModel.shouldRenderNearColumn(14, -22, 1.0, 1.0));
    }

    @Test
    void opacityScalingNeverLeaksInvalidAlpha() {
        assertEquals(0.5F, PrecipitationVisualModel.scaledAlpha(0.5F, 1.0), 1.0E-6F);
        assertEquals(1.0F, PrecipitationVisualModel.scaledAlpha(0.8F, 4.0), 1.0E-6F);
        assertEquals(0.0F, PrecipitationVisualModel.scaledAlpha(0.8F, Double.NaN), 1.0E-6F);
    }

    @Test
    void rainAmbienceNeverPlaysForCameraLocalSnow() {
        assertTrue(PrecipitationVisualModel.usesRainSound(PrecipitationType.RAIN, 0.8));
        assertTrue(PrecipitationVisualModel.usesRainSound(PrecipitationType.HAIL, 0.8));
        assertFalse(PrecipitationVisualModel.usesRainSound(PrecipitationType.SNOW, 0.8));
        assertFalse(PrecipitationVisualModel.usesRainSound(PrecipitationType.RAIN, 0.0));
    }

    @Test
    void snowUsesSparseMutedTexturePresentation() {
        assertEquals(1.0F / 12.0F, PrecipitationVisualModel.snowTextureVerticalScale(), 1.0E-6F);
        assertEquals(0.68F, PrecipitationVisualModel.snowAlpha(1.0F), 1.0E-6F);
        assertTrue(PrecipitationVisualModel.softenedSnowLight(0) < 0x00F000F0);
    }

    @Test
    void hailPelletsAreStableBoundedAndSparserAtDistance() {
        float first = PrecipitationVisualModel.hailPelletProgress(14, -22, 2, 81.5);
        float repeated = PrecipitationVisualModel.hailPelletProgress(14, -22, 2, 81.5);

        assertEquals(first, repeated, 0.0F);
        assertTrue(first >= 0.0F && first < 1.0F);
        assertEquals(5, PrecipitationVisualModel.hailPelletCount(false));
        assertEquals(2, PrecipitationVisualModel.hailPelletCount(true));
        assertTrue(PrecipitationVisualModel.hailAlpha(1.0F, true)
                < PrecipitationVisualModel.hailAlpha(1.0F, false));
    }
}
