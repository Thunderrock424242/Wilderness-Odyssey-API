package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that snapshot camera sampling uses the authored Gerstner spectra. */
class WaterSurfaceEquationTest {

    @Test
    void frozenSurfaceDampsWavesAndImpulsesButKeepsBaseAndTide() {
        for (float frozen : new float[] {0.0f, 0.25f, 0.5f, 1.0f}) {
            float wave = GerstnerWaveProfile.OCEAN.sampleAt(
                    11, 17, 4.0, 4, WaveSpectrumState.NEUTRAL, 0, 0, 3).height();
            float actual = WaterSurfaceEquation.snapshotSurfaceHeight(63, 11, 17, 4,
                    WaveSpectrumState.NEUTRAL, WaveSpectrumState.NEUTRAL,
                    4, 0, 0, 1, 0, 0, 0, 0, 1, 0.2f, 0.1f, 3, frozen);
            assertEquals(63.2f + (wave + 0.1f) * (1.0f - frozen * 0.94f), actual, 0.00001f);
        }
    }

    @Test
    void frozenSurfaceRetainsDryBoundaryAnchorAndBoundsInvalidFractions() {
        for (float frozen : new float[] {0, 1, -10, 10, Float.NaN, Float.POSITIVE_INFINITY}) {
            float anchored = WaterSurfaceEquation.snapshotSurfaceHeight(63, 11, 17, 4,
                    WaveSpectrumState.NEUTRAL, WaveSpectrumState.NEUTRAL,
                    4, 0, 0, 1, 0, 0, 0, 0, 0, 0.2f, 0.1f, 3, frozen);
            assertEquals(63.0f, anchored);
            float moving = WaterSurfaceEquation.snapshotSurfaceHeight(63, 11, 17, 4,
                    WaveSpectrumState.NEUTRAL, WaveSpectrumState.NEUTRAL,
                    0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 3, frozen);
            assertTrue(Float.isFinite(moving));
            assertTrue(moving >= 63.015f && moving <= 63.25f);
        }
    }

    @Test
    void exactFrameTickRetainsWakeSubstepsInOldWorlds() {
        long gameTime = 1L << 28;
        assertEquals(gameTime + 0.25, WaterAnimationClock.sampleTick(gameTime, 0.25f), 0.0);
        assertEquals(gameTime + 0.75, WaterAnimationClock.sampleTick(gameTime, 0.75f), 0.0);
        assertEquals(gameTime, WaterAnimationClock.sampleTick(gameTime, Float.NaN), 0.0);
        assertEquals(gameTime + 1.0, WaterAnimationClock.sampleTick(gameTime, 4.0f), 0.0);
        assertEquals(0.0, WaterAnimationClock.sampleTick(-1, -1), 0.0);
    }

    @Test
    void immersionMatchesNativeShallowOceanCrest() {
        var spectrum = WaveSpectrumState.NEUTRAL;
        float expected = 63.0f + GerstnerWaveProfile.OCEAN.sampleAt(
                11, 17, 4.0, 4, spectrum, 0, 0, 3).height();
        float actual = WaterSurfaceEquation.snapshotSurfaceHeight(63, 11, 17, 4,
                spectrum, spectrum, 4, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 3);
        assertEquals(expected, actual, 0.00001f);
    }

    @Test
    void snapshotHeightMatchesWeightedCpuProfilesAndTransientDisplacement() {
        float x = 31.25f;
        float z = -12.75f;
        float time = 8.4f;
        WaveSpectrumState spectrum = new WaveSpectrumState(1.4f, 0.8f, 0.4f, 0.9f, 0.55f);
        float oceanWeight = 0.55f;
        float riverWeight = 0.30f;
        float pondWeight = 0.15f;
        float expectedWaves = GerstnerWaveProfile.OCEAN.sampleAt(x, z, time, 3, spectrum).height()
                * oceanWeight;
        expectedWaves += GerstnerWaveProfile.RIVER.sampleAt(
                x, z, time, 2, WaveSpectrumState.NEUTRAL).height() * riverWeight;
        expectedWaves += GerstnerWaveProfile.LAKE.sampleAt(
                x, z, time, 1, WaveSpectrumState.NEUTRAL).height() * pondWeight;

        float height = WaterSurfaceEquation.snapshotSurfaceHeight(
                63.0f,
                x,
                z,
                time,
                spectrum,
                3,
                2,
                1,
                oceanWeight,
                riverWeight,
                pondWeight,
                0.2f,
                -0.08f
        );

        assertEquals(63.0f + expectedWaves + 0.2f * oceanWeight - 0.08f,
                height, 1.0e-5f);
    }

    @Test
    void transientDisplacementIsBounded() {
        float height = WaterSurfaceEquation.snapshotSurfaceHeight(
                10.0f,
                0.0f,
                0.0f,
                0.0f,
                WaveSpectrumState.NEUTRAL,
                0,
                0,
                0,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                4.0f
        );

        assertEquals(10.25f, height, 1.0e-6f);
    }

    @Test
    void enclosedWaterUsesItsExplicitWeatherSpectrum() {
        WaveSpectrumState enclosed = new WaveSpectrumState(
                1.6f, 1.9f, 0.6f, 0.8f, 0.7f);
        double x = 6.25;
        double z = -4.75;
        double time = 3.2;
        float expected = GerstnerWaveProfile.LAKE.sampleAt(
                x, z, time, 3, enclosed).height();

        float height = WaterSurfaceEquation.snapshotSurfaceHeight(
                30.0f,
                x,
                z,
                time,
                WaveSpectrumState.NEUTRAL,
                enclosed,
                0,
                0,
                3,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                0.0f
        );

        assertEquals(30.0f + expected, height, 1.0e-6f);
    }

    @Test
    void currentAlignedRiverAndContinuityMirrorSnapshotShader() {
        double x = 22.375;
        double z = -9.625;
        double time = 12.45;
        float currentX = 0.0f;
        float currentZ = 1.5f;
        float expectedRiver = GerstnerWaveProfile.RIVER.sampleAt(
                x,
                z,
                time,
                3,
                WaveSpectrumState.NEUTRAL,
                currentX,
                currentZ
        ).height();

        float full = WaterSurfaceEquation.snapshotSurfaceHeight(
                40.0f, x, z, time, WaveSpectrumState.NEUTRAL,
                0, 3, 0,
                0.0f, 1.0f, 0.0f,
                currentX, currentZ, 0.92f,
                0.0f, 0.08f
        );
        float boundary = WaterSurfaceEquation.snapshotSurfaceHeight(
                40.0f, x, z, time, WaveSpectrumState.NEUTRAL,
                0, 3, 0,
                0.0f, 1.0f, 0.0f,
                currentX, currentZ, 0.18f,
                0.0f, 0.08f
        );

        assertEquals(40.0f + expectedRiver + 0.08f, full, 1.0e-6f);
        assertEquals(40.0f, boundary, 1.0e-6f);
    }
}
