package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericWaterFlux;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.*;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;
import static org.junit.jupiter.api.Assertions.*;

/** Measures one finite pulse across rain receipts, routing, projection and recession. */
class StormRunoffConservationTest {
    private static final long SOURCE = new ChunkPos(0, 0).toLong();
    private static final long TARGET = new ChunkPos(1, 0).toLong();
    private static final long INPUT = 134_217_728;
    private static final long M3 = HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK;
    private static final RegionalHydrologyModel.Parameters DRY =
            new RegionalHydrologyModel.Parameters(12, 0, .045, .25, 1800, false, false);

    @Test
    void finiteStormRoutesFillsProjectsAndRecedesAcrossSavedOwners() {
        var source = source();
        var lake = new RegionalHydrologyState(TARGET, 0, 64);
        lake.feature = WaterFeature.LAKE;
        lake.soil = SoilHydrologyModel.SoilProfile.IMPERMEABLE;
        var book = new AtmosphericWaterExchange.ReceiptBook();
        book.publish(SOURCE, 0, 0, 0, 256, 0);
        var cell = new AtmosphereCellKey(0, 0);
        var rain = new AtmosphericWaterFlux(2, 0, 0, 0, 128, 0, 0, 0, 0, 0, 0);
        assertTrue(book.publishPrecipitation(cell, 16, rain, 40));
        assertEquals(INPUT, book.applyPrecipitation(source));
        assertEquals(0, book.applyPrecipitation(source));
        int projected = 0;
        boolean returned = false;
        boolean reloaded = false;
        boolean fundedTwoParcels = false;
        double peakStage = 0;
        double peakFlow = 0;
        for (int step = 0; step < 2000; step++) {
            RegionalHydrologyModel.advance(source, lake, DRY, 2);
            RegionalHydrologyModel.advance(lake, null, DRY, 2);
            peakStage = Math.max(peakStage, source.stageMetres());
            peakFlow = Math.max(peakFlow, source.dischargeCubicMetresPerSecond());
            if (projected < 8192 && !returned && source.stored(FLOODPLAIN) >= M3) {
                try (var parcel = RegionalWaterProjection.reserve(source, FLOODPLAIN, 4096)) {
                    assertNotNull(parcel);
                    parcel.commit();
                    projected += 4096;
                    fundedTwoParcels |= projected == 8192;
                }
            }
            if (!reloaded && projected > 0 && source.stored(FLOODPLAIN) > 0) {
                source = RegionalHydrologyState.load(source.save());
                lake = RegionalHydrologyState.load(lake.save());
                book = AtmosphericWaterExchange.ReceiptBook.load(book.save());
                assertFalse(book.publishPrecipitation(cell, 16, rain, 40), "Saved rainfall clock prevents replay");
                assertEquals(0, book.applyPrecipitation(source));
                reloaded = true;
            }
            if (projected > 0 && source.stored(FLOODPLAIN) == 0) {
                assertEquals(projected * 1000L, RegionalWaterProjection.returnParcel(source, projected, false));
                projected = 0;
                returned = true;
            }
            assertEquals(0, source.residualMilliUnits());
            assertEquals(0, lake.residualMilliUnits());
            assertEquals(source.receipt(DOWNSTREAM), lake.receipt(UPSTREAM));
            assertEquals(INPUT, source.totalStored() + lake.totalStored() + projected * 1000L);
        }
        assertTrue(peakStage > .25 && peakFlow > 0, "Storm must overtop its channel and route downstream");
        assertTrue(reloaded, "Save during funded active flooding");
        assertTrue(fundedTwoParcels, "Pulse must fund expansion beyond its first detailed parcel");
        assertTrue(lake.stored(LAKE) > 0 && lake.stageMetres() > 0);
        assertEquals(0, source.stored(FLOODPLAIN), "The floodplain must finish recession");
        assertTrue(returned, "Detailed water must return after the flood recedes");
        assertEquals(source.receipt(PROJECTION_OUT), source.receipt(PROJECTION_IN));
        assertTrue(source.dischargeCubicMetresPerSecond() < peakFlow);
    }

    @Test
    void finalFloodplainMilliUnitCannotRetainFundedFloodwaterForever() {
        var source = source();
        var target = new RegionalHydrologyState(TARGET, 0, 64);
        source.credit(FLOODPLAIN, 1, PRECIPITATION);
        RegionalHydrologyModel.advance(source, target, DRY, 2);
        assertEquals(0, source.stored(FLOODPLAIN), "A sub-quantum drain still needs finite forward progress");
        assertEquals(1, source.totalStored() + target.totalStored());
        assertEquals(0, source.residualMilliUnits());
    }

    @Test
    void aFullChannelRetainsTheTailWithoutOverfillingOrDiscardingIt() {
        var source = source();
        source.credit(RIVER, 4 * M3, PRECIPITATION);
        source.credit(FLOODPLAIN, 1, PRECIPITATION);
        RegionalHydrologyModel.advance(source, null, DRY, 2);
        assertEquals(1, source.stored(FLOODPLAIN));
        assertEquals(4 * M3, source.stored(RIVER));
        assertEquals(0, source.residualMilliUnits());
    }

    @Test
    void unknownFrontierRetainsTheEntireStormInsteadOfInventingAnOutlet() {
        var closed = source();
        closed.downstream = RegionalHydrologyState.NO_OUTLET;
        closed.credit(SURFACE_RUNOFF, INPUT, PRECIPITATION);
        for (int step = 0; step < 100; step++) {
            RegionalHydrologyModel.advance(closed, null, DRY, 2);
            assertEquals(INPUT, closed.totalStored());
            assertEquals(0, closed.receipt(DOWNSTREAM));
            assertEquals(0, closed.residualMilliUnits());
        }
    }

    private static RegionalHydrologyState source() {
        var source = new RegionalHydrologyState(SOURCE, 0, 66);
        source.downstream = TARGET;
        source.soil = SoilHydrologyModel.SoilProfile.IMPERMEABLE;
        source.physicalPrecipitation = true;
        return source;
    }
}
