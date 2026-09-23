package com.thunder.wildernessodysseyapi.temporalrift.echo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoRegionModelTest {
    @Test
    void negativeCoordinatesBelongToTheirOwnRegions() {
        assertEquals(EchoRegionModel.regionId(-256, -256), EchoRegionModel.regionId(-1, -1));
        assertEquals(EchoRegionModel.regionId(0, 0), EchoRegionModel.regionId(255, 255));
        assertNotEquals(EchoRegionModel.regionId(-1, 0), EchoRegionModel.regionId(0, 0));
        assertNotEquals(EchoRegionModel.regionId(0, -1), EchoRegionModel.regionId(-1, 0));
    }

    @Test
    void generationOrderDoesNotChangeRegionsAndDefaultsFavorFamiliarTerrain() {
        int stable = 0;
        for (int region = 0; region < 10000; region++) {
            var expected = EchoRegionModel.level(12345, region, 75, 22, 3);
            EchoRegionModel.level(-9876, 10000 - region, 1, 1, 1);
            assertEquals(expected, EchoRegionModel.level(12345, region, 75, 22, 3));
            if (expected == EchoStabilityLevel.STABLE) stable++;
        }
        assertTrue(stable > 7200 && stable < 7800, "Stable default share: " + stable);
    }

    @Test
    void zeroWeightsSafelyDisableRegionalCorruption() {
        for (int region = -100; region < 100; region++) {
            assertEquals(EchoStabilityLevel.STABLE, EchoRegionModel.level(1, region, 0, 0, 0));
            assertEquals(EchoStabilityLevel.FRACTURED, EchoRegionModel.level(1, region, -5, 0, 10));
        }
    }

    @Test
    void fractureInfluenceIsBoundedAndFallsSmoothlyWithDistance() {
        assertEquals(1, EchoRegionModel.fractureInfluence(0, 384, 1));
        assertEquals(0, EchoRegionModel.fractureInfluence(384, 384, 1));
        double previous = 1;
        for (int distance = 1; distance <= 400; distance++) {
            double value = EchoRegionModel.fractureInfluence(distance, 384, 1);
            assertTrue(value >= 0 && value <= previous);
            previous = value;
        }
        assertEquals(0, EchoRegionModel.fractureInfluence(0, 384, Double.NaN));
        assertEquals(0, EchoRegionModel.fractureInfluence(0, 0, 1));
    }
}
