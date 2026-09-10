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
    void repeatEvaluationsDoNotInventElapsedTime() {
        assertEquals(0.0, FiniteWaterFlowPlanner.elapsedSeconds(80L, 80L));
        assertEquals(0.1, FiniteWaterFlowPlanner.elapsedSeconds(80L, 82L));
        assertEquals(0, FiniteWaterFlowPlanner.verticalTransfer(FULL, 0, 0));
    }

    @Test
    void hydraulicMultiOutletEquilibriumDoesNotEmptySourceBelowItsNeighbors() {
        var plan = FiniteWaterFlowPlanner.planHydraulic(0, FULL,
                new double[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}, new double[]{1, 1, 1, 1}, 1);
        int moved = Arrays.stream(plan.transfers()).sum();
        assertEquals(FULL, moved + plan.sourceRemainder());
        for (int amount : plan.transfers()) assertTrue(plan.sourceRemainder() >= amount);
        assertTrue(moved > 0);
    }

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

    @Test
    void hydraulicHeadUsesBedPlusDepthInsteadOfRawFillAlone() {
        FiniteWaterFlowPlanner.LateralPlan plan = FiniteWaterFlowPlanner.planHydraulic(
                10.0,
                FULL / 2,
                new double[]{9.0},
                new int[]{FULL * 3 / 4},
                new double[]{1.0},
                0.05
        );

        assertTrue(plan.transfers()[0] > 0,
                "Higher source free surface must flow despite its lower raw fill fraction");
        assertEquals(FULL / 2, plan.sourceRemainder() + plan.transfers()[0]);
    }

    @Test
    void hydraulicOpeningAndElapsedTimeControlDischargeWithoutChangingMass() {
        FiniteWaterFlowPlanner.LateralPlan shortOpen = FiniteWaterFlowPlanner.planHydraulic(
                0.0, FULL, new double[]{0.0}, new int[]{0}, new double[]{1.0}, 0.05
        );
        FiniteWaterFlowPlanner.LateralPlan longRestricted = FiniteWaterFlowPlanner.planHydraulic(
                0.0, FULL, new double[]{0.0}, new int[]{0}, new double[]{0.25}, 0.20
        );
        FiniteWaterFlowPlanner.LateralPlan blocked = FiniteWaterFlowPlanner.planHydraulic(
                0.0, FULL, new double[]{0.0}, new int[]{0}, new double[]{0.0}, 0.20
        );

        assertEquals(shortOpen.transfers()[0], longRestricted.transfers()[0], 1);
        assertEquals(0, blocked.transfers()[0]);
        assertEquals(FULL, shortOpen.sourceRemainder() + shortOpen.transfers()[0]);
    }

    @Test
    void steepHeadBuildsMoreMomentumThanCentimeterEqualization() {
        float pondVelocity = FiniteWaterFlowPlanner.velocityAfterHeadGradient(
                0.0f, 0.01, 0.05, 2.5, 4.8
        );
        float breachVelocity = FiniteWaterFlowPlanner.velocityAfterHeadGradient(
                0.0f, 1.0, 0.05, 2.5, 4.8
        );

        assertTrue(breachVelocity > pondVelocity * 50.0f);
        assertTrue(breachVelocity <= 4.8f);
    }
}
