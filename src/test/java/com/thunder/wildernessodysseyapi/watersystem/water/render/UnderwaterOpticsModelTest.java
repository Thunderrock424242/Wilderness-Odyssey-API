package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnderwaterOpticsModelTest {

    @Test
    void surfaceTransitionBlendsAcrossAnimatedBoundary() {
        var above = sample(-0.04f, 12.0f, 0.0f, 0.0f);
        var below = sample(0.18f, 12.0f, 0.0f, 0.0f);

        assertEquals(0.0f, above.immersionBlend(), 1.0e-6f);
        assertEquals(1.0f, below.immersionBlend(), 1.0e-6f);
    }

    @Test
    void stormAndMovingShallowWaterReduceVisibility() {
        var calmDeep = sample(2.0f, 20.0f, 0.0f, 0.0f);
        var disturbedShallow = sample(2.0f, 2.0f, 1.0f, 1.0f);

        assertTrue(disturbedShallow.clarity() < calmDeep.clarity());
        assertTrue(disturbedShallow.visibilityBlocks() < calmDeep.visibilityBlocks());
    }

    @Test
    void redLightAttenuatesFasterThanBlueLight() {
        var surface = sample(0.2f, 20.0f, 0.0f, 0.0f);
        var deep = sample(24.0f, 20.0f, 0.0f, 0.0f);

        float redTransmission = deep.fogRed() / surface.fogRed();
        float blueTransmission = deep.fogBlue() / surface.fogBlue();
        assertTrue(redTransmission < blueTransmission);
    }

    @Test
    void moderateDaylightOceanDepthKeepsAReadableScatteringColor() {
        var optics = UnderwaterOpticsModel.evaluate(
                8.0f,
                8.0f,
                0.0f,
                1.0f,
                0.018f,
                0.25f,
                0.62f,
                0.0f,
                44.0f,
                1.0f
        );

        float luminance = optics.fogRed() * 0.2126f
                + optics.fogGreen() * 0.7152f
                + optics.fogBlue() * 0.0722f;
        assertTrue(luminance > 0.12f,
                "daylight water at the screenshot depth must not collapse toward black");
        assertTrue(optics.fogBlue() > optics.fogGreen());
        assertTrue(optics.fogGreen() > optics.fogRed());
    }

    @Test
    void ambientFloorRemainsNonBlackWithoutDirectDaylight() {
        var optics = UnderwaterOpticsModel.evaluate(
                8.0f,
                8.0f,
                0.0f,
                0.0f,
                0.018f,
                0.25f,
                0.62f,
                0.0f,
                44.0f,
                1.0f
        );

        float luminance = optics.fogRed() * 0.2126f
                + optics.fogGreen() * 0.7152f
                + optics.fogBlue() * 0.0722f;
        assertTrue(luminance > 0.05f,
                "night and overhangs may be dark but retain bounded ambient water color");
    }

    private static UnderwaterOpticsModel.OpticalProperties sample(
            float depth,
            float columnDepth,
            float disturbance,
            float seaState
    ) {
        return UnderwaterOpticsModel.evaluate(
                depth,
                columnDepth,
                disturbance,
                1.0f,
                0.25f,
                0.55f,
                0.90f,
                seaState,
                44.0f,
                1.0f
        );
    }
}
