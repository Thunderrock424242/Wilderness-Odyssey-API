package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies precipitation geometry cannot cross above its lowest source deck. */
class CloudAltitudeModelTest {

    @Test
    void nimbostratusRainStopsAtItsLowestVisibleCloudBase() {
        CloudFieldSample storm = new CloudFieldSample(
                0.90, 0.42, 0.22, 0.15, 12.0, 0.96, 0.04, 0.44,
                0.0, 0.0, 0.12, 0.0, 1.0
        );
        double cloudBase = CloudAltitudeModel.precipitationBaseY(192.0, storm);
        int rainTop = CloudAltitudeModel.precipitationTopY(240, cloudBase);

        assertTrue(cloudBase < 192.0, "the low storm deck should sit below the dimension base");
        assertEquals((int) Math.floor(cloudBase), rainTop);
        assertTrue(rainTop <= cloudBase);
    }

    @Test
    void clearFieldCannotCreateAFreeFloatingRainColumn() {
        double cloudBase = CloudAltitudeModel.precipitationBaseY(192.0, CloudFieldSample.CLEAR);

        assertTrue(Double.isNaN(cloudBase));
        assertEquals(Integer.MIN_VALUE, CloudAltitudeModel.precipitationTopY(240, cloudBase));
    }
}
