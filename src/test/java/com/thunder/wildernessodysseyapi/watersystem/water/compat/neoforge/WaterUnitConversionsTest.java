package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterUnits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies lossless absolute-level planning at the NeoForge fluid boundary. */
class WaterUnitConversionsTest {

    @Test
    void mapsEveryVisibleMilliBucketLevelBackToItself() {
        for (int milliBuckets = 0; milliBuckets <= 1_000; milliBuckets++) {
            long units = WaterUnitConversions.targetUnitsForMilliBuckets(milliBuckets);
            assertEquals(milliBuckets, WaterUnitConversions.toMilliBuckets(units));
        }
    }

    @Test
    void repeatedSingleMilliBucketTransfersReachAndDrainOneExactBlock() {
        long units = 0L;
        for (int amount = 1; amount <= 1_000; amount++) {
            WaterUnitConversions.TransferPlan plan = WaterUnitConversions.planFill(units, 1);
            assertEquals(1, plan.milliBuckets());
            units += plan.deltaUnits();
            assertEquals(amount, WaterUnitConversions.toMilliBuckets(units));
        }
        assertEquals(WaterUnits.UNITS_PER_BLOCK, units);

        for (int amount = 999; amount >= 0; amount--) {
            WaterUnitConversions.TransferPlan plan = WaterUnitConversions.planDrain(units, 1);
            assertEquals(1, plan.milliBuckets());
            units -= plan.deltaUnits();
            assertEquals(amount, WaterUnitConversions.toMilliBuckets(units));
        }
        assertEquals(0L, units);
    }

    @Test
    void clampsOverfillAndOverdrainToOneCell() {
        WaterUnitConversions.TransferPlan fill =
                WaterUnitConversions.planFill(WaterUnits.UNITS_PER_BLOCK - 1L, 1_000);
        assertEquals(0, fill.milliBuckets());
        assertEquals(0L, fill.deltaUnits());

        WaterUnitConversions.TransferPlan drain =
                WaterUnitConversions.planDrain(WaterUnits.UNITS_PER_BLOCK, 2_000);
        assertEquals(1_000, drain.milliBuckets());
        assertEquals(WaterUnits.UNITS_PER_BLOCK, drain.deltaUnits());
    }

    @Test
    void preservesOffGridResidualAcrossRoundTrip() {
        long initialUnits = 1L;
        WaterUnitConversions.TransferPlan fill =
                WaterUnitConversions.planFill(initialUnits, 1);
        assertEquals(1, fill.milliBuckets());

        long filledUnits = initialUnits + fill.deltaUnits();
        WaterUnitConversions.TransferPlan drain =
                WaterUnitConversions.planDrain(filledUnits, 1);
        assertEquals(1, drain.milliBuckets());
        assertEquals(initialUnits, filledUnits - drain.deltaUnits());
    }

    @Test
    void rejectsTargetsThatCannotEncodeTheExistingResidual() {
        long fourHiddenUnits = 4L;
        WaterUnitConversions.TransferPlan oneMilliBucket =
                WaterUnitConversions.planFill(fourHiddenUnits, 1);
        assertEquals(0, oneMilliBucket.milliBuckets());
        assertEquals(fourHiddenUnits, oneMilliBucket.targetUnits());

        WaterUnitConversions.TransferPlan compatibleTarget =
                WaterUnitConversions.planFill(fourHiddenUnits, 10);
        assertEquals(10, compatibleTarget.milliBuckets());
        WaterUnitConversions.TransferPlan roundTrip =
                WaterUnitConversions.planDrain(compatibleTarget.targetUnits(), 10);
        assertEquals(fourHiddenUnits, roundTrip.targetUnits());
    }

    @Test
    void preservesEveryAuthorityOccupancyAcrossAcceptedRoundTrips() {
        int[] requests = {1, 2, 3, 7, 10, 125, 333, 999, 1_000, 2_000};
        for (long currentUnits = 0L; currentUnits <= WaterUnits.UNITS_PER_BLOCK; currentUnits++) {
            for (int request : requests) {
                assertFillRoundTrip(currentUnits, request);
                assertDrainRoundTrip(currentUnits, request);
            }
        }
    }

    private static void assertFillRoundTrip(long currentUnits, int request) {
        WaterUnitConversions.TransferPlan fill =
                WaterUnitConversions.planFill(currentUnits, request);
        assertEquals(
                WaterUnitConversions.toMilliBuckets(currentUnits) + fill.milliBuckets(),
                WaterUnitConversions.toMilliBuckets(fill.targetUnits())
        );
        assertEquals(Math.abs(fill.targetUnits() - currentUnits), fill.deltaUnits());
        if (fill.milliBuckets() <= 0) {
            assertEquals(currentUnits, fill.targetUnits());
            return;
        }
        WaterUnitConversions.TransferPlan reverse =
                WaterUnitConversions.planDrain(fill.targetUnits(), fill.milliBuckets());
        assertEquals(fill.milliBuckets(), reverse.milliBuckets());
        assertEquals(currentUnits, reverse.targetUnits());
    }

    private static void assertDrainRoundTrip(long currentUnits, int request) {
        WaterUnitConversions.TransferPlan drain =
                WaterUnitConversions.planDrain(currentUnits, request);
        assertEquals(
                WaterUnitConversions.toMilliBuckets(currentUnits) - drain.milliBuckets(),
                WaterUnitConversions.toMilliBuckets(drain.targetUnits())
        );
        assertEquals(Math.abs(drain.targetUnits() - currentUnits), drain.deltaUnits());
        if (drain.milliBuckets() <= 0) {
            assertEquals(currentUnits, drain.targetUnits());
            return;
        }
        WaterUnitConversions.TransferPlan reverse =
                WaterUnitConversions.planFill(drain.targetUnits(), drain.milliBuckets());
        assertEquals(drain.milliBuckets(), reverse.milliBuckets());
        assertEquals(currentUnits, reverse.targetUnits());
    }
}
