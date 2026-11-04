package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import org.junit.jupiter.api.Test;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.FLOODPLAIN;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;
import static org.junit.jupiter.api.Assertions.*;

class CoastalStormSurgeTest {
    private static final long M3 = 4096000;

    @Test
    void externallyDrainedClaimsCannotRetainPhantomStageAfterReload() {
        var state = coast();
        state.credit(FLOODPLAIN, M3, OCEAN_IN);
        try (var parcel = RegionalWaterProjection.reserve(state, FLOODPLAIN, 4096)) { parcel.commit(); }
        var ledger = new TemporaryFloodSavedData();
        var position = new net.minecraft.core.BlockPos(2, 64, 3);
        assertTrue(ledger.record(position, 0, 0, 16, null, SurfaceWaterKind.FLOOD, 4096, state.key));
        assertEquals(4096, ledger.releaseOwnedUnits(position, 4096));
        state = RegionalHydrologyState.load(state.save());
        ledger = TemporaryFloodSavedData.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        CoastalStormSurge.advance(state, 0, 120, 2, ledger.ownedMilliUnits(state.key));
        assertEquals(0, state.stageMetres(), "An exported parcel is no longer live detailed water");
        long before = state.receipt(OCEAN_IN);
        CoastalStormSurge.advance(state, 1.0 / 256, 120, 2, ledger.ownedMilliUnits(state.key));
        assertTrue(state.receipt(OCEAN_IN) > before, "A later storm must replace externally withdrawn water");
        assertEquals(0, state.residualMilliUnits());
    }

    @Test
    void generatedAndProjectedCoastalColumnsShareOneAbsoluteMeanSurface() {
        long representative = new net.minecraft.core.BlockPos(2, 63, 3).asLong();
        float generated = WatershedServices.composeSurfaceHeight(63 + 8f / 9, 1.5f, representative, true);
        float projected = WatershedServices.composeSurfaceHeight(64 + 8f / 9, 1.5f, representative, true);
        assertEquals(65.38889, generated, .00001);
        assertEquals(generated, projected, .00001, "Canonical rise must not receive the full surge a second time");
        assertEquals(65.88889, WatershedServices.composeSurfaceHeight(64 + 8f / 9, 1, representative, false), .00001);
    }

    @Test
    void coastalProjectionNeverFillsAboveItsFundedMeanWaterline() {
        assertFalse(TemporaryFloodManager.withinFundedStage(true, .5, 63, 64));
        assertTrue(TemporaryFloodManager.withinFundedStage(true, 1, 63, 64));
        assertTrue(TemporaryFloodManager.withinFundedStage(true, 3, 63, 66));
        assertFalse(TemporaryFloodManager.withinFundedStage(true, 3, 63, 67));
        assertFalse(TemporaryFloodManager.withinFundedStage(true, Double.NaN, 63, 64));
        assertTrue(TemporaryFloodManager.withinFundedStage(false, 0, 63, 64), "Inland compatibility remains intact");
    }

    @Test
    void regionalIntegrationIsOptInAndDisablingItReturnsStoredSurge() {
        var state = coast();
        state.surgeTargetMetres = 1.5;
        var defaults = new RegionalHydrologyModel.Parameters(0, 0, .045, 1.25, 1800, false, false);
        RegionalHydrologyModel.advance(state, null, defaults, 2);
        assertEquals(0, state.receipt(OCEAN_IN), "Legacy/default calls cannot enable physical surge");
        var enabled = new RegionalHydrologyModel.Parameters(0, 0, .045, 1.25, 1800, false, false, true, 120);
        RegionalHydrologyModel.advance(state, null, enabled, 2);
        long funded = state.stored(FLOODPLAIN);
        assertTrue(funded > 0);
        RegionalHydrologyModel.advance(state, null, defaults, 2);
        assertEquals(0, state.totalStored());
        assertEquals(state.receipt(OCEAN_IN), state.receipt(OCEAN_OUT));
        assertEquals(0, state.stageMetres());
        assertEquals(0, state.residualMilliUnits());
    }

    @Test
    void savedHistoricalSurgeForcingSurvivesReloadAndOldSavesDefaultToCalm() {
        var state = coast();
        state.surgeTargetMetres = 1.25;
        var tag = state.save();
        assertEquals(1.25, RegionalHydrologyState.load(tag).surgeTargetMetres);
        tag.remove("surge_target");
        assertEquals(0, RegionalHydrologyState.load(tag).surgeTargetMetres);
    }

    @Test
    void onshoreStormsBuildMoreSurgeThanOffshoreWindAndCalmWeatherIsDry() {
        double onshore = CoastalStormSurge.targetMetres(.8, 1, 1, 2);
        double offshore = CoastalStormSurge.targetMetres(.8, 1, -1, 2);
        assertTrue(onshore > offshore && offshore > 0);
        assertTrue(onshore <= 2);
        assertEquals(0, CoastalStormSurge.targetMetres(1, 0, 0, 2));
        assertEquals(0, CoastalStormSurge.targetMetres(.8, 0, 1, 2), "Geometry/weather must contain a storm");
    }

    @Test
    void risingWaterUsesExactOceanReceiptsAndNeverOvershootsItsTarget() {
        var state = coast();
        CoastalStormSurge.advance(state, 1, 120, 2);
        assertTrue(state.stored(FLOODPLAIN) > 0 && state.stored(FLOODPLAIN) < 256 * M3);
        assertEquals(state.stored(FLOODPLAIN), state.receipt(OCEAN_IN));
        for (int i = 0; i < 2000; i++) CoastalStormSurge.advance(state, 1, 120, 2);
        assertEquals(256 * M3, state.stored(FLOODPLAIN));
        assertEquals(1, state.stageMetres(), 1e-9);
        assertEquals(0, state.residualMilliUnits());
    }

    @Test
    void projectingAndReloadingWaterCannotCauseTheOceanToFundItAgain() {
        var state = coast();
        for (int i = 0; i < 2000; i++) CoastalStormSurge.advance(state, 1, 120, 2);
        try (var parcel = RegionalWaterProjection.reserve(state, FLOODPLAIN, 4096)) {
            assertNotNull(parcel);
            parcel.commit();
        }
        state = RegionalHydrologyState.load(state.save());
        long before = state.receipt(OCEAN_IN);
        for (int i = 0; i < 50; i++) CoastalStormSurge.advance(state, 1, 120, 2, M3);
        assertEquals(before, state.receipt(OCEAN_IN), "Detailed projection is already funded");
        assertEquals(256 * M3, state.totalStored() + M3);
        assertEquals(1, state.stageMetres(), 1e-9);
        assertEquals(0, state.residualMilliUnits());
    }

    @Test
    void recessionReturnsEveryUnprojectedAndLaterReturnedUnitToTheOcean() {
        var state = coast();
        state.credit(FLOODPLAIN, 256 * M3, OCEAN_IN);
        try (var parcel = RegionalWaterProjection.reserve(state, FLOODPLAIN, 4096)) {
            assertNotNull(parcel);
            parcel.commit();
        }
        for (int i = 0; i < 2000; i++) CoastalStormSurge.advance(state, 0, 120, 2, M3);
        assertEquals(0, state.stored(FLOODPLAIN), "Detailed claims need an empty regional pool to recede");
        RegionalWaterProjection.returnParcel(state, 4096, false);
        RegionalHydrologyModel.advance(state, null,
                new RegionalHydrologyModel.Parameters(0, 0, .045, 1.25, 1800, false, false), 2);
        assertEquals(0, state.totalStored());
        assertEquals(state.receipt(OCEAN_IN), state.receipt(OCEAN_OUT));
        assertEquals(0, state.residualMilliUnits());
    }

    @Test
    void anInlandRegionCannotInventAnOceanBoundary() {
        var inland = new RegionalHydrologyState(0, 0, 64);
        CoastalStormSurge.advance(inland, 2, 120, 2);
        assertEquals(0, inland.totalStored());
        assertEquals(0, inland.receipt(OCEAN_IN));
    }

    @Test
    void invalidOrZeroTimeCannotAdmitWater() {
        var state = coast();
        assertThrows(IllegalArgumentException.class, () -> CoastalStormSurge.advance(state, 1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> CoastalStormSurge.advance(state, 1, 120, Double.NaN));
        CoastalStormSurge.advance(state, 1, 120, 0);
        assertEquals(0, state.totalStored());
    }

    private static RegionalHydrologyState coast() {
        var state = new RegionalHydrologyState(0, 0, 63);
        state.ocean = true;
        return state;
    }
}
