package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies client hull presentation has stable angular momentum. */
class BoatTiltStoreTest {

    @Test
    void springDamperConvergesWithoutSnappingToTheWaveSlope() {
        float[] response = new float[6];

        BoatTiltStore.integrateResponse(response, 12.0f, -8.0f, 0.4f);
        assertTrue(response[0] > 0.0f && response[0] < 12.0f);
        assertTrue(response[1] < 0.0f && response[1] > -8.0f);
        assertTrue(response[2] > 0.0f && response[2] < 0.4f);

        for (int tick = 0; tick < 120; tick++) {
            BoatTiltStore.integrateResponse(response, 12.0f, -8.0f, 0.4f);
        }
        assertTrue(Math.abs(response[0] - 12.0f) < 0.05f);
        assertTrue(Math.abs(response[1] + 8.0f) < 0.05f);
        assertTrue(Math.abs(response[2] - 0.4f) < 0.01f);
    }
}
