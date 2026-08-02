package com.thunder.wildernessodysseyapi.weather.integration.survival;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies optional survival mods receive bounded, non-duplicated weather exposure. */
class WeatherSurvivalExposureModelTest {

    @Test
    void shelteredEntitiesReceiveNoWeatherExposure() {
        WeatherSample heatWave = sample(44.0, 0.18, 1.14, 0.9, PrecipitationType.NONE);

        assertEquals(0.0, WeatherSurvivalExposureModel.coldSweatOffsetCelsius(
                heatWave, 24.0, false, 12.0
        ));
        assertEquals(0.0, WeatherSurvivalExposureModel.thirstExhaustionPerInterval(
                heatWave, false, false, 0.025
        ));
    }

    @Test
    void coldSweatOffsetTracksAirMassAndStormCoolingWithinConfiguredBound() {
        WeatherSample hot = sample(44.0, 0.30, 1.10, 0.8, PrecipitationType.NONE);
        WeatherSample blizzard = sample(-12.0, 0.96, 0.90, 1.0, PrecipitationType.SNOW);

        double hotOffset = WeatherSurvivalExposureModel.coldSweatOffsetCelsius(
                hot, 24.0, true, 12.0
        );
        double blizzardOffset = WeatherSurvivalExposureModel.coldSweatOffsetCelsius(
                blizzard, -2.0, true, 12.0
        );

        assertEquals(12.0, hotOffset);
        assertEquals(-12.0, blizzardOffset);
        assertEquals(12.0 / 25.0, WeatherSurvivalExposureModel.coldSweatOffsetMinecraftUnits(
                hot, 24.0, true, 12.0
        ));
    }

    @Test
    void thirstKeepsDryWeatherContributionButReducesHeatWhenColdSweatOwnsIt() {
        WeatherSample hotDry = sample(42.0, 0.16, 1.13, 0.85, PrecipitationType.NONE);
        double standalone = WeatherSurvivalExposureModel.thirstExhaustionPerInterval(
                hotDry, true, false, 0.025
        );
        double withColdSweat = WeatherSurvivalExposureModel.thirstExhaustionPerInterval(
                hotDry, true, true, 0.025
        );

        assertTrue(standalone > 0.0);
        assertTrue(withColdSweat > 0.0);
        assertTrue(withColdSweat < standalone);
        assertTrue(standalone <= 0.025);
    }

    @Test
    void mildWeatherAddsNoMeaningfulThirstPressure() {
        WeatherSample mild = sample(18.0, 0.55, 1.0, 0.1, PrecipitationType.NONE);

        assertEquals(0.0, WeatherSurvivalExposureModel.thirstExhaustionPerInterval(
                mild, true, false, 0.025
        ));
    }

    private static WeatherSample sample(
            double temperature,
            double humidity,
            double pressure,
            double wind,
            PrecipitationType precipitationType
    ) {
        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                new WindVector(wind, wind * 0.35),
                precipitationType == PrecipitationType.NONE ? 0.08 : 0.92,
                0.70,
                precipitationType == PrecipitationType.NONE ? 0.15 : 0.82,
                precipitationType == PrecipitationType.NONE ? 0.0 : 0.90,
                precipitationType
        );
    }
}
