package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies quality-independent vertical cloud-column shaping. */
class CloudColumnModelTest {

    @Test
    void humidRisingStormsHaveLowerBasesAndGreaterDepth() {
        CloudFieldSample fair = field(0.30, 0.45, 0.0, 0.20, 0.10);
        CloudFieldSample storm = field(0.90, 0.95, 0.70, 0.90, 0.85);

        assertTrue(CloudColumnModel.baseOffsetBlocks(storm) < CloudColumnModel.baseOffsetBlocks(fair));
        assertTrue(CloudColumnModel.depthBlocks(storm) > CloudColumnModel.depthBlocks(fair));
    }

    @Test
    void layerCountDoesNotChangeCombinedOpacity() {
        double fourLayerAlpha = CloudColumnModel.sliceOpacity(0.82, 4);
        double twelveLayerAlpha = CloudColumnModel.sliceOpacity(0.82, 12);

        assertEquals(0.82, 1.0 - Math.pow(1.0 - fourLayerAlpha, 4), 1.0E-12);
        assertEquals(0.82, 1.0 - Math.pow(1.0 - twelveLayerAlpha, 12), 1.0E-12);
    }

    @Test
    void cameraImmersionPeaksInsideTheColumn() {
        CloudFieldSample storm = field(0.90, 0.95, 0.70, 0.90, 0.85);
        double base = 192.0 + CloudColumnModel.baseOffsetBlocks(storm);
        double middle = base + CloudColumnModel.depthBlocks(storm) * 0.5;

        assertTrue(CloudColumnModel.cameraImmersion(storm, middle, 192.0) > 0.70);
        assertEquals(0.0, CloudColumnModel.cameraImmersion(storm, middle + 200.0, 192.0), 1.0E-12);
    }

    private static CloudFieldSample field(
            double cloudWater,
            double humidity,
            double verticalMotion,
            double cloudDepth,
            double storm
    ) {
        return new CloudFieldSample(
                cloudWater,
                storm,
                storm,
                storm,
                18.0,
                humidity,
                verticalMotion,
                cloudDepth,
                0.2,
                0.1,
                0.3,
                0.2,
                1.0
        );
    }
}
