package com.thunder.wildernessodysseyapi.weather.wildfire;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereEnvironment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that wildfire eligibility requires both exceptional dryness and fire season. */
class WildfireRiskModelTest {

    @Test
    void extremeSummerDroughtWithDryGroundAndWindIsEligible() {
        WildfireRiskModel.RiskProfile risk = WildfireRiskModel.evaluate(
                dryWeather(44.0, 0.03, 1.25, 0.82, SurfaceWeatherState.DRY),
                environment(1.0, true)
        );

        assertTrue(risk.eligible());
        assertTrue(risk.drought() >= 0.80);
        assertTrue(risk.risk() > 0.75);
        assertTrue(risk.calendarAvailable());
    }

    @Test
    void winterRainOrRecentlyWetGroundBlocksIgnition() {
        WeatherSample dry = dryWeather(44.0, 0.03, 1.25, 0.82, SurfaceWeatherState.DRY);
        WeatherSample wetGround = dryWeather(
                44.0,
                0.03,
                1.25,
                0.82,
                new SurfaceWeatherState(0.20, 0.0, 0.0, 0.0)
        );
        WeatherSample rain = new WeatherSample(
                44.0,
                0.03,
                1.25,
                new WindVector(0.82, 0.0),
                0.40,
                0.30,
                0.20,
                0.50,
                PrecipitationType.RAIN,
                0.0,
                0.0,
                new WindVector(0.82, 0.0),
                SurfaceWeatherState.DRY
        );

        assertFalse(WildfireRiskModel.evaluate(dry, environment(0.0, true)).eligible());
        assertFalse(WildfireRiskModel.evaluate(wetGround, environment(1.0, true)).eligible());
        assertFalse(WildfireRiskModel.evaluate(rain, environment(1.0, true)).eligible());
    }

    @Test
    void missingCalendarUsesOnlyStricterExtremeWeatherFallback() {
        WeatherSample extreme = dryWeather(44.0, 0.03, 1.25, 0.82, SurfaceWeatherState.DRY);
        WeatherSample merelyHot = dryWeather(36.0, 0.10, 1.18, 0.50, SurfaceWeatherState.DRY);

        WildfireRiskModel.RiskProfile extremeRisk = WildfireRiskModel.evaluate(
                extreme,
                environment(0.0, false)
        );
        assertTrue(extremeRisk.eligible());
        assertFalse(extremeRisk.calendarAvailable());
        assertFalse(WildfireRiskModel.evaluate(merelyHot, environment(0.0, false)).eligible());
    }

    private static WeatherSample dryWeather(
            double temperature,
            double humidity,
            double pressure,
            double wind,
            SurfaceWeatherState surface
    ) {
        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                new WindVector(wind, 0.0),
                0.0,
                0.20,
                0.0,
                0.0,
                PrecipitationType.NONE,
                0.0,
                0.0,
                new WindVector(wind, 0.0),
                surface
        );
    }

    private static AtmosphereEnvironment environment(double fireSeason, boolean calendarAvailable) {
        return new AtmosphereEnvironment(
                15.0,
                0.40,
                64.0,
                0.0,
                0.5,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                fireSeason,
                calendarAvailable
        );
    }
}
