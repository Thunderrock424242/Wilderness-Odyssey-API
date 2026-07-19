package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudCoverageModelTest {

    @Test
    void precipitationGuaranteesAnOccupiedCloudVoxel() {
        CloudFieldSample raining = field(0.03, 2.0E-4, 0.05, 0.10, 1.0);

        int[][] tiles = {
                {-31, 47},
                {0, 0},
                {173, -89},
                {2_048, 4_096}
        };
        for (int[] tile : tiles) {
            assertTrue(
                    CloudCoverageModel.isPresent(raining, tile[0], tile[1], 37.5, -18.25),
                    () -> "Rain at tile " + tile[0] + ", " + tile[1] + " must have cloud cover"
            );
        }
        assertTrue(CloudCoverageModel.coverage(raining) > 0.0);
    }

    @Test
    void supportParticipatesInThePrecipitationCoverageGuarantee() {
        CloudFieldSample supportedRain = field(0.03, 0.08, 0.0, 0.0, 0.50);
        CloudFieldSample unsupportedRain = field(0.03, 0.08, 0.0, 0.0, 0.0);

        assertTrue(CloudCoverageModel.isPresent(supportedRain, 12, -6, 0.0, 0.0));
        assertEquals(0.0, CloudCoverageModel.coverage(unsupportedRain), 1.0E-12);
    }

    @Test
    void rainAndConvectionIncreaseBlockyThicknessAndDarkness() {
        CloudFieldSample fairWeather = field(0.25, 0.0, 0.05, 0.10, 1.0);
        CloudFieldSample rain = field(0.65, 0.20, 0.20, 0.20, 1.0);
        CloudFieldSample severeStorm = field(0.95, 0.60, 0.90, 0.85, 1.0);

        assertEquals(4, CloudCoverageModel.thickness(fairWeather));
        assertEquals(8, CloudCoverageModel.thickness(rain));
        assertEquals(12, CloudCoverageModel.thickness(severeStorm));
        assertTrue(CloudCoverageModel.darkness(rain) > CloudCoverageModel.darkness(fairWeather));
        assertTrue(CloudCoverageModel.darkness(severeStorm) > CloudCoverageModel.darkness(rain));
        assertTrue(CloudCoverageModel.darkness(severeStorm) <= 1.0);
    }

    @Test
    void morphologyIsDeterministicForWorldPositionAndWindOffset() {
        double first = CloudCoverageModel.morphologyNoise(27, -14, 8.25, -3.5);
        double repeated = CloudCoverageModel.morphologyNoise(27, -14, 8.25, -3.5);
        double shifted = CloudCoverageModel.morphologyNoise(27, -14, 20.25, -3.5);

        assertEquals(first, repeated, 0.0);
        assertTrue(first >= 0.0 && first <= 1.0);
        assertTrue(shifted >= 0.0 && shifted <= 1.0);
        assertTrue(Math.abs(first - shifted) > 1.0E-12);
    }

    @Test
    void morphologyRetainsPrecisionBeyondIntegerLatticeCoordinates() {
        double largeOffset = (double) Integer.MAX_VALUE * CloudCoverageModel.CLOUD_TILE_SIZE * 8.0;
        double first = CloudCoverageModel.morphologyNoise(27, -14, largeOffset, -largeOffset);
        double repeated = CloudCoverageModel.morphologyNoise(27, -14, largeOffset, -largeOffset);

        assertEquals(first, repeated, 0.0);
        assertTrue(first >= 0.0 && first <= 1.0);
    }

    private static CloudFieldSample field(
            double cloudWater,
            double precipitation,
            double stormEnergy,
            double instability,
            double support
    ) {
        return new CloudFieldSample(
                cloudWater,
                precipitation,
                stormEnergy,
                instability,
                0.35,
                -0.20,
                support
        );
    }
}
