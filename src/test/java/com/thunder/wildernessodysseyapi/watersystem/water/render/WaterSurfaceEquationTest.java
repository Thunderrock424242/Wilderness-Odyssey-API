package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that snapshot camera sampling uses the authored Gerstner spectra. */
class WaterSurfaceEquationTest {

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
        expectedWaves += GerstnerWaveProfile.POND.sampleAt(
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
}
