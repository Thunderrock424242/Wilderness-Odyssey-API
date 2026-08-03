package com.thunder.wildernessodysseyapi.watersystem.ocean;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded network-safe sea-state behavior without a loaded world. */
class OceanSeaStateTest {

    @Test
    void sanitizesEnvironmentalValuesAndNormalizesWind() {
        OceanSeaState.Sample sample = new OceanSeaState.Sample(
                4.0f,
                3.0f,
                4.0f,
                90.0f,
                -2.0f,
                9.0f,
                3.0f,
                -1.0f
        );

        assertEquals(1.0f, sample.strength());
        assertEquals(40.0f, sample.windSpeed());
        assertEquals(0.0f, sample.swellScale());
        assertEquals(4.0f, sample.chopScale());
        assertEquals(1.0f, sample.directionBlend());
        assertEquals(0.0f, sample.breakingStrength());
        assertEquals(1.0f, (float) Math.hypot(
                sample.windDirectionX(),
                sample.windDirectionZ()
        ), 1.0e-6f);
    }

    @Test
    void interpolationMovesTowardServerTarget() {
        OceanSeaState.Sample calm = OceanSeaState.CALM;
        OceanSeaState.Sample storm = new OceanSeaState.Sample(
                1.0f, 0.0f, 1.0f, 15.0f, 1.7f, 2.3f, 0.82f, 1.0f
        );

        OceanSeaState.Sample blended = calm.interpolate(storm, 0.5f);

        assertTrue(blended.strength() > calm.strength());
        assertTrue(blended.strength() < storm.strength());
        assertEquals(1.0f, (float) Math.hypot(
                blended.windDirectionX(),
                blended.windDirectionZ()
        ), 1.0e-6f);
    }

    @Test
    void interpolationRotatesSmoothlyThroughOppositeWindDirections() {
        OceanSeaState.Sample north = new OceanSeaState.Sample(
                0.5f, 0.0f, 1.0f, 8.0f, 1.0f, 1.0f, 0.5f, 0.2f
        );
        OceanSeaState.Sample south = new OceanSeaState.Sample(
                0.5f, 0.0f, -1.0f, 8.0f, 1.0f, 1.0f, 0.5f, 0.2f
        );

        OceanSeaState.Sample quarter = north.interpolate(south, 0.25f);
        OceanSeaState.Sample midpoint = north.interpolate(south, 0.5f);

        assertTrue(Math.abs(quarter.windDirectionX()) > 0.6f);
        assertTrue(quarter.windDirectionZ() > 0.6f);
        assertEquals(1.0f, (float) Math.hypot(
                midpoint.windDirectionX(),
                midpoint.windDirectionZ()
        ), 1.0e-6f);
        assertEquals(0.0f, midpoint.windDirectionZ(), 1.0e-5f);
    }

    @Test
    void localizedWeatherDrivesDirectionAndOrganizedSeaEnergy() {
        WeatherSample storm = new WeatherSample(
                22.0,
                0.91,
                0.86,
                new WindVector(0.6, 0.8),
                0.9,
                0.85,
                0.95,
                0.88,
                PrecipitationType.RAIN
        );

        OceanSeaState.Sample target = OceanSeaState.targetFromWeather(
                storm,
                OceanSeaState.CALM
        );

        assertTrue(target.strength() > OceanSeaState.CALM.strength());
        assertTrue(target.swellScale() > OceanSeaState.CALM.swellScale());
        assertTrue(target.breakingStrength() > OceanSeaState.CALM.breakingStrength());
        assertEquals(0.6f, target.windDirectionX(), 1.0e-6f);
        assertEquals(0.8f, target.windDirectionZ(), 1.0e-6f);
    }

    @Test
    void calmWeatherRetainsDirectionAndSwellDecaysMoreSlowlyThanItBuilds() {
        OceanSeaState.Sample easterlyStorm = new OceanSeaState.Sample(
                1.0f, 0.0f, 1.0f, 20.0f, 1.8f, 2.2f, 0.8f, 1.0f
        );
        OceanSeaState.Sample calmTarget = OceanSeaState.targetFromWeather(
                WeatherSample.CLEAR,
                easterlyStorm
        );
        OceanSeaState.Sample decaying = easterlyStorm.approach(
                calmTarget, 100L, 20.0f, 180.0f);
        OceanSeaState.Sample building = calmTarget.approach(
                easterlyStorm, 100L, 20.0f, 180.0f);

        assertEquals(0.0f, calmTarget.windDirectionX(), 1.0e-6f);
        assertEquals(1.0f, calmTarget.windDirectionZ(), 1.0e-6f);
        assertTrue(decaying.strength() > building.strength());
        assertTrue(decaying.strength() < easterlyStorm.strength());
        assertTrue(building.strength() > calmTarget.strength());
    }
}
