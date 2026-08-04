package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies sound cadence responds monotonically to river energy. */
class RiverSoundscapeModelTest {

    @Test
    void floodsAndConfluencesIncreaseIntensityAndShortenCadence() {
        float quiet = RiverSoundscapeModel.intensity(0.12f, 0.10f, false, false, 0.0f);
        float active = RiverSoundscapeModel.intensity(0.82f, 0.90f, true, true, 0.7f);

        assertTrue(active > quiet);
        assertTrue(RiverSoundscapeModel.intervalTicks(active)
                < RiverSoundscapeModel.intervalTicks(quiet));
    }
}
