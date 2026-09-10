package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.AtmosphericWaterExchange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceWeatherModelTest {

    @Test
    void physicalSnowProjectionCannotIndependentlyAccumulateOrMelt() {
        AtmosphericWaterExchange.Receipt receipt = new AtmosphericWaterExchange.Receipt(
                0L, 0L, 1.0, 0.37, 256.0, List.of());
        SurfaceWeatherState snow = SurfaceWeatherModel.simulate(SurfaceWeatherState.DRY,
                weather(-12.0, PrecipitationType.SNOW, 1.0), AtmosphereEnvironment.TEMPERATE, 1.0, receipt);
        assertEquals(0.37, snow.snowpack(), 1.0E-12);
        SurfaceWeatherState warm = SurfaceWeatherModel.simulate(snow,
                weather(35.0, PrecipitationType.NONE, 0.0), AtmosphereEnvironment.TEMPERATE, 1.0, receipt);
        assertEquals(0.37, warm.snowpack(), 1.0E-12);
    }

    @Test
    void rainBuildsWetnessAndPuddlesWithoutSnow() {
        SurfaceWeatherState state = SurfaceWeatherState.DRY;
        WeatherSample rain = weather(12.0, PrecipitationType.RAIN, 0.9);
        for (int step = 0; step < 20; step++) {
            state = SurfaceWeatherModel.simulate(state, rain, AtmosphereEnvironment.TEMPERATE, 1.0);
        }
        assertTrue(state.wetness() > 0.75);
        assertTrue(state.puddleCoverage() > 0.20);
        assertTrue(state.snowpack() < 0.01);
    }

    @Test
    void snowAccumulatesAndThenWarmWeatherThawsIt() {
        SurfaceWeatherState state = SurfaceWeatherState.DRY;
        for (int step = 0; step < 24; step++) {
            state = SurfaceWeatherModel.simulate(
                    state, weather(-8.0, PrecipitationType.SNOW, 0.9), AtmosphereEnvironment.TEMPERATE, 1.0);
        }
        double accumulated = state.snowpack();
        assertTrue(accumulated > 0.65);
        for (int step = 0; step < 30; step++) {
            state = SurfaceWeatherModel.simulate(
                    state, weather(16.0, PrecipitationType.NONE, 0.0), AtmosphereEnvironment.TEMPERATE, 1.0);
        }
        assertTrue(state.snowpack() < accumulated);
    }

    private static WeatherSample weather(double temperature, PrecipitationType type, double intensity) {
        return new WeatherSample(
                temperature, 0.9, 0.98, WindVector.ZERO, 0.85, 0.6, 0.6, intensity, type
        );
    }
}
