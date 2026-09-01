package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
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
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_WAVES.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_RUN_UP.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_FOAM.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_WETNESS.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_SPRAY.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_AUDIO.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_WEATHER_INFLUENCE.getDefault());
        assertTrue(WaterRenderingConfig.ENABLE_COASTAL_SEASON_INFLUENCE.getDefault());
        assertTrue(WaterRenderingConfig.AUTO_DETECT_WATER_QUALITY.getDefault());
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
    void disabledOrLowQualityProfilesNeverMarchScreenReflections() {
        assertEquals(0, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false, false));
        assertEquals(0, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.LOW, false, true));
        assertEquals(6, WaterRenderingConfig.screenSpaceReflectionSteps(
                WaterRenderingConfig.WaterQuality.MEDIUM, false, true));
    }

    @Test
    void optimizedProfileAlsoBoundsReflectionTravelDistance() {
        assertEquals(20.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.MEDIUM, false));
        assertEquals(16.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.MEDIUM, true));
        assertEquals(32.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.HIGH, false));
        assertEquals(28.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.HIGH, true));
        assertEquals(56.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false));
        assertEquals(48.0f, WaterRenderingConfig.screenSpaceReflectionDistance(
                WaterRenderingConfig.WaterQuality.CINEMATIC, true));
    }

    @Test
    void renderThreadMeshBudgetsScaleWithoutAllowingStreamingBursts() {
        assertEquals(1, WaterRenderingConfig.snapshotMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.LOW, false));
        assertEquals(4, WaterRenderingConfig.snapshotMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.HIGH, false));
        assertEquals(3, WaterRenderingConfig.snapshotMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.HIGH, true));
        assertEquals(6, WaterRenderingConfig.snapshotMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false));
        assertEquals(3_500_000L, WaterRenderingConfig.snapshotMeshRebuildTimeBudgetNanos(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false));
    }

    @Test
    void sphExtractionBudgetKeepsLocalDetailFairAndBounded() {
        assertEquals(0, WaterRenderingConfig.sphMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.MEDIUM, false));
        assertEquals(2, WaterRenderingConfig.sphMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.HIGH, false));
        assertEquals(1, WaterRenderingConfig.sphMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.HIGH, true));
        assertEquals(3, WaterRenderingConfig.sphMeshRebuildsPerFrame(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false));
    }

    @Test
    void waveDisplacementNeverChangesWithPresentationQuality() {
        assertEquals(4, WaterRenderingConfig.authoritativeWaveTrainCount(
                WaterBodyClassifier.WaterType.OCEAN));
        assertEquals(4, WaterRenderingConfig.authoritativeWaveTrainCount(
                WaterBodyClassifier.WaterType.COAST));
        assertEquals(3, WaterRenderingConfig.authoritativeWaveTrainCount(
                WaterBodyClassifier.WaterType.RIVER));
        assertEquals(3, WaterRenderingConfig.authoritativeWaveTrainCount(
                WaterBodyClassifier.WaterType.LAKE));
        assertEquals(2, WaterRenderingConfig.authoritativeWaveTrainCount(
                WaterBodyClassifier.WaterType.POND));
    }

    @Test
    void lowQualityRetainsABoundedEntrySplash() {
        assertEquals(3, WaterRenderingConfig.splashParticleBudget(
                WaterRenderingConfig.WaterQuality.LOW, 8));
        assertEquals(0, WaterRenderingConfig.splashParticleBudget(
                WaterRenderingConfig.WaterQuality.LOW, 0));
    }

    @Test
    void coastalDetailDegradesByGeometryBudgetWithoutChangingTheWaveClock() {
        assertEquals(8, WaterRenderingConfig.coastalSegmentBudget(
                WaterRenderingConfig.WaterQuality.LOW, false));
        assertEquals(0, WaterRenderingConfig.coastalQuadBudget(
                WaterRenderingConfig.WaterQuality.LOW, false));
        assertEquals(384, WaterRenderingConfig.coastalQuadBudget(
                WaterRenderingConfig.WaterQuality.HIGH, false));
        assertEquals(224, WaterRenderingConfig.coastalQuadBudget(
                WaterRenderingConfig.WaterQuality.HIGH, true));
        assertEquals(24, WaterRenderingConfig.coastalRunUpDetailDistanceBlocks(
                WaterRenderingConfig.WaterQuality.HIGH, false));
        assertEquals(14, WaterRenderingConfig.coastalSprayDistanceBlocks(
                WaterRenderingConfig.WaterQuality.HIGH, true));
        assertEquals(48, WaterRenderingConfig.coastalSegmentBudget(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false));
        assertEquals(768, WaterRenderingConfig.coastalQuadBudget(
                WaterRenderingConfig.WaterQuality.CINEMATIC, false));
    }

    @Test
    void directCliffSprayRemainsVeryNearAndQualityBounded() {
        assertEquals(0, WaterRenderingConfig.coastalSprayDistanceBlocks(
                WaterRenderingConfig.WaterQuality.LOW, false));
        assertEquals(14, WaterRenderingConfig.coastalSprayDistanceBlocks(
                WaterRenderingConfig.WaterQuality.HIGH, true));
    }
}
