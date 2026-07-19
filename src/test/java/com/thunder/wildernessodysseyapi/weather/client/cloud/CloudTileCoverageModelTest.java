package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudTileCoverageModelTest {

    @Test
    void detectsRainAtTileEdgeWhenCenterIsClear() {
        boolean overlaps = CloudTileCoverageModel.overlapsPrecipitation(
                0,
                0,
                16,
                (x, z) -> x >= 8.0 && x <= 8.25 ? 0.5 : 0.0
        );

        assertTrue(overlaps);
    }

    @Test
    void detectsRainAtInteriorAtmosphericBoundary() {
        boolean overlaps = CloudTileCoverageModel.overlapsPrecipitation(
                -1,
                -1,
                16,
                (x, z) -> x == -8.0 && z == -8.0 ? 0.25 : 0.0
        );

        assertTrue(overlaps);
    }

    @Test
    void rejectsCompletelyClearTile() {
        assertFalse(CloudTileCoverageModel.overlapsPrecipitation(
                7,
                -3,
                256,
                (x, z) -> 0.0
        ));
    }
}
