package com.thunder.wildernessodysseyapi.vegetation.api;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that external plant pressure stays bounded in distance and time. */
class PlantDisturbanceTest {

    @Test
    void intensityFallsWithDistanceAndTimeThenExpires() {
        PlantDisturbance disturbance = PlantDisturbance.lasting(
                PlantDisturbanceType.METEOR,
                BlockPos.ZERO,
                20,
                0.80,
                100L,
                200,
                true
        );

        double centerAtStart = disturbance.intensityAt(BlockPos.ZERO, 100L);
        double halfwayOut = disturbance.intensityAt(new BlockPos(10, 0, 0), 100L);
        double halfwayThrough = disturbance.intensityAt(BlockPos.ZERO, 200L);

        assertEquals(0.80, centerAtStart, 0.0001);
        assertTrue(halfwayOut < centerAtStart);
        assertTrue(halfwayThrough < centerAtStart);
        assertEquals(0.0, disturbance.intensityAt(BlockPos.ZERO, 300L));
        assertEquals(0.0, disturbance.intensityAt(new BlockPos(21, 0, 0), 100L));
    }

    @Test
    void malformedInputsAreNormalizedAtThePublicBoundary() {
        PlantDisturbance disturbance = new PlantDisturbance(
                null, null, 999, Double.NaN, -10L, -20L, false);

        assertEquals(PlantDisturbanceType.WIND, disturbance.type());
        assertEquals(BlockPos.ZERO, disturbance.center());
        assertEquals(256, disturbance.radiusBlocks());
        assertEquals(0.0, disturbance.intensity());
        assertEquals(0L, disturbance.createdAt());
        assertEquals(0L, disturbance.expiresAt());
    }
}
