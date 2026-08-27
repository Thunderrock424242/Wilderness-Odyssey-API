package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies exact-volume planning before transfers touch Minecraft world state. */
class FiniteWaterFlowPlannerTest {

    private static final int FULL = WaterVolumeChunk.UNITS_PER_BLOCK;

    @Test
    void gravityFillsAllAvailableCapacityWithoutOverdrawingSource() {
        assertEquals(FULL, FiniteWaterFlowPlanner.verticalTransfer(FULL, 0));
        assertEquals(3, FiniteWaterFlowPlanner.verticalTransfer(8, FULL - 3));
        assertEquals(0, FiniteWaterFlowPlanner.verticalTransfer(FULL, FULL));
    }

    @Test
    void symmetricTargetsReceiveIdenticalSnapshotTransfers() {
        int[] targets = {0, 0, 0, 0};
        FiniteWaterFlowPlanner.LateralPlan plan =
                FiniteWaterFlowPlanner.planLateral(FULL, targets);

        int[] transfers = plan.transfers();
        assertEquals(transfers[0], transfers[1]);
        assertEquals(transfers[0], transfers[2]);
        assertEquals(transfers[0], transfers[3]);
        assertEquals(FULL, plan.sourceRemainder() + Arrays.stream(transfers).sum());
    }

    @Test
    void targetIterationOrderCannotChangeRequestedVolume() {
        int[] ascending = {0, FULL / 4, FULL / 2, FiniteWaterFlowPlanner.BLOCKED_TARGET};
        int[] descending = {FULL / 2, FULL / 4, 0, FiniteWaterFlowPlanner.BLOCKED_TARGET};

        int[] ascendingTransfers =
                FiniteWaterFlowPlanner.planLateral(FULL, ascending).transfers();
        int[] descendingTransfers =
                FiniteWaterFlowPlanner.planLateral(FULL, descending).transfers();

        assertArrayEquals(
                new int[]{ascendingTransfers[2], ascendingTransfers[1], ascendingTransfers[0], 0},
                descendingTransfers
        );
    }

    @Test
    void repeatedPlanningConservesEveryUnitForHundredsOfSteps() {
        int source = FULL;
        int[] targets = {0, FULL / 8, FULL / 3, 0};
        int initialTotal = source + Arrays.stream(targets).sum();

        for (int step = 0; step < 256; step++) {
            FiniteWaterFlowPlanner.LateralPlan plan =
                    FiniteWaterFlowPlanner.planLateral(source, targets);
            int[] transfers = plan.transfers();
            source = plan.sourceRemainder();
            for (int index = 0; index < targets.length; index++) {
                targets[index] += transfers[index];
                assertTrue(targets[index] <= FULL, "A target exceeded one canonical block");
            }
            assertEquals(initialTotal, source + Arrays.stream(targets).sum());
        }
    }

    @Test
    void rejectedDestinationVolumeRemainsWithTheSourceLedger() {
        FiniteWaterFlowPlanner.LateralPlan plan =
                FiniteWaterFlowPlanner.planLateral(FULL, new int[]{0, 0});
        int acceptedFirst = plan.transfers()[0];
        int acceptedSecond = 0;
        int runtimeSourceRemainder = FULL - acceptedFirst - acceptedSecond;

        assertEquals(FULL, runtimeSourceRemainder + acceptedFirst + acceptedSecond);
        assertTrue(runtimeSourceRemainder > plan.sourceRemainder());
    }

    @Test
    void wildernessFluidCannotUseVanillaInfiniteSourceConversion() {
        assertFalse(WildernessFluidRegistry.ALLOW_SOURCE_CONVERSION);
    }
}
