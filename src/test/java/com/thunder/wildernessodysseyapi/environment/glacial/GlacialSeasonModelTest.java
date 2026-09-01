package com.thunder.wildernessodysseyapi.environment.glacial;

import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlacialSeasonModelTest {

    @Test
    void mapsTemperateCycleQuartersWithoutDependingOnCalendarClasses() {
        assertEquals(GlacialSeason.SPRING, evaluate(0.05).season());
        assertEquals(GlacialSeason.SUMMER, evaluate(0.30).season());
        assertEquals(GlacialSeason.AUTUMN, evaluate(0.55).season());
        assertEquals(GlacialSeason.WINTER, evaluate(0.80).season());
        assertTrue(evaluate(0.30).meltFraction() > evaluate(0.80).meltFraction());
    }

    @Test
    void missingCalendarUsesStablePolarFallback() {
        GlacialSeasonSnapshot snapshot = GlacialSeasonModel.evaluate(SeasonalClimateState.NONE);

        assertEquals(GlacialSeason.POLAR_COLD, snapshot.season());
        assertFalse(snapshot.calendarAvailable());
        assertEquals(0.08, snapshot.meltFraction(), 0.0001);
    }

    @Test
    void debugOverrideIsExplicitAndDoesNotClaimCalendarAuthority() {
        GlacialSeasonSnapshot snapshot = GlacialSeasonModel.override(GlacialSeason.SUMMER);

        assertEquals(GlacialSeason.SUMMER, snapshot.season());
        assertTrue(snapshot.debugOverride());
        assertFalse(snapshot.calendarAvailable());
        assertTrue(snapshot.meltFraction() > 0.9);
    }

    private static GlacialSeasonSnapshot evaluate(double phase) {
        return GlacialSeasonModel.evaluate(new SeasonalClimateState(
                0.0, 1.0, 0.0, 0.0, true, phase));
    }
}
