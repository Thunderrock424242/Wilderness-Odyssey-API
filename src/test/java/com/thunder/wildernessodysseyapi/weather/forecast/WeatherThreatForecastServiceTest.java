package com.thunder.wildernessodysseyapi.weather.forecast;

import com.thunder.wildernessodysseyapi.weather.api.WeatherThreat;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemTracker;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers threat tiers, movement rejection, dissipation, and competing systems. */
class WeatherThreatForecastServiceTest {

    private static final WeatherSystemTracker.TrackingSettings SETTINGS =
            WeatherSystemTracker.TrackingSettings.DEFAULT;

    @Test
    void classifiesLightRainRainThunderstormAndSevereStorm() {
        assertEquals(WeatherThreat.LIGHT_RAIN, forecast(system(1L, WeatherSystemType.STORM,
                WeatherSystemStage.MATURE, 0.25, -700.0, new WindVector(1.0, 0.0))).type());
        assertEquals(WeatherThreat.RAIN, forecast(system(2L, WeatherSystemType.STORM,
                WeatherSystemStage.MATURE, 0.40, -700.0, new WindVector(1.0, 0.0))).type());
        assertEquals(WeatherThreat.THUNDERSTORM, forecast(system(3L, WeatherSystemType.STORM,
                WeatherSystemStage.MATURE, 0.60, -700.0, new WindVector(1.0, 0.0))).type());
        assertEquals(WeatherThreat.SEVERE_STORM, forecast(system(4L, WeatherSystemType.STORM,
                WeatherSystemStage.MATURE, 0.82, -700.0, new WindVector(1.0, 0.0))).type());
    }

    @Test
    void matureSevereIdentityBecomesExtremeWeather() {
        WeatherThreatForecast forecast = forecast(system(
                5L,
                WeatherSystemType.TORNADO,
                WeatherSystemStage.MATURE,
                0.84,
                -700.0,
                new WindVector(1.0, 0.0)
        ));

        assertEquals(WeatherThreat.EXTREME_WEATHER, forecast.type());
        assertTrue(forecast.incoming());
        assertEquals(5L, forecast.sourceSystemId());
    }

    @Test
    void movingAwayAndPathThatMissesAreIgnored() {
        TrackedWeatherSystem movingAway = system(
                6L, WeatherSystemType.STORM, WeatherSystemStage.MATURE,
                0.90, -700.0, new WindVector(-1.0, 0.0));
        TrackedWeatherSystem passingNorth = new TrackedWeatherSystem(
                7L, WeatherSystemType.STORM, WeatherSystemStage.MATURE,
                -700.0, 500.0, 100.0, 0.90, new WindVector(1.0, 0.0),
                0.8, 1_000L, 1_000L, 0L
        );

        WeatherThreatForecast forecast = WeatherThreatForecastService.forecast(
                0.0, 0.0, 7_200, List.of(movingAway, passingNorth), SETTINGS);

        assertFalse(forecast.incoming());
        assertEquals(WeatherThreat.NONE, forecast.type());
    }

    @Test
    void weakeningSystemThatDissipatesBeforeArrivalIsIgnored() {
        TrackedWeatherSystem weakening = system(
                8L, WeatherSystemType.STORM, WeatherSystemStage.WEAKENING,
                0.80, -1_000.0, new WindVector(1.0, 0.0));

        assertEquals(WeatherThreat.NONE, forecast(weakening).type());
    }

    @Test
    void strongestRelevantSystemWinsWhenSeveralCellsAreNearby() {
        TrackedWeatherSystem nearbyRain = system(
                9L, WeatherSystemType.STORM, WeatherSystemStage.MATURE,
                0.40, -400.0, new WindVector(1.0, 0.0));
        TrackedWeatherSystem laterSevere = system(
                10L, WeatherSystemType.STORM, WeatherSystemStage.MATURE,
                0.82, -800.0, new WindVector(1.0, 0.0));

        WeatherThreatForecast forecast = WeatherThreatForecastService.forecast(
                0.0, 0.0, 7_200, List.of(nearbyRain, laterSevere), SETTINGS);

        assertEquals(WeatherThreat.SEVERE_STORM, forecast.type());
        assertEquals(10L, forecast.sourceSystemId());
    }

    private static WeatherThreatForecast forecast(TrackedWeatherSystem system) {
        return WeatherThreatForecastService.forecast(
                0.0, 0.0, 7_200, List.of(system), SETTINGS);
    }

    private static TrackedWeatherSystem system(
            long id,
            WeatherSystemType type,
            WeatherSystemStage stage,
            double intensity,
            double centerX,
            WindVector motion
    ) {
        return new TrackedWeatherSystem(
                id,
                type,
                stage,
                centerX,
                0.0,
                100.0,
                intensity,
                motion,
                0.75,
                1_000L,
                1_000L,
                0L
        );
    }
}
