package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.weather.api.*;
import com.thunder.wildernessodysseyapi.weather.simulation.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real regional stores and physical weather share the same accepted water quantity. */
class PhysicalAtmosphericExchangeTest {
    private static final long CHUNK = new ChunkPos(0, 0).toLong();
    private static final AtmosphereCellKey CELL = new AtmosphereCellKey(0, 0);

    @Test
    void persistedEvaporationReceiptEqualsRegionalDebitAndIsNotCreditedTwice() {
        var region = new RegionalHydrologyState(CHUNK, 0, 64);
        region.credit(HydrologicReservoir.LAKE, 4096000, RegionalHydrologyState.Boundary.PRECIPITATION);
        long debit = region.debit(HydrologicReservoir.LAKE, 4096000, RegionalHydrologyState.Boundary.EVAPORATION);
        var book = book();
        book.publish(CHUNK, 0, debit, 0, 256, 40);
        book = AtmosphericWaterExchange.ReceiptBook.load(book.save());
        region = RegionalHydrologyState.load(region.save());
        var receipt = book.capture(CELL, 16);
        var state = AtmosphericPhysicalState.fromLegacy(WeatherSample.CLEAR);
        var result = new AtmosphereSimulationEngine().simulatePhysical(state, AtmosphereEnvironment.TEMPERATE,
                AtmosphereSimulationEngine.PhysicalNeighborhood.bounded(state, null, null, null, null),
                SimulationSettings.DEFAULT, .05, 16, receipt);
        double credited = (result.state().totalWaterKgPerSquareMetre() + result.flux().precipitationMm()
                - state.totalWaterKgPerSquareMetre()) * 256 / 1000;
        assertEquals(debit / (double) AtmosphericWaterExchange.MILLI_UNITS_PER_CUBIC_METRE, credited, 1.0E-12);
        assertEquals(0, region.residualMilliUnits());
        book.acknowledge(receipt);
        book = AtmosphericWaterExchange.ReceiptBook.load(book.save());
        assertEquals(0, book.capture(CELL, 16).evaporationMilliUnits());
    }

    @Test
    void allPrecipitationPhasesReachTheirStoresOnceAcrossRestart() {
        var book = book();
        var flux = new AtmosphericWaterFlux(2, 0, 0, 0, .125, .25, .5, 1, 2, 0, 0);
        assertTrue(book.publishPrecipitation(CELL, 16, flux, 40));
        book = AtmosphericWaterExchange.ReceiptBook.load(book.save());
        assertFalse(book.publishPrecipitation(CELL, 16, flux, 40));
        var region = new RegionalHydrologyState(CHUNK, 0, 64);
        region.airTemperature = -15;
        long accepted = book.applyPrecipitation(region);
        double unitsPerMm = 256 * AtmosphericWaterExchange.MILLI_UNITS_PER_CUBIC_METRE / 1000.0;
        assertEquals((long) (flux.precipitationMm() * unitsPerMm), accepted);
        assertEquals((long) (.125 * unitsPerMm), region.stored(HydrologicReservoir.SURFACE_RUNOFF));
        assertEquals((long) (2.75 * unitsPerMm), region.stored(HydrologicReservoir.SNOW));
        assertEquals((long) unitsPerMm, region.stored(HydrologicReservoir.ICE));
        assertEquals(0, book.applyPrecipitation(region));
        assertEquals(0, region.residualMilliUnits());
        region.precipitationFraction = 1;
        var exchange = RegionalHydrologyModel.advance(region, null,
                new RegionalHydrologyModel.Parameters(100, 0, .03, 1, 3600, false, false), 2);
        assertEquals(0, exchange.precipitationMilliUnits(), "Normalized rain must not duplicate committed physical rain");
        assertEquals(0, region.residualMilliUnits());
    }

    @Test
    void fractionalPrecipitationIsRetainedInsteadOfRoundedAwayEveryUpdate() {
        var book = book();
        var region = new RegionalHydrologyState(CHUNK, 0, 64);
        double oneMilliUnitMm = 1000.0 / (256 * AtmosphericWaterExchange.MILLI_UNITS_PER_CUBIC_METRE);
        var flux = new AtmosphericWaterFlux(1, 0, 0, 0, .25 * oneMilliUnitMm, 0, 0, 0, 0, 0, 0);
        for (int i = 1; i <= 4; i++) {
            book.publishPrecipitation(CELL, 16, flux, i);
            book = AtmosphericWaterExchange.ReceiptBook.load(book.save());
            assertEquals(i == 4 ? 1 : 0, book.applyPrecipitation(region));
        }
        assertEquals(1, region.totalStored());
        assertEquals(0, region.residualMilliUnits());
    }

    @Test
    void rejectedCapacityRemainsPendingUntilTheSurfaceCanAcceptIt() {
        var book = book();
        var region = new RegionalHydrologyState(CHUNK, 0, 64);
        region.credit(HydrologicReservoir.SURFACE_RUNOFF, Long.MAX_VALUE, RegionalHydrologyState.Boundary.PRECIPITATION);
        var flux = new AtmosphericWaterFlux(1, 0, 0, 0, .125, 0, 0, 0, 0, 0, 0);
        book.publishPrecipitation(CELL, 16, flux, 10);
        assertEquals(0, book.applyPrecipitation(region));
        book = AtmosphericWaterExchange.ReceiptBook.load(book.save());
        region.debit(HydrologicReservoir.SURFACE_RUNOFF, 131072, RegionalHydrologyState.Boundary.DOWNSTREAM);
        assertEquals(131072, book.applyPrecipitation(region));
        assertEquals(0, region.residualMilliUnits());
    }

    @Test
    void cellDepthIsDistributedByAdmittedAreaWithoutMultiplyingMassByMemberCount() {
        var book = book();
        long secondKey = new ChunkPos(1, 0).toLong();
        book.publish(secondKey, 0, 0, 0, 128, 0);
        var flux = new AtmosphericWaterFlux(1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0);
        book.publishPrecipitation(CELL, 32, flux, 20);
        var a = new RegionalHydrologyState(CHUNK, 0, 64);
        var b = new RegionalHydrologyState(secondKey, 0, 64);
        assertEquals(1048576, book.applyPrecipitation(a));
        assertEquals(524288, book.applyPrecipitation(b));
        assertEquals(384.0 / 1024, book.capture(CELL, 32).coveredFraction());
    }

    private static AtmosphericWaterExchange.ReceiptBook book() {
        var book = new AtmosphericWaterExchange.ReceiptBook();
        book.publish(CHUNK, 0, 0, 0, 256, 0);
        return book;
    }
}
