package com.thunder.wildernessodysseyapi.weather.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies conservative evaporation weighting for partially loaded probe lattices. */
class WaterInfluenceSampleTest {

    @Test
    void partialProbeCoverageCannotRepresentAnEntireWetCell() {
        WaterInfluenceSample fullyObserved = new WaterInfluenceSample(
                1.0f,
                1.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f
        );
        WaterInfluenceSample quarterObserved = new WaterInfluenceSample(
                1.0f,
                1.0f,
                0.0f,
                0.0f,
                0.0f,
                0.25f
        );

        assertEquals(
                fullyObserved.moisturePotential() * 0.25f,
                quarterObserved.moisturePotential(),
                1.0E-6f
        );
    }
}
