package com.thunder.wildernessodysseyapi.weather.integration.season;

import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.SeasonalWeatherInfluence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies both optional season adapters share bounded Wilderness-owned balance. */
class SeasonCycleProfileTest {

    private static final WeatherConfig.SeasonSettings SETTINGS =
            new WeatherConfig.SeasonSettings(true, 8.0, 0.12, 0.18);

    @Test
    void temperateSummerWarmsAndWinterCools() {
        SeasonalWeatherInfluence.SeasonalOffset summer =
                SeasonCycleProfile.temperate(0.375, SETTINGS);
        SeasonalWeatherInfluence.SeasonalOffset winter =
                SeasonCycleProfile.temperate(0.875, SETTINGS);

        assertEquals(8.0, summer.temperatureCelsius(), 1.0E-9);
        assertEquals(-8.0, winter.temperatureCelsius(), 1.0E-9);
        assertTrue(summer.storminess() > winter.storminess());
        assertTrue(summer.evaporationMultiplier() > winter.evaporationMultiplier());
    }

    @Test
    void tropicalWetAndDrySeasonsPullMoistureInOppositeDirections() {
        SeasonalWeatherInfluence.SeasonalOffset wet =
                SeasonCycleProfile.tropical("MID_WET", SETTINGS);
        SeasonalWeatherInfluence.SeasonalOffset dry =
                SeasonCycleProfile.tropical("MID_DRY", SETTINGS);

        assertTrue(wet.humidity() > 0.0);
        assertTrue(wet.storminess() > 0.0);
        assertTrue(dry.humidity() < 0.0);
        assertTrue(dry.storminess() < 0.0);
    }

    @Test
    void disabledIntegrationReturnsNeutralInfluence() {
        WeatherConfig.SeasonSettings disabled =
                new WeatherConfig.SeasonSettings(false, 8.0, 0.12, 0.18);

        assertEquals(
                SeasonalWeatherInfluence.SeasonalOffset.NONE,
                SeasonCycleProfile.temperate(0.375, disabled)
        );
        assertEquals(
                SeasonalWeatherInfluence.SeasonalOffset.NONE,
                SeasonCycleProfile.tropical("MID_WET", disabled)
        );
    }
}
