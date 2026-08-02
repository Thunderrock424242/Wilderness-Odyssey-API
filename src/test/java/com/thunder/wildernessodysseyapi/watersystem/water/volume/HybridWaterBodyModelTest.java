package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Verifies canonical current is retained when wave and tide motion are composed. */
class HybridWaterBodyModelTest {

    private static final WaveSurfaceSample WAVE = new WaveSurfaceSample(
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.2f, 0.0f, -0.3f
    );

    @Test
    void combinesCanonicalOrbitalAndOceanTideCurrents() {
        float[] flow = HybridWaterBodyModel.combineFlow(
                WaterBodyClassifier.WaterType.OCEAN,
                WAVE,
                1.0f,
                0.5f,
                0.4f,
                0.5f,
                -0.25f
        );

        assertArrayEquals(new float[]{1.4f, 0.1f}, flow, 1.0e-6f);
    }

    @Test
    void riverRetainsCanonicalAndOrbitalCurrentWithoutOceanTide() {
        float[] flow = HybridWaterBodyModel.combineFlow(
                WaterBodyClassifier.WaterType.RIVER,
                WAVE,
                1.0f,
                0.5f,
                9.0f,
                1.0f,
                1.0f
        );

        assertArrayEquals(new float[]{1.2f, 0.2f}, flow, 1.0e-6f);
    }
}
