package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the exact quantity spine beneath derived watershed metadata. */
class HydrologicConservationTest {

    @Test
    void closedWatershedBudgetBalancesAcrossAllInternalTransfers() {
        HydrologicStorage storage = storage(20_000_000L);
        WaterBudget budget = new WaterBudget(storage);

        long precipitation = budget.creditPrecipitation(HydrologicReservoir.SNOW, 4_000_000L);
        long melt = budget.transfer(HydrologicReservoir.SNOW, HydrologicReservoir.SOIL,
                precipitation / 2L);
        budget.transfer(HydrologicReservoir.SOIL, HydrologicReservoir.GROUNDWATER, melt / 2L);
        budget.transfer(HydrologicReservoir.SOIL, HydrologicReservoir.SURFACE_RUNOFF, melt / 4L);
        budget.transfer(HydrologicReservoir.SURFACE_RUNOFF, HydrologicReservoir.RIVER, melt / 4L);
        budget.debitEvapotranspiration(HydrologicReservoir.SOIL, 100_000L);
        budget.debitDownstream(HydrologicReservoir.RIVER, 75_000L);

        assertEquals(0L, budget.residualMilliUnits());
        assertEquals(0L, budget.snapshot().residual());
        WaterBudgetAuditor.Report report = WaterBudgetAuditor.audit(
                42L, storage, budget.snapshot(), 0L
        );
        assertEquals(42L, report.basinId());
        assertEquals(0L, report.residual());
        assertTrue(!report.warning());
    }

    @Test
    void destinationCapacityCannotDeleteAnUnacceptedTransferRemainder() {
        long[] capacities = capacities(10_000L);
        capacities[HydrologicReservoir.RIVER.ordinal()] = 250L;
        HydrologicStorage storage = new HydrologicStorage(capacities);
        storage.credit(HydrologicReservoir.SURFACE_RUNOFF, 1_000L);

        long moved = storage.transfer(
                HydrologicReservoir.SURFACE_RUNOFF,
                HydrologicReservoir.RIVER,
                800L
        );

        assertEquals(250L, moved);
        assertEquals(750L, storage.stored(HydrologicReservoir.SURFACE_RUNOFF));
        assertEquals(1_000L, storage.totalStored());
    }

    @Test
    void snowWaterEquivalentLossExactlyEqualsLiquidGain() {
        SnowWaterEquivalentModel.Result result = SnowWaterEquivalentModel.advance(
                new SnowWaterEquivalentModel.Input(
                        2_000_000L,
                        500_000L,
                        false,
                        6.0,
                        0.8,
                        0.4,
                        0.3,
                        0.7,
                        0.02,
                        10.0
                )
        );

        assertTrue(result.meltMilliUnits() > 0L);
        assertEquals(result.rainfallMilliUnits() + result.meltMilliUnits(),
                result.liquidOutputMilliUnits());
        assertEquals(0L, result.residualMilliUnits());
    }

    @Test
    void impermeableTerrainProducesMuchMoreRunoffThanSand() {
        long rain = HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK / 2L;
        SoilHydrologyModel.Input sandInput = new SoilHydrologyModel.Input(
                SoilHydrologyModel.SoilProfile.SAND,
                0L,
                HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK,
                rain,
                0L,
                0.2,
                5.0,
                256.0
        );
        SoilHydrologyModel.Input pavedInput = new SoilHydrologyModel.Input(
                SoilHydrologyModel.SoilProfile.IMPERMEABLE,
                0L,
                HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK,
                rain,
                0L,
                0.0,
                5.0,
                256.0
        );

        SoilHydrologyModel.Result sand = SoilHydrologyModel.advance(sandInput);
        SoilHydrologyModel.Result paved = SoilHydrologyModel.advance(pavedInput);

        assertTrue(paved.runoffMilliUnits() > sand.runoffMilliUnits() * 4L);
        assertEquals(0L, sand.residualMilliUnits());
        assertEquals(0L, paved.residualMilliUnits());
    }

    @Test
    void physicalGroundwaterRechargeReturnsDelayedBaseflowWithoutLoss() {
        GroundwaterModel.PhysicalResult result = GroundwaterModel.advancePhysical(
                new GroundwaterModel.PhysicalInput(
                        2_000_000L,
                        8_000_000L,
                        1_000_000L,
                        0.02,
                        0.6,
                        0.001,
                        10.0,
                        true
                )
        );

        assertEquals(1_000_000L, result.acceptedRechargeMilliUnits());
        assertTrue(result.dischargeMilliUnits() > 0L);
        assertEquals(0L, result.residualMilliUnits());
    }

    private static HydrologicStorage storage(long capacity) {
        return new HydrologicStorage(capacities(capacity));
    }

    private static long[] capacities(long capacity) {
        long[] capacities = new long[HydrologicReservoir.values().length];
        Arrays.fill(capacities, capacity);
        return capacities;
    }
}
