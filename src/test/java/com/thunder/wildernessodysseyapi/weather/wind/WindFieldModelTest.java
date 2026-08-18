package com.thunder.wildernessodysseyapi.weather.wind;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.api.WindSettings;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies deterministic, continuous, weather-coupled regional wind queries. */
class WindFieldModelTest {
    private static final int CELL_SIZE = 256;
    private static final long OVERWORLD_SALT = WindFieldModel.dimensionSalt("minecraft:overworld");
    private static final WindSettings SETTINGS = WindSettings.DEFAULT;

    @Test
    void disabledWindReturnsCalmWithoutDiscardingRegion() {
        WindSample sample = WindFieldModel.sample(
                WeatherSample.CLEAR,
                WindSettings.DISABLED,
                CELL_SIZE,
                -1.0,
                512.0,
                4_000L,
                OVERWORLD_SALT
        );

        assertEquals(0.0F, sample.effectiveSpeed());
        assertEquals(-1, sample.region().x());
        assertEquals(2, sample.region().z());
    }

    @Test
    void identicalQueriesProduceIdenticalRegionalGusts() {
        WindSample first = sample(WeatherSample.CLEAR, 96.25, -48.75, 7_500L, SETTINGS);
        WindSample second = sample(WeatherSample.CLEAR, 96.25, -48.75, 7_500L, SETTINGS);

        assertEquals(first, second);
        assertTrue(first.speed() > 0.0F);
        assertTrue(first.effectiveSpeed() <= SETTINGS.maxWindSpeed());
    }

    @Test
    void interpolationRemainsContinuousAcrossChunkAndAtmosphereBoundaries() {
        assertContinuousAcross(15.99, 16.01, 90.0, 6_300L);
        assertContinuousAcross(255.99, 256.01, 90.0, 6_300L);
        assertContinuousAcross(-256.01, -255.99, 90.0, 6_300L);
    }

    @Test
    void approachingAndMatureStormsIncreaseSustainedWind() {
        WindSample clear = sample(WeatherSample.CLEAR, 100.0, 100.0, 8_200L, SETTINGS);
        WindSample approaching = sample(approachingStorm(), 100.0, 100.0, 8_200L, SETTINGS);
        WindSample severe = sample(severeStorm(), 100.0, 100.0, 8_200L, SETTINGS);

        assertTrue(approaching.speed() > clear.speed());
        assertTrue(severe.speed() > approaching.speed());
        assertTrue(severe.weatherContribution() > approaching.weatherContribution());
        assertTrue(severe.effectiveSpeed() <= SETTINGS.maxWindSpeed());
    }

    @Test
    void stormMultiplierProducesStrongerRegionalGusts() {
        float clearMaximum = 0.0F;
        float severeMaximum = 0.0F;
        for (long gameTime = 0L; gameTime <= 1_200L; gameTime += 5L) {
            clearMaximum = Math.max(
                    clearMaximum,
                    sample(WeatherSample.CLEAR, 100.0, 100.0, gameTime, SETTINGS).gust()
            );
            severeMaximum = Math.max(
                    severeMaximum,
                    sample(severeStorm(), 100.0, 100.0, gameTime, SETTINGS).gust()
            );
        }

        assertTrue(severeMaximum > clearMaximum);
    }

    @Test
    void coherentGustEnvelopeVariesWithWorldTimeInsteadOfObjectRandomness() {
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (long gameTime = 0L; gameTime <= 1_200L; gameTime += 5L) {
            WindSample sample = sample(WeatherSample.CLEAR, 80.0, 80.0, gameTime, SETTINGS);
            minimum = Math.min(minimum, sample.gustFactor());
            maximum = Math.max(maximum, sample.gustFactor());
        }

        WindSample center = sample(WeatherSample.CLEAR, 80.0, 80.0, 320L, SETTINGS);
        WindSample nearby = sample(WeatherSample.CLEAR, 81.0, 80.0, 320L, SETTINGS);
        assertTrue(minimum < 0.02F);
        assertTrue(maximum > 0.35F);
        assertTrue(Math.abs(center.gustFactor() - nearby.gustFactor()) < 0.02F);
    }

    @Test
    void convectiveStormAddsBoundedVerticalDirection() {
        WindSample clear = sample(WeatherSample.CLEAR, 16.0, 16.0, 480L, SETTINGS);
        WindSample storm = sample(severeStorm(), 16.0, 16.0, 480L, SETTINGS);

        assertTrue(Math.abs(clear.direction().y) < 0.02D);
        assertTrue(storm.direction().y > clear.direction().y);
        assertTrue(Math.abs(storm.direction().y) <= 0.35D);
        assertEquals(1.0D, storm.direction().length(), 1.0E-7D);
    }

    @Test
    void differentDimensionsUseDifferentAmbientFields() {
        long netherSalt = WindFieldModel.dimensionSalt("minecraft:the_nether");
        WindSample overworld = WindFieldModel.sample(
                WeatherSample.CLEAR, SETTINGS, CELL_SIZE, 0.0, 0.0, 2_000L, OVERWORLD_SALT
        );
        WindSample nether = WindFieldModel.sample(
                WeatherSample.CLEAR, SETTINGS, CELL_SIZE, 0.0, 0.0, 2_000L, netherSalt
        );

        assertNotEquals(overworld.direction(), nether.direction());
    }

    @Test
    void highSpeedTravelQueriesStayFiniteAndCapped() {
        for (int step = -30; step <= 30; step++) {
            double coordinate = step * 900_000.0D;
            WindSample sample = sample(severeStorm(), coordinate, -coordinate, 90_000L + step, SETTINGS);
            assertTrue(Double.isFinite(sample.direction().x));
            assertTrue(Double.isFinite(sample.direction().y));
            assertTrue(Double.isFinite(sample.direction().z));
            assertTrue(Float.isFinite(sample.effectiveSpeed()));
            assertTrue(sample.effectiveSpeed() <= SETTINGS.maxWindSpeed());
        }
    }

    private static void assertContinuousAcross(double westX, double eastX, double z, long gameTime) {
        WindSample west = sample(approachingStorm(), westX, z, gameTime, SETTINGS);
        WindSample east = sample(approachingStorm(), eastX, z, gameTime, SETTINGS);
        assertTrue(Math.abs(west.effectiveSpeed() - east.effectiveSpeed()) < 0.02F);
        assertTrue(west.direction().dot(east.direction()) > 0.999D);
    }

    private static WindSample sample(
            WeatherSample weather,
            double x,
            double z,
            long gameTime,
            WindSettings settings
    ) {
        return WindFieldModel.sample(weather, settings, CELL_SIZE, x, z, gameTime, OVERWORLD_SALT);
    }

    private static WeatherSample approachingStorm() {
        return new WeatherSample(
                21.0,
                0.82,
                0.96,
                new WindVector(0.32, 0.18),
                0.64,
                0.58,
                0.35,
                0.12,
                PrecipitationType.RAIN,
                0.24,
                0.58,
                new WindVector(0.38, 0.24)
        );
    }

    private static WeatherSample severeStorm() {
        return new WeatherSample(
                24.0,
                0.98,
                0.86,
                new WindVector(0.92, 0.36),
                0.96,
                0.94,
                0.98,
                0.92,
                PrecipitationType.HAIL,
                0.88,
                0.96,
                new WindVector(0.74, 0.66)
        );
    }
}
