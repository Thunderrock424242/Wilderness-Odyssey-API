package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherPhenomenon;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherHazardModelTest {

    @Test
    void coldWindDrivenSnowProducesBlizzardSignal() {
        WeatherSample snow = new WeatherSample(
                -9.0, 0.96, 0.93, new WindVector(0.9, 0.5),
                0.92, 0.72, 0.78, 0.95, PrecipitationType.SNOW,
                0.65, 0.90, new WindVector(-0.8, 0.7)
        );
        WeatherHazardModel.HazardProfile hazards = WeatherHazardModel.evaluate(snow);
        assertTrue(hazards.blizzard() > 0.65);
        assertEquals(WeatherPhenomenon.BLIZZARD, hazards.dominant());
    }

    @Test
    void hailPrecipitationIsReportedDirectly() {
        WeatherSample hail = new WeatherSample(
                8.0, 0.9, 0.95, WindVector.ZERO, 0.9, 0.9, 0.9, 0.8, PrecipitationType.HAIL
        );
        assertTrue(WeatherHazardModel.evaluate(hail).hail() >= 0.8);
        assertEquals(CloudType.CUMULONIMBUS, hail.cloudType());
    }
}
