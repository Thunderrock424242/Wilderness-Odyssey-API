package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies named cloud genera create the expected altitude decks. */
class CloudLayerProfileTest {

    @Test
    void cumulonimbusBuildsTowerAndShearedAnvil() {
        CloudLayerProfile profile = CloudLayerProfile.evaluate(field(
                0.95, 0.96, 0.86, 0.82, 0.78, 0.74, 0.92, 0.65
        ));

        assertTrue(profile.convective().visible());
        assertTrue(profile.high().visible());
        assertTrue(profile.convective().depthBlocks() > profile.high().depthBlocks());
        assertTrue(profile.high().baseOffsetBlocks() > profile.convective().baseOffsetBlocks());
    }

    @Test
    void nimbostratusSpansLowAndMiddleDecksWithoutTower() {
        CloudLayerProfile profile = CloudLayerProfile.evaluate(field(
                0.88, 0.96, 0.15, 0.22, 0.42, 0.04, 0.44, 0.12
        ));

        assertTrue(profile.low().visible());
        assertTrue(profile.middle().visible());
        assertFalse(profile.convective().visible());
    }

    @Test
    void cirrusOccupiesOnlyTheHighDeck() {
        CloudLayerProfile profile = CloudLayerProfile.evaluate(field(
                0.12, 0.65, 0.10, 0.0, 0.0, -0.10, 0.12, 0.50
        ));

        assertTrue(profile.high().visible());
        assertFalse(profile.low().visible());
        assertFalse(profile.middle().visible());
        assertFalse(profile.convective().visible());
    }

    private static CloudFieldSample field(
            double cloudWater,
            double humidity,
            double instability,
            double storm,
            double precipitation,
            double lift,
            double depth,
            double shear
    ) {
        return new CloudFieldSample(
                cloudWater,
                precipitation,
                storm,
                instability,
                18.0,
                humidity,
                lift,
                depth,
                0.0,
                0.0,
                shear,
                0.0,
                1.0
        );
    }
}
