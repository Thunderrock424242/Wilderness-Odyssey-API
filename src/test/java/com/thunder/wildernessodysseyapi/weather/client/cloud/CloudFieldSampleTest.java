package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudFieldSampleTest {

    @Test
    void spatialSamplingNormalizesAvailableCellsAndRetainsSupport() {
        WeatherSample northWest = sample(0.80, 0.60, 0.50, 0.40, new WindVector(0.70, -0.30));

        CloudFieldSample field = CloudFieldSample.spatial(
                northWest,
                null,
                null,
                null,
                0.50,
                0.0
        );

        assertEquals(0.50, field.support(), 1.0E-12);
        assertEquals(0.80, field.cloudWater(), 1.0E-12);
        assertEquals(0.60, field.precipitationIntensity(), 1.0E-12);
        assertEquals(0.50, field.stormEnergy(), 1.0E-12);
        assertEquals(0.40, field.instability(), 1.0E-12);
        assertEquals(0.70, field.windX(), 1.0E-12);
        assertEquals(-0.30, field.windZ(), 1.0E-12);
        assertEquals(0.40, field.effectiveCloudWater(), 1.0E-12);
        assertEquals(0.30, field.effectivePrecipitation(), 1.0E-12);
    }

    @Test
    void spatialSamplingWithoutAnyCellsReturnsClearUnsupportedField() {
        CloudFieldSample field = CloudFieldSample.spatial(null, null, null, null, 0.25, 0.75);

        assertEquals(CloudFieldSample.CLEAR, field);
    }

    @Test
    void temporalInterpolationBlendsCloudFieldsAndSupport() {
        CloudFieldSample from = new CloudFieldSample(0.20, 0.10, 0.30, 0.40, -0.50, 0.25, 1.0);
        CloudFieldSample to = new CloudFieldSample(0.80, 0.70, 0.90, 0.60, 0.50, -0.75, 0.50);

        CloudFieldSample field = CloudFieldSample.interpolate(from, to, 0.25);

        assertEquals(0.35, field.cloudWater(), 1.0E-12);
        assertEquals(0.25, field.precipitationIntensity(), 1.0E-12);
        assertEquals(0.45, field.stormEnergy(), 1.0E-12);
        assertEquals(0.45, field.instability(), 1.0E-12);
        assertEquals(-0.25, field.windX(), 1.0E-12);
        assertEquals(0.0, field.windZ(), 1.0E-12);
        assertEquals(0.875, field.support(), 1.0E-12);
    }

    private static WeatherSample sample(
            double cloudWater,
            double precipitation,
            double stormEnergy,
            double instability,
            WindVector wind
    ) {
        return new WeatherSample(
                12.0,
                0.80,
                1.0,
                wind,
                cloudWater,
                instability,
                stormEnergy,
                precipitation,
                PrecipitationType.RAIN
        );
    }
}
