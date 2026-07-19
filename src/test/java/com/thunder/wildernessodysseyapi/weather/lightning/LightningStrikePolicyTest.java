package com.thunder.wildernessodysseyapi.weather.lightning;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies localized strike cadence without requiring a loaded Minecraft world. */
class LightningStrikePolicyTest {

    @Test
    void ineligibleOrDisabledWeatherNeverRequestsLightning() {
        WeatherConfig.LightningSettings enabled = settings(true, 0.20, 4);
        WeatherSample weakStorm = storm(0.90, 0.54, 0.90);

        assertEquals(0.0, LightningStrikePolicy.strikeChance(WeatherSample.CLEAR, enabled));
        assertEquals(0.0, LightningStrikePolicy.strikeChance(weakStorm, enabled));
        assertEquals(
                0.0,
                LightningStrikePolicy.strikeChance(storm(1.0, 1.0, 1.0), settings(false, 1.0, 4))
        );
    }

    @Test
    void strongerThunderIncreasesOneBoundedPerCheckChance() {
        WeatherConfig.LightningSettings settings = settings(true, 0.40, 4);
        double moderate = LightningStrikePolicy.strikeChance(storm(0.70, 0.70, 0.70), settings);
        double severe = LightningStrikePolicy.strikeChance(storm(1.0, 1.0, 1.0), settings);

        assertTrue(moderate > 0.0);
        assertTrue(severe > moderate);
        assertEquals(0.40, severe, 1.0E-9);
    }

    @Test
    void cooldownBoundaryAndCandidateBudgetAreExact() {
        WeatherConfig.LightningSettings settings = settings(true, 0.20, 12);

        assertTrue(LightningStrikePolicy.cooldownElapsed(600L, 600L));
        assertFalse(LightningStrikePolicy.cooldownElapsed(599L, 600L));
        assertEquals(0, LightningStrikePolicy.candidateBudget(0, settings));
        assertEquals(12, LightningStrikePolicy.candidateBudget(1, settings));
        assertEquals(12, LightningStrikePolicy.candidateBudget(100, settings));
    }

    private static WeatherConfig.LightningSettings settings(
            boolean enabled,
            double maximumChance,
            int maxCandidates
    ) {
        return new WeatherConfig.LightningSettings(
                enabled,
                20,
                120,
                600,
                96,
                maxCandidates,
                maximumChance
        );
    }

    private static WeatherSample storm(
            double precipitation,
            double stormEnergy,
            double instability
    ) {
        return new WeatherSample(
                18.0,
                0.95,
                0.94,
                WindVector.ZERO,
                0.90,
                instability,
                stormEnergy,
                precipitation,
                PrecipitationType.RAIN
        );
    }
}
