package com.thunder.wildernessodysseyapi.weather.integration.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies Homeostatic's twelve sub-seasons map safely into one weather year. */
class HomeostaticSeasonsWeatherInfluenceTest {

    @Test
    void configuredCalendarUsesTickProgressWithinSubSeason() {
        assertEquals(
                0.0,
                HomeostaticSeasonsWeatherInfluence.cyclePhase(0, true, 72_000L, 72_000L),
                1.0E-9
        );
        assertEquals(
                0.125,
                HomeostaticSeasonsWeatherInfluence.cyclePhase(1, true, 72_000L, 36_000L),
                1.0E-9
        );
    }

    @Test
    void fixedOrRealtimeCalendarUsesReportedSubSeasonMidpoint() {
        assertEquals(
                0.375,
                HomeostaticSeasonsWeatherInfluence.cyclePhase(4, false, 72_000L, 0L),
                1.0E-9
        );
        assertEquals(
                0.875,
                HomeostaticSeasonsWeatherInfluence.cyclePhase(10, false, 72_000L, 600_000L),
                1.0E-9
        );
    }

    @Test
    void invalidTimingFallsBackToSubSeasonMidpoint() {
        assertEquals(
                0.375,
                HomeostaticSeasonsWeatherInfluence.cyclePhase(4, true, 0L, 0L),
                1.0E-9
        );
        assertEquals(
                0.375,
                HomeostaticSeasonsWeatherInfluence.cyclePhase(4, true, 72_000L, 72_001L),
                1.0E-9
        );
    }

    @Test
    void undocumentedSubSeasonOrdinalIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HomeostaticSeasonsWeatherInfluence.cyclePhase(12, true, 72_000L, 36_000L)
        );
    }
}
