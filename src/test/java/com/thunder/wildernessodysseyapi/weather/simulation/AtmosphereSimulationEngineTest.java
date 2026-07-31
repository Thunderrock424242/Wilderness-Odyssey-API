package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies deterministic first-pass atmosphere coupling without world access. */
class AtmosphereSimulationEngineTest {

    private final AtmosphereSimulationEngine engine = new AtmosphereSimulationEngine();

    @Test
    void pressureGradientDrivesWindAndAdvectsUpwindHumidity() {
        WeatherSample center = sample(15.0, 0.10, 1.0, 0.0, 0.0, 0.0);
        WeatherSample west = sample(15.0, 0.90, 1.2, 0.0, 0.0, 0.0);
        WeatherSample east = sample(15.0, 0.10, 0.8, 0.0, 0.0, 0.0);
        AtmosphereSimulationEngine.Neighborhood neighbors = new AtmosphereSimulationEngine.Neighborhood(
                center,
                east,
                center,
                west
        );
        SimulationSettings settings = new SimulationSettings(
                1.0,
                0.8,
                0.0,
                0.2,
                0.0,
                0.95,
                0.95,
                1.0,
                1.0,
                0.0
        );
        AtmosphereEnvironment environment = new AtmosphereEnvironment(
                15.0,
                0.10,
                64.0,
                0.0,
                0.5,
                0.0,
                0.0,
                0.0
        );

        WeatherSample result = engine.simulate(center, environment, neighbors, settings);

        assertTrue(result.wind().x() > 0.0, "higher pressure to the west should drive eastward wind");
        assertEquals(0.0, result.wind().z(), 1.0E-9);
        assertTrue(result.humidity() > 0.25, "eastward wind should advect the humid western air");
    }

    @Test
    void saturatedColdAirCondensesCloudWaterAndProducesSnow() {
        WeatherSample saturated = sample(0.0, 1.0, 1.0, 0.90, 0.0, 0.0);
        AtmosphereEnvironment environment = new AtmosphereEnvironment(
                0.0,
                1.0,
                64.0,
                0.0,
                0.5,
                0.0,
                0.0,
                0.0
        );
        SimulationSettings settings = new SimulationSettings(
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.50,
                0.50,
                1.0,
                1.0,
                0.0
        );

        WeatherSample result = engine.simulate(
                saturated,
                environment,
                AtmosphereSimulationEngine.Neighborhood.uniform(saturated),
                settings
        );

        assertTrue(result.humidity() < saturated.humidity(), "condensation should remove vapor");
        assertTrue(result.cloudWater() > saturated.cloudWater(), "condensation should build cloud water");
        assertTrue(result.precipitationIntensity() > 0.20);
        assertEquals(PrecipitationType.SNOW, result.precipitationType());
    }

    @Test
    void cloudWaterBelowThresholdDoesNotStartPrecipitation() {
        WeatherSample thinCloud = sample(12.0, 0.40, 1.0, 0.30, 0.0, 0.0);
        SimulationSettings settings = new SimulationSettings(
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.95,
                0.60,
                1.0,
                1.0,
                0.0
        );

        WeatherSample result = engine.simulate(
                thinCloud,
                new AtmosphereEnvironment(12.0, 0.40, 64.0, 0.0, 0.5, 0.0, 0.0, 0.0),
                AtmosphereSimulationEngine.Neighborhood.uniform(thinCloud),
                settings
        );

        assertEquals(0.0, result.precipitationIntensity(), 1.0E-9);
        assertEquals(PrecipitationType.NONE, result.precipitationType());
    }

    @Test
    void windwardTerrainBuildsMoreVerticalCloudDevelopment() {
        WeatherSample movingAir = new WeatherSample(
                18.0,
                0.85,
                0.96,
                new WindVector(0.8, 0.0),
                0.62,
                0.55,
                0.35,
                0.10,
                PrecipitationType.RAIN
        );
        AtmosphereEnvironment flat = new AtmosphereEnvironment(
                18.0, 0.85, 96.0, 0.0, 0.5, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.0, 0.0, 0.0
        );
        AtmosphereEnvironment windwardSlope = new AtmosphereEnvironment(
                18.0, 0.85, 96.0, 0.0, 0.5, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.25, 0.0, 0.4
        );

        WeatherSample flatResult = engine.simulate(
                movingAir,
                flat,
                AtmosphereSimulationEngine.Neighborhood.uniform(movingAir),
                SimulationSettings.DEFAULT
        );
        WeatherSample liftedResult = engine.simulate(
                movingAir,
                windwardSlope,
                AtmosphereSimulationEngine.Neighborhood.uniform(movingAir),
                SimulationSettings.DEFAULT
        );

        assertTrue(liftedResult.verticalMotion() > flatResult.verticalMotion());
        assertTrue(liftedResult.cloudDepth() > flatResult.cloudDepth());
    }

    @Test
    void sharedFaceTransportConservesPairVaporInventory() {
        WeatherSample west = sample(15.0, 0.80, 1.20, 0.0, 0.0, 0.0);
        WeatherSample east = sample(15.0, 0.20, 0.80, 0.0, 0.0, 0.0);
        SimulationSettings transportOnly = new SimulationSettings(
                1.0, 0.8, 0.0, 0.0, 0.0,
                0.99, 0.99, 1.0, 0.0, 0.0
        );

        WeatherSample westResult = engine.simulate(
                west,
                new AtmosphereEnvironment(15.0, 0.80, 64.0, 0.0, 0.5, 0.0, 0.0, 0.0),
                new AtmosphereSimulationEngine.Neighborhood(west, east, west, west),
                transportOnly
        );
        WeatherSample eastResult = engine.simulate(
                east,
                new AtmosphereEnvironment(15.0, 0.20, 64.0, 0.0, 0.5, 0.0, 0.0, 0.0),
                new AtmosphereSimulationEngine.Neighborhood(east, east, east, west),
                transportOnly
        );

        double before = AtmosphericThermodynamics.vaporContent(15.0, west.humidity())
                + AtmosphericThermodynamics.vaporContent(15.0, east.humidity());
        double after = AtmosphericThermodynamics.vaporContent(
                westResult.temperature(),
                westResult.humidity()
        ) + AtmosphericThermodynamics.vaporContent(
                eastResult.temperature(),
                eastResult.humidity()
        );

        assertEquals(before, after, 1.0E-9);
        assertTrue(westResult.humidity() < west.humidity());
        assertTrue(eastResult.humidity() > east.humidity());
    }

    private static WeatherSample sample(
            double temperature,
            double humidity,
            double pressure,
            double cloudWater,
            double instability,
            double precipitationIntensity
    ) {
        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                WindVector.ZERO,
                cloudWater,
                instability,
                0.0,
                precipitationIntensity,
                precipitationIntensity > 0.0 ? PrecipitationType.RAIN : PrecipitationType.NONE
        );
    }
}
