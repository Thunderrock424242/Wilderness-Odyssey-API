package com.thunder.wildernessodysseyapi.meteor.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that natural meteors remain enabled but exceptionally rare by default. */
class MeteorConfigTest {

    private static final double TICKS_PER_HOUR = 72_000.0D;

    @Test
    void naturalMeteorDefaultsAverageAtLeastOneWeekOfActivePlay() {
        assertTrue(MeteorConfig.NATURAL_EVENTS_ENABLED.getDefault());
        assertEquals(72_000, MeteorConfig.EVENT_CHECK_INTERVAL_TICKS.getDefault());
        assertEquals(168, MeteorConfig.EVENT_CHANCE_PER_CHECK.getDefault());

        double expectedActiveHours = MeteorConfig.EVENT_CHECK_INTERVAL_TICKS.getDefault()
                / TICKS_PER_HOUR
                * MeteorConfig.EVENT_CHANCE_PER_CHECK.getDefault();
        assertTrue(expectedActiveHours >= 24.0D * 7.0D);
    }
}
