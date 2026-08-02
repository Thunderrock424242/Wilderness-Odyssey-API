package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudShadowModelTest {

    @Test
    void projectedStormDarkensOtherwiseClearOverheadSky() {
        CloudFieldSample storm = new CloudFieldSample(
                0.9, 0.75, 0.9, 0.8, 0.0, 0.0, 1.0
        );
        double shadow = CloudShadowModel.evaluate(CloudFieldSample.CLEAR, storm, 0.8, 0.6);
        assertTrue(shadow > 0.35);
    }

    @Test
    void configuredZeroStrengthDisablesApproximation() {
        assertEquals(0.0, CloudShadowModel.evaluate(
                new CloudFieldSample(1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0),
                CloudFieldSample.CLEAR,
                1.0,
                0.0
        ));
    }
}
