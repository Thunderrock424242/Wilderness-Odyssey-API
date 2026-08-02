package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies stable tide text and contextual visibility for the vanilla clock. */
class TideClockDisplayModelTest {

    @Test
    void createsLocaleStableRisingReadout() {
        TideClockDisplayModel.TideReadout readout = TideClockDisplayModel.create(
                sample(0.426f, 0.002f, 0),
                "Spring Flooding"
        );

        assertEquals("Spring Flooding", readout.tideName());
        assertEquals("+0.43", readout.offsetBlocks());
        assertEquals("tide.wildernessodysseyapi.trend.rising",
                readout.trendTranslationKey());
        assertEquals("tide.wildernessodysseyapi.moon.full",
                readout.moonTranslationKey());
    }

    @Test
    void representsFallingAndTurningTidesWithoutCorruptedSymbols() {
        TideClockDisplayModel.TideReadout falling = TideClockDisplayModel.create(
                sample(-1.25f, -0.002f, 4),
                "Spring Ebbing"
        );
        TideClockDisplayModel.TideReadout turning = TideClockDisplayModel.create(
                sample(-0.0001f, 0.0004f, 7),
                "Mixed Low Tide"
        );

        assertEquals("-1.25", falling.offsetBlocks());
        assertEquals("tide.wildernessodysseyapi.trend.falling",
                falling.trendTranslationKey());
        assertEquals("tide.wildernessodysseyapi.moon.new",
                falling.moonTranslationKey());
        assertEquals("+0.00", turning.offsetBlocks());
        assertEquals("tide.wildernessodysseyapi.trend.turning",
                turning.trendTranslationKey());
        assertEquals("tide.wildernessodysseyapi.moon.waxing_gibbous",
                turning.moonTranslationKey());
    }

    @Test
    void contextualDisplayAcceptsEitherHandOrTargetedFrameButNotUnrelatedViews() {
        assertTrue(TideClockDisplayModel.shouldShowContextualDisplay(true, false, false));
        assertTrue(TideClockDisplayModel.shouldShowContextualDisplay(false, true, false));
        assertTrue(TideClockDisplayModel.shouldShowContextualDisplay(false, false, true));
        assertFalse(TideClockDisplayModel.shouldShowContextualDisplay(false, false, false));
    }

    private static TideSystem.TideSample sample(float offset, float rate, int moonPhase) {
        return new TideSystem.TideSample(offset, rate, 1.0f, 0.5f,
                moonPhase, 0.5f, 0.0f);
    }
}
