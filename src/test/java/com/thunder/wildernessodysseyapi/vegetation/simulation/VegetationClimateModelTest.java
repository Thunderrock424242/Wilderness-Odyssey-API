package com.thunder.wildernessodysseyapi.vegetation.simulation;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VegetationClimateModelTest {

    @Test
    void prolongedDryWeatherBuildsDroughtWithoutBlockDensityInputs() {
        VegetationClimateState state = VegetationClimateState.DEFAULT;
        WeatherSample dry = weather(
                35.0,
                0.12,
                new WindVector(0.65, 0.20),
                PrecipitationType.NONE,
                0.0,
                SurfaceWeatherState.DRY
        );

        for (int update = 0; update < 320; update++) {
            state = VegetationClimateModel.advance(
                    state,
                    dry,
                    SeasonalClimateState.NONE,
                    1.0,
                    0.06,
                    update * 200L
            );
        }

        assertTrue(state.moisture() < 0.30);
        assertTrue(state.droughtLevel() > 0.65);
    }

    @Test
    void sustainedRainRecoversMoistureAndReducesExistingDrought() {
        VegetationClimateState state = new VegetationClimateState(
                0.12,
                0.0,
                0.92,
                0.0,
                VegetationSeasonState.DRY,
                0L,
                0L,
                0,
                0.0
        );
        WeatherSample rain = weather(
                17.0,
                0.92,
                WindVector.ZERO,
                PrecipitationType.RAIN,
                0.88,
                new SurfaceWeatherState(0.92, 0.55, 0.0, 0.0)
        );

        for (int update = 0; update < 80; update++) {
            state = VegetationClimateModel.advance(
                    state,
                    rain,
                    SeasonalClimateState.NONE,
                    1.0,
                    0.06,
                    10_000L + update * 200L
            );
        }

        assertTrue(state.moisture() > 0.72);
        assertTrue(state.recentRainfall() > 0.75);
        assertTrue(state.droughtLevel() < 0.25);
        assertTrue(state.mushroomOpportunity() > 0.60);
    }

    @Test
    void externalSeasonInputsMapToPlantRelevantStates() {
        VegetationClimateState winter = VegetationClimateModel.advance(
                VegetationClimateState.DEFAULT,
                WeatherSample.CLEAR,
                new SeasonalClimateState(-8.0, 0.8, 0.0, 0.9, true),
                1.0,
                0.06,
                200L
        );
        VegetationClimateState wet = VegetationClimateModel.advance(
                VegetationClimateState.DEFAULT,
                WeatherSample.CLEAR,
                new SeasonalClimateState(-1.0, 0.92, 0.0, 0.0, true),
                1.0,
                0.06,
                200L
        );

        assertEquals(VegetationSeasonState.DORMANT, winter.seasonState());
        assertEquals(VegetationSeasonState.WET, wet.seasonState());
    }

    private static WeatherSample weather(
            double temperature,
            double humidity,
            WindVector wind,
            PrecipitationType type,
            double intensity,
            SurfaceWeatherState surface
    ) {
        return new WeatherSample(
                temperature,
                humidity,
                1.0,
                wind,
                intensity,
                0.25,
                intensity * 0.45,
                intensity,
                type,
                0.0,
                intensity,
                wind,
                surface
        );
    }
}
