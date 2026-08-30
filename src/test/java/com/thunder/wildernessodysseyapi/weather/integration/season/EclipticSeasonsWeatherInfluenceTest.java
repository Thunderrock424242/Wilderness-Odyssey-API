package com.thunder.wildernessodysseyapi.weather.integration.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies Ecliptic's 24 solar terms map safely into one weather year. */
class EclipticSeasonsWeatherInfluenceTest {

    @Test
    void firstSolarTermStartsNormalizedYear() {
        assertEquals(
                0.0,
                EclipticSeasonsWeatherInfluence.cyclePhase(0, 0, 6),
                1.0E-9
        );
    }

    @Test
    void calendarAdvancesWithinSolarTerm() {
        assertEquals(
                0.0625,
                EclipticSeasonsWeatherInfluence.cyclePhase(1, 3, 6),
                1.0E-9
        );
    }

    @Test
    void finalSolarTermRemainsInsideNormalizedYear() {
        double phase = EclipticSeasonsWeatherInfluence.cyclePhase(23, 5, 6);

        assertTrue(phase >= 23.0 / 24.0);
        assertTrue(phase < 1.0);
    }

    @Test
    void undocumentedSolarTermOrdinalIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EclipticSeasonsWeatherInfluence.cyclePhase(24, 0, 6)
        );
    }

    @Test
    void invalidTermTimingIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EclipticSeasonsWeatherInfluence.cyclePhase(0, 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EclipticSeasonsWeatherInfluence.cyclePhase(0, -1, 6)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EclipticSeasonsWeatherInfluence.cyclePhase(0, 6, 6)
        );
    }
}
