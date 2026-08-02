package com.thunder.wildernessodysseyapi.weather.forecast;

import com.thunder.wildernessodysseyapi.weather.api.WeatherForecast;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherForecastServiceTest {

    @Test
    void selectsApproachingFrontAndCalculatesEta() {
        TrackedWeatherSystem approaching = new TrackedWeatherSystem(
                1L, WeatherSystemType.COLD_FRONT, WeatherSystemStage.MATURE,
                -1_000.0, 0.0, 300.0, 0.8, new WindVector(1.0, 0.0),
                0.7, 1_000L, 1_000L, 0L
        );
        WeatherForecast forecast = WeatherForecastService.forecast(
                WeatherSample.CLEAR, -0.02, 0.0, 0.0, List.of(approaching), 3.0
        );
        assertEquals(WeatherSystemType.COLD_FRONT, forecast.approachingSystem());
        assertEquals("falling", forecast.pressureTendency());
        assertTrue(forecast.estimatedArrivalTicks() > 4_000L);
        assertTrue(forecast.estimatedArrivalTicks() < 5_000L);
    }
}
