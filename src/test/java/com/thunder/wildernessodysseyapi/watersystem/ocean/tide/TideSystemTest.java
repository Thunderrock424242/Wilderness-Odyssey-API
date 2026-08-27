package com.thunder.wildernessodysseyapi.watersystem.ocean.tide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the lunar amplitude envelope and coastline-oriented current model. */
class TideSystemTest {

    private static final float EPSILON = 1.0e-6f;

    @Test
    void fullAndNewMoonsProduceSpringTides() {
        assertEquals(TideSystem.MAX_SPRING_AMPLITUDE, TideSystem.getLunarAmplitude(0), EPSILON);
        assertEquals(TideSystem.MAX_SPRING_AMPLITUDE, TideSystem.getLunarAmplitude(4), EPSILON);
    }

    @Test
    void quarterMoonsProduceNeapTides() {
        assertEquals(TideSystem.MAX_NEAP_AMPLITUDE, TideSystem.getLunarAmplitude(2), EPSILON);
        assertEquals(TideSystem.MAX_NEAP_AMPLITUDE, TideSystem.getLunarAmplitude(6), EPSILON);
    }

    @Test
    void lunarAmplitudeWrapsAndRemainsSymmetric() {
        assertEquals(TideSystem.getLunarAmplitude(0), TideSystem.getLunarAmplitude(8), EPSILON);
        assertEquals(TideSystem.getLunarAmplitude(1), TideSystem.getLunarAmplitude(3), EPSILON);
        assertEquals(TideSystem.getLunarAmplitude(5), TideSystem.getLunarAmplitude(7), EPSILON);
    }

    @Test
    void floodCurrentMovesTowardLandAndEbbMovesOffshore() {
        float[] flood = {0.0f, 0.0f};
        float[] ebb = {0.0f, 0.0f};
        TideSystem.addTidalCurrent(0.02f, 3.0f, 4.0f, flood);
        TideSystem.addTidalCurrent(-0.02f, 3.0f, 4.0f, ebb);

        assertEquals(0.012f, flood[0], EPSILON);
        assertEquals(0.016f, flood[1], EPSILON);
        assertEquals(-0.012f, ebb[0], EPSILON);
        assertEquals(-0.016f, ebb[1], EPSILON);
    }

    @Test
    void missingCoastlineOrSlackWaterCannotInventAnAxisCurrent() {
        float[] noCoast = {0.0f, 0.0f};
        float[] slack = {0.0f, 0.0f};
        TideSystem.addTidalCurrent(0.02f, 0.0f, 0.0f, noCoast);
        TideSystem.addTidalCurrent(0.0f, 1.0f, 0.0f, slack);

        assertEquals(0.0f, noCoast[0], EPSILON);
        assertEquals(0.0f, noCoast[1], EPSILON);
        assertEquals(0.0f, slack[0], EPSILON);
        assertEquals(0.0f, slack[1], EPSILON);
    }
}
