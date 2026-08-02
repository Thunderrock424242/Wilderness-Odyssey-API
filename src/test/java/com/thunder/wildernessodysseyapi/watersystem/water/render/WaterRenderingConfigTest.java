package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies quality and renderer-aware limits for the expensive optical pass. */
class WaterRenderingConfigTest {

    @Test
    void clockTideInformationDefaultsToContextualVisibility() {
        assertTrue(WaterRenderingConfig.SHOW_CLOCK_TIDE_TOOLTIP.getDefault());
        assertTrue(WaterRenderingConfig.SHOW_CONTEXTUAL_CLOCK_TIDE_DISPLAY.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_AMBIENT_WATER_PARTICLES.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_PERSISTENT_WAKE_FOAM.getDefault());
    }

    @Test
    void ambientParticlesAndPersistentFoamRespectQualityAndRendererCaps() {
        assertEquals(0, WaterRenderingConfig.ambientWaterParticleBudget(
                WaterRenderingConfig.WaterQuality.LOW, false, true));
        assertEquals(3, WaterRenderingConfig.ambientWaterParticleBudget(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false, true));
        assertEquals(2, WaterRenderingConfig.ambientWaterParticleBudget(
                WaterRenderingConfig.WaterQuality.CINEMATIC, true, true));
        assertEquals(0, WaterRenderingConfig.ambientWaterParticleBudget(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false, false));

        assertEquals(0.0f, WaterRenderingConfig.persistentWakeFoamScale(
                WaterRenderingConfig.WaterQuality.LOW, false, true));
        assertEquals(0.70f, WaterRenderingConfig.persistentWakeFoamScale(
                WaterRenderingConfig.WaterQuality.HIGH, true, true));
        assertEquals(1.0f, WaterRenderingConfig.persistentWakeFoamScale(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false, true));
    }

    @Test
    void optimizedProfileReducesBroadSsrWorkButKeepsHitRefinementEnabled() {
        assertEquals(12, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.HIGH, false, true));
        assertEquals(8, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.HIGH, true, true));
        assertEquals(20, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false, true));
        assertEquals(14, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.CINEMATIC, true, true));
    }

    @Test
    void disabledOrLowerQualityProfilesNeverMarchScreenReflections() {
        assertEquals(0, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false, false));
        assertEquals(0, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.MEDIUM, false, true));
    }

    @Test
    void optimizedProfileAlsoBoundsReflectionTravelDistance() {
        assertEquals(32.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.HIGH, false));
        assertEquals(28.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.HIGH, true));
        assertEquals(56.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false));
        assertEquals(48.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.CINEMATIC, true));
    }
}
