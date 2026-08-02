package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphericFrontType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies air-mass boundaries produce stable, bounded front signals. */
class AtmosphericFrontModelTest {

    @Test
    void uniformAirHasNoFront() {
        WeatherSample center = sample(18.0, 0.65, 1.0, WindVector.ZERO);

        assertEquals(
                AtmosphericFrontType.NONE,
                AtmosphericFrontModel.analyze(
                        center,
                        AtmosphereSimulationEngine.Neighborhood.uniform(center)
                ).type()
        );
    }

    @Test
    void advancingColdAndWarmAirReceiveDifferentFrontTypes() {
        WeatherSample warmCenter = sample(20.0, 0.78, 0.98, WindVector.ZERO);
        WeatherSample coldAir = sample(9.0, 0.48, 1.04, new WindVector(0.35, 0.0));
        WeatherSample coldCenter = sample(8.0, 0.55, 0.99, WindVector.ZERO);
        WeatherSample warmAir = sample(23.0, 0.82, 1.03, new WindVector(0.25, 0.0));

        AtmosphericFrontModel.FrontState cold = AtmosphericFrontModel.analyze(
                warmCenter,
                AtmosphereSimulationEngine.Neighborhood.uniform(coldAir)
        );
        AtmosphericFrontModel.FrontState warm = AtmosphericFrontModel.analyze(
                coldCenter,
                AtmosphereSimulationEngine.Neighborhood.uniform(warmAir)
        );

        assertEquals(AtmosphericFrontType.COLD, cold.type());
        assertEquals(AtmosphericFrontType.WARM, warm.type());
        assertTrue(cold.lift() > warm.lift(), "cold fronts should lift more abruptly than warm fronts");
    }

    @Test
    void convergingLowPressureAirFormsOccludedFront() {
        WeatherSample center = sample(16.0, 0.86, 0.88, WindVector.ZERO);
        AtmosphereSimulationEngine.Neighborhood neighborhood = new AtmosphereSimulationEngine.Neighborhood(
                sample(16.0, 0.82, 1.0, new WindVector(0.0, 0.6)),
                sample(16.0, 0.82, 1.0, new WindVector(-0.6, 0.0)),
                sample(16.0, 0.82, 1.0, new WindVector(0.0, -0.6)),
                sample(16.0, 0.82, 1.0, new WindVector(0.6, 0.0))
        );

        AtmosphericFrontModel.FrontState front = AtmosphericFrontModel.analyze(center, neighborhood);

        assertEquals(AtmosphericFrontType.OCCLUDED, front.type());
        assertTrue(front.strength() > 0.50);
        assertTrue(front.stormBoost() > 0.30);
    }

    private static WeatherSample sample(double temperature, double humidity, double pressure, WindVector wind) {
        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                wind,
                0.20,
                0.20,
                0.0,
                0.0,
                PrecipitationType.NONE
        );
    }
}
