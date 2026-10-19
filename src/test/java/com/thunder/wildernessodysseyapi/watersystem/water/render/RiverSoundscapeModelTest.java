package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies sound cadence responds monotonically to river energy. */
class RiverSoundscapeModelTest {

    @Test
    void waterfallAccentsUsePhysicalCurrentAndDropWhileStillFallsStayQuiet() {
        float stream = RiverSoundscapeModel.intensity(0.2f, 1.2f, false, false, 0,
                RiverChannelColumn.Kind.STREAM, 0);
        float fall = RiverSoundscapeModel.intensity(0.2f, 1.2f, false, false, 0,
                RiverChannelColumn.Kind.WATERFALL, 8);
        float still = RiverSoundscapeModel.intensity(0, 0, false, false, 0,
                RiverChannelColumn.Kind.WATERFALL, 16);
        assertTrue(fall > stream);
        assertTrue(RiverSoundscapeModel.intervalTicks(fall) < RiverSoundscapeModel.intervalTicks(stream));
        assertTrue(still == 0);
    }

    @Test
    void lakeDoesNotAcquireRapidNoiseFromAnAdjacentRiverCurrent() {
        float lake = RiverSoundscapeModel.intensity(0, 3, false, false, 0,
                RiverChannelColumn.Kind.LAKE, 0);
        assertTrue(lake == 0);
    }

    @Test
    void floodsAndConfluencesIncreaseIntensityAndShortenCadence() {
        float quiet = RiverSoundscapeModel.intensity(0.12f, 0.10f, false, false, 0.0f);
        float active = RiverSoundscapeModel.intensity(0.82f, 0.90f, true, true, 0.7f);

        assertTrue(active > quiet);
        assertTrue(RiverSoundscapeModel.intervalTicks(active)
                < RiverSoundscapeModel.intervalTicks(quiet));
    }
}
