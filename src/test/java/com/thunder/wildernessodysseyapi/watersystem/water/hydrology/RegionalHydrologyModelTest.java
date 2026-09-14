package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.*;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact transactions and hydraulic behavior without loading or mutating a Minecraft world. */
class RegionalHydrologyModelTest {
    private static final long M3 = HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK;
    private static final RegionalHydrologyModel.Parameters DRY = new RegionalHydrologyModel.Parameters(0, 0, .045, 1.25, 1800, true, false);
    private static final RegionalHydrologyModel.Parameters WEATHER = new RegionalHydrologyModel.Parameters(12, .15, .045, 1.25, 1800, true, true);

    @Test void closedRegionConservesStormSnowThawEvaporationAndIce() {
        RegionalHydrologyState state = new RegionalHydrologyState(1, 0, 64);
        state.airTemperature = -8;
        state.snowing = true; // The atmosphere owns phase; cold air alone no longer reclassifies rain.
        state.precipitationFraction = 1;
        long vaporReceipts = 0;
        for (int i = 0; i < 100; i++) {
            var exchange = RegionalHydrologyModel.advance(state, null, WEATHER, 2);
            vaporReceipts += exchange.evaporationMilliUnits();
            assertEquals(0, state.residualMilliUnits());
        }
        assertTrue(state.stored(SNOW) > 0);
        state.airTemperature = 15;
        state.snowing = false;
        state.sunlight = .8;
        for (int i = 0; i < 500; i++) {
            var exchange = RegionalHydrologyModel.advance(state, null, WEATHER, 2);
            vaporReceipts += exchange.evaporationMilliUnits();
            assertEquals(0, state.residualMilliUnits());
        }
        assertEquals(vaporReceipts, state.receipt(EVAPORATION));
        assertTrue(state.stored(SOIL) > 0);
    }

    @Test void projectionNeedsFundingAndRollbackReturnsEveryUnit() {
        RegionalHydrologyState state = new RegionalHydrologyState(1, 0, 64);
        assertNull(RegionalWaterProjection.reserve(state, FLOODPLAIN, 4096));
        state.credit(FLOODPLAIN, M3, UPSTREAM);
        try (var reservation = RegionalWaterProjection.reserve(state, FLOODPLAIN, 4096)) {
            assertNotNull(reservation);
            assertEquals(0, state.stored(FLOODPLAIN));
        }
        assertEquals(M3, state.stored(FLOODPLAIN));
        assertEquals(0, state.receipt(PROJECTION_OUT));
        try (var reservation = RegionalWaterProjection.reserve(state, FLOODPLAIN, 4096)) { reservation.commit(); }
        assertEquals(M3, state.receipt(PROJECTION_OUT));
        assertEquals(0, state.residualMilliUnits());
        assertEquals(M3, RegionalWaterProjection.returnParcel(state, 4096, false));
        assertEquals(M3, state.stored(SURFACE_RUNOFF));
        assertEquals(0, state.residualMilliUnits());
    }

    @Test void bankfullExcessFundsFloodplainAndRoutedUnitsAreExactlyMatched() {
        RegionalHydrologyState source = new RegionalHydrologyState(1, 0, 66);
        RegionalHydrologyState target = new RegionalHydrologyState(2, 0, 64);
        source.downstream = target.key;
        source.credit(RIVER, 40 * M3, UPSTREAM);
        RegionalHydrologyModel.advance(source, target, DRY, 2);
        assertTrue(source.stored(FLOODPLAIN) > 0);
        assertTrue(source.discharge > 0);
        assertEquals(source.receipt(DOWNSTREAM), target.receipt(UPSTREAM));
        assertEquals(40 * M3, source.totalStored() + target.totalStored());
        assertEquals(0, source.residualMilliUnits());
        assertEquals(0, target.residualMilliUnits());
    }

    @Test void containedRiverDoesNotCreateFloodWater() {
        RegionalHydrologyState source = new RegionalHydrologyState(1, 0, 66);
        RegionalHydrologyState target = new RegionalHydrologyState(2, 0, 64);
        source.downstream = target.key;
        source.credit(RIVER, M3, UPSTREAM);
        RegionalHydrologyModel.advance(source, target, DRY, 2);
        assertEquals(0, source.stored(FLOODPLAIN));
    }

    @Test void saturatedAquiferKeepsRejectedRechargeInSoilWithoutLosingWater() {
        var state = new RegionalHydrologyState(1, 0, 64);
        state.credit(GROUNDWATER, 1024 * M3, UPSTREAM);
        state.credit(SOIL, 100 * M3, UPSTREAM);
        RegionalHydrologyModel.advance(state, null, DRY, 2);
        assertEquals(100 * M3, state.stored(SOIL));
        assertEquals(0, state.lastRecharge);
        assertTrue(state.lastBaseflow > 0);
        assertEquals(1124 * M3, state.totalStored());
        assertEquals(0, state.residualMilliUnits());
    }

    @Test void depressionFillsBeforeSpillAndClosedFrontierDoesNotExport() {
        RegionalHydrologyState lake = new RegionalHydrologyState(1, 0, 64);
        RegionalHydrologyState target = new RegionalHydrologyState(2, 0, 62);
        lake.downstream = target.key;
        lake.spillElevation = 65;
        lake.credit(LAKE, 200 * M3, UPSTREAM);
        RegionalHydrologyModel.advance(lake, target, DRY, 2);
        assertEquals(0, lake.receipt(DOWNSTREAM));
        lake.credit(LAKE, 100 * M3, UPSTREAM);
        RegionalHydrologyModel.advance(lake, target, DRY, 2);
        assertTrue(lake.receipt(DOWNSTREAM) > 0);
        long exported = lake.receipt(DOWNSTREAM);
        lake.downstream = RegionalHydrologyState.NO_OUTLET;
        RegionalHydrologyModel.advance(lake, null, DRY, 2);
        assertEquals(exported, lake.receipt(DOWNSTREAM));
        assertEquals(0, lake.residualMilliUnits());
    }

    @Test void stormHydrographRisesAndFallsWithRetainedRunoff() {
        RegionalHydrologyState river = new RegionalHydrologyState(1, 0, 66);
        RegionalHydrologyState outlet = new RegionalHydrologyState(2, 0, 64);
        river.downstream = outlet.key;
        river.soil = SoilHydrologyModel.SoilProfile.IMPERMEABLE;
        river.precipitationFraction = 1;
        double early = 0, peak = 0;
        for (int i = 0; i < 60; i++) {
            RegionalHydrologyModel.advance(river, outlet, WEATHER, 2);
            if (i == 2) early = river.discharge;
            peak = Math.max(peak, river.discharge);
        }
        assertTrue(peak > early);
        assertTrue(river.stored(SURFACE_RUNOFF) > 0);
        river.precipitationFraction = 0;
        for (int i = 0; i < 300; i++) RegionalHydrologyModel.advance(river, outlet, WEATHER, 2);
        assertTrue(river.discharge < peak * .25);
        assertEquals(0, river.residualMilliUnits());
        assertEquals(0, outlet.residualMilliUnits());
    }

    @Test void oceanEvaporationNamesItsBoundaryAndCreditsExactlyTheDebitedVapor() {
        RegionalHydrologyState ocean = new RegionalHydrologyState(1, 0, 63);
        ocean.ocean = true;
        ocean.precipitationFraction = 1;
        ocean.sunlight = 1;
        var exchange = RegionalHydrologyModel.advance(ocean, null, WEATHER, 2);
        assertTrue(exchange.evaporationMilliUnits() > 0);
        assertEquals(exchange.evaporationMilliUnits(), ocean.receipt(OCEAN_IN));
        assertEquals(exchange.evaporationMilliUnits(), ocean.receipt(EVAPORATION));
        assertEquals(exchange.precipitationMilliUnits(), ocean.receipt(OCEAN_OUT));
        assertEquals(0, ocean.residualMilliUnits());
    }

    @Test void persistedCoarseCatchupMatchesContinuousEvolutionForSameForcing() {
        RegionalHydrologyState continuous = new RegionalHydrologyState(1, 0, 64);
        continuous.precipitationFraction = .7;
        RegionalHydrologyState reloaded = RegionalHydrologyState.load(continuous.save());
        for (int i = 0; i < 200; i++) {
            RegionalHydrologyModel.advance(continuous, null, WEATHER, 2);
            continuous.lastSimulationTick += 40;
        }
        for (int batch = 0; batch < 50; batch++) {
            for (int step = 0; step < 4; step++) {
                RegionalHydrologyModel.advance(reloaded, null, WEATHER, 2);
                reloaded.lastSimulationTick += 40;
            }
            reloaded = RegionalHydrologyState.load(reloaded.save());
        }
        assertArrayEquals(continuous.storage.storedSnapshot(), reloaded.storage.storedSnapshot());
        assertArrayEquals(continuous.receipts, reloaded.receipts);
        assertEquals(8000, reloaded.lastSimulationTick);
        assertEquals(0, reloaded.residualMilliUnits());
    }

    @Test void physicalSaveNeverEvictsOwnedWaterWhenAdmissionCapFalls() {
        RegionalHydrologySavedData data = new RegionalHydrologySavedData();
        RegionalHydrologyState first = new RegionalHydrologyState(1, 500, 64);
        first.credit(SNOW, M3, PRECIPITATION);
        assertTrue(data.admit(first, 1));
        assertFalse(data.admit(new RegionalHydrologyState(2, 0, 64), 1));
        RegionalHydrologySavedData restored = RegionalHydrologySavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(M3, restored.state(1).stored(SNOW));
        assertEquals(500, restored.state(1).lastSimulationTick);
        assertThrows(IllegalArgumentException.class, () -> RegionalHydrologySavedData.load(new CompoundTag(), null));
    }

    @Test void analyticalCatchupKeepsAquiferMemoryAndDoesNotAccumulateBudgetDebt() {
        var continuous = new RegionalHydrologyState(1, 0, 64);
        continuous.credit(GROUNDWATER, 100 * M3, UPSTREAM);
        var coarse = RegionalHydrologyState.load(continuous.save());
        for (int i = 0; i < 30; i++) RegionalHydrologyModel.advance(continuous, null, DRY, 2);
        RegionalHydrologyModel.advance(coarse, null, DRY, 60);
        assertEquals(continuous.stored(GROUNDWATER), coarse.stored(GROUNDWATER), 30);
        assertEquals(continuous.totalStored(), coarse.totalStored());
        assertEquals(0, coarse.residualMilliUnits());
        long lag = 11000;
        for (int step = 4; step > 0; step--) lag -= RegionalHydrologyManager.catchupStepTicks(lag, step);
        assertTrue(lag < RegionalHydrologyModel.STEP_TICKS);
    }

    @Test void priorityFloodRoutesPitAcrossChunksWithoutCycles() {
        Map<Long, RegionalHydrologyState> states = new HashMap<>();
        for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
            long key = ChunkPos.asLong(x, z);
            states.put(key, new RegionalHydrologyState(key, 0, x == 0 && z == 0 ? 0 : 10));
        }
        RegionalDrainageGraph graph = new RegionalDrainageGraph(states);
        int steps = 0;
        while (!graph.advance(1)) assertTrue(++steps < 200);
        RegionalHydrologyState pit = states.get(0L);
        assertEquals(10, pit.spillElevation);
        assertNotEquals(RegionalHydrologyState.NO_OUTLET, pit.downstream);
        for (RegionalHydrologyState state : states.values()) {
            HashSet<Long> visited = new HashSet<>();
            while (state != null) {
                assertTrue(visited.add(state.key));
                state = states.get(state.downstream);
            }
        }
    }

    @Test void knownDownhillNeighborIsNotMistakenForAnUnknownFrontierDrain() {
        var high = new RegionalHydrologyState(ChunkPos.asLong(0, 0), 0, 70);
        var low = new RegionalHydrologyState(ChunkPos.asLong(1, 0), 0, 60);
        Map<Long, RegionalHydrologyState> states = Map.of(high.key, high, low.key, low);
        var graph = new RegionalDrainageGraph(states);
        while (!graph.advance(1)) { }
        assertEquals(low.key, high.downstream);
        assertEquals(512, low.contributingArea);
        assertEquals(RegionalHydrologyState.NO_OUTLET, low.downstream);
    }
}
