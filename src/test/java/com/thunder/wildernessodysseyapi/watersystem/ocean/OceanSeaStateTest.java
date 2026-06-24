package com.thunder.wildernessodysseyapi.watersystem.ocean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded network-safe sea-state behavior without a loaded world. */
class OceanSeaStateTest {

    @Test
    void sanitizesEnvironmentalValuesAndNormalizesWind() {
        OceanSeaState.Sample sample = new OceanSeaState.Sample(
                4.0f,
                3.0f,
                4.0f,
                90.0f,
                -2.0f,
                9.0f,
                3.0f,
                -1.0f
        );

        assertEquals(1.0f, sample.strength());
        assertEquals(40.0f, sample.windSpeed());
        assertEquals(0.0f, sample.swellScale());
        assertEquals(4.0f, sample.chopScale());
        assertEquals(1.0f, sample.directionBlend());
        assertEquals(0.0f, sample.breakingStrength());
        assertEquals(1.0f, (float) Math.hypot(
                sample.windDirectionX(),
                sample.windDirectionZ()
        ), 1.0e-6f);
    }

    @Test
    void interpolationMovesTowardServerTarget() {
        OceanSeaState.Sample calm = OceanSeaState.CALM;
        OceanSeaState.Sample storm = new OceanSeaState.Sample(
                1.0f, 0.0f, 1.0f, 15.0f, 1.7f, 2.3f, 0.82f, 1.0f
        );

        OceanSeaState.Sample blended = calm.interpolate(storm, 0.5f);

        assertTrue(blended.strength() > calm.strength());
        assertTrue(blended.strength() < storm.strength());
        assertEquals(1.0f, (float) Math.hypot(
                blended.windDirectionX(),
                blended.windDirectionZ()
        ), 1.0e-6f);
    }
}
