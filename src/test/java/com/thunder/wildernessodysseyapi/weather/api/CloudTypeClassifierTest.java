package com.thunder.wildernessodysseyapi.weather.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the standard cloud genera remain reachable from physical signals. */
class CloudTypeClassifierTest {

    @Test
    void classifiesClearAndHighCloudFamilies() {
        assertType(CloudType.CLEAR, 0.01, 0.40, 0.05, 0.0, 0.0, 0.0, 0.05, 0.0);
        assertType(CloudType.CIRRUS, 0.12, 0.65, 0.10, 0.0, 0.0, -0.10, 0.12, 0.50);
        assertType(CloudType.CIRROSTRATUS, 0.18, 0.92, 0.03, 0.0, 0.0, -0.25, 0.18, 0.40);
        assertType(CloudType.CIRROCUMULUS, 0.18, 0.72, 0.28, 0.0, 0.0, 0.08, 0.22, 0.35);
    }

    @Test
    void classifiesMiddleAndLowCloudFamilies() {
        assertType(CloudType.ALTOSTRATUS, 0.38, 0.90, 0.08, 0.0, 0.0, -0.12, 0.34, 0.08);
        assertType(CloudType.ALTOCUMULUS, 0.40, 0.72, 0.28, 0.0, 0.0, 0.04, 0.34, 0.08);
        assertType(CloudType.STRATUS, 0.72, 0.97, 0.05, 0.0, 0.0, -0.22, 0.22, 0.05);
        assertType(CloudType.STRATOCUMULUS, 0.62, 0.84, 0.30, 0.0, 0.0, 0.05, 0.40, 0.20);
    }

    @Test
    void classifiesConvectiveAndPrecipitatingFamilies() {
        assertType(CloudType.CUMULUS, 0.58, 0.78, 0.58, 0.12, 0.0, 0.34, 0.56, 0.10);
        assertType(CloudType.NIMBOSTRATUS, 0.88, 0.96, 0.15, 0.22, 0.42, 0.04, 0.44, 0.12);
        assertType(CloudType.CUMULONIMBUS, 0.95, 0.96, 0.86, 0.82, 0.78, 0.74, 0.92, 0.48);
    }

    private static void assertType(
            CloudType expected,
            double cloudWater,
            double humidity,
            double instability,
            double storm,
            double precipitation,
            double lift,
            double depth,
            double shear
    ) {
        assertEquals(expected, CloudTypeClassifier.classify(
                cloudWater, humidity, instability, storm, precipitation, lift, depth, shear
        ));
    }
}
