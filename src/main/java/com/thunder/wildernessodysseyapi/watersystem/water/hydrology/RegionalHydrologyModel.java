package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.*;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;

/** Pure finite reservoir integration. The runtime supplies cached weather, topology and elapsed time. */
public final class RegionalHydrologyModel {
    public static final long STEP_TICKS = 40;
    private static final double UNITS = HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK;

    private RegionalHydrologyModel() { }

    /** Executes one bounded coarse interval, returning exact atmospheric receipts. */
    public static Exchange advance(RegionalHydrologyState state, RegionalHydrologyState downstream,
                                   Parameters parameters, double dt) {
        if (!Double.isFinite(dt) || dt <= 0 || dt > 3600.0) {
            throw new IllegalArgumentException("Regional hydrology requires a positive step at most one simulation hour");
        }
        // Weather rates are normalized; this explicit conversion defines their hydrologic boundary.
        double rainExact = state.precipitationFraction * parameters.rainfallMmPerGameHour
                * 0.001 / 50.0 * RegionalHydrologyState.AREA * dt * UNITS + state.precipitationRemainder;
        long rainRequested = units(rainExact);
        state.precipitationRemainder = rainExact - rainRequested;
        boolean snowfall = state.snowing || state.airTemperature <= 0;
        long initialFlood = state.stored(FLOODPLAIN);
        long initialLake = state.stored(LAKE);
        long rain = state.credit(snowfall ? SNOW : SURFACE_RUNOFF,
                rainRequested, PRECIPITATION);
        // Any rejected rain still belongs to the atmosphere, not a disappeared regional input.
        long melt = SnowWaterEquivalentModel.advance(new SnowWaterEquivalentModel.Input(
                state.stored(SNOW), 0, false, state.airTemperature, state.sunlight, state.wind,
                0.2, state.precipitationFraction, 0.00015 * RegionalHydrologyState.AREA, dt))
                .meltMilliUnits();
        melt = state.storage.transfer(SNOW, SURFACE_RUNOFF, melt);
        state.lastMelt = melt / UNITS / dt;

        double etExact = parameters.evaporationMmPerGameHour * 0.001 / 50.0
                * RegionalHydrologyState.AREA * dt * UNITS
                * Math.max(0, 1.0 - state.humidity) * (0.2 + 0.8 * state.sunlight)
                * (1.0 + state.wind * 0.3) * Math.max(0, Math.min(2, (state.airTemperature + 10) / 30))
                + state.evaporationRemainder;
        long potentialEt = units(etExact);
        state.evaporationRemainder = etExact - potentialEt;
        long et = 0;
        if (state.ocean) {
            // Oceans are an explicit effectively infinite boundary, never block-by-block stores.
            long oceanInput = state.credit(SURFACE_RUNOFF, potentialEt, OCEAN_IN);
            et = state.debit(SURFACE_RUNOFF, oceanInput, EVAPORATION);
            for (HydrologicReservoir reservoir : new HydrologicReservoir[]{SURFACE_RUNOFF, RIVER, LAKE, FLOODPLAIN}) {
                state.debit(reservoir, state.stored(reservoir), OCEAN_OUT);
            }
            state.lastFlux = new HydrologicFlux(rain, snowfall ? rain : 0, melt, 0, 0, et, 0, 0, 0, 0, 0, 0);
            return new Exchange(rain, et);
        }

        long soilCapacity = units(state.soil.porosity() * RegionalHydrologyState.AREA * UNITS);
        SoilHydrologyModel.Result soil = SoilHydrologyModel.advance(new SoilHydrologyModel.Input(
                state.soil, state.stored(SOIL), soilCapacity, state.stored(SURFACE_RUNOFF),
                potentialEt, state.vegetation, dt, RegionalHydrologyState.AREA));
        state.storage.transfer(SURFACE_RUNOFF, SOIL, soil.infiltrationMilliUnits());
        long aquiferCapacity = units(RegionalHydrologyState.AREA * 4 * UNITS);
        // A saturated aquifer rejects recharge back to the soil owner. Never
        // truncate legacy/excess storage merely to enforce the nominal capacity.
        long recharge = parameters.groundwaterEnabled
                ? state.storage.transfer(SOIL, GROUNDWATER, Math.min(soil.groundwaterRechargeMilliUnits(),
                        Math.max(0, aquiferCapacity - state.stored(GROUNDWATER)))) : 0;
        et += state.debit(SOIL, soil.evapotranspirationMilliUnits(), EVAPORATION);
        for (HydrologicReservoir reservoir : new HydrologicReservoir[]{SURFACE_RUNOFF, LAKE, RIVER, FLOODPLAIN}) {
            if (et >= potentialEt) break;
            et += state.debit(reservoir, potentialEt - et, EVAPORATION);
        }

        GroundwaterModel.PhysicalResult aquifer = GroundwaterModel.advancePhysical(
                new GroundwaterModel.PhysicalInput(state.stored(GROUNDWATER),
                        aquiferCapacity, 0,
                        parameters.groundwaterEnabled ? 1.0 / parameters.aquiferSeconds : 0,
                        1, 0, dt, true));
        long baseflow = state.storage.transfer(GROUNDWATER, SURFACE_RUNOFF, aquifer.dischargeMilliUnits());
        state.lastRecharge = recharge / UNITS / dt;
        state.lastBaseflow = baseflow / UNITS / dt;
        boolean lake = state.downstream == RegionalHydrologyState.NO_OUTLET
                || state.spillElevation > state.elevation + 0.01
                || state.feature == com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature.LAKE;
        // Drain runoff on a residence time, retaining storm memory instead of an instantaneous teleport.
        long runoff = units(state.stored(SURFACE_RUNOFF) * -Math.expm1(-dt / (12.0 + 30.0 * state.vegetation)));
        state.storage.transfer(SURFACE_RUNOFF, lake ? LAKE : RIVER, runoff);
        if (lake && downstream != null) {
            long containment = units(Math.max(0.25, state.spillElevation - state.elevation)
                    * RegionalHydrologyState.AREA * UNITS);
            long excess = Math.max(0, state.stored(LAKE) - containment);
            state.storage.transfer(LAKE, RIVER, units(excess * -Math.expm1(-dt / 8.0)));
        }
        double slope = downstream == null ? 0 : Math.max(0.0001,
                (state.elevation - downstream.elevation) / 16.0);
        RiverHydraulics.Result river = RiverHydraulics.evaluate(state.stored(RIVER) / UNITS,
                state.contributingArea, slope, parameters.roughness, parameters.bankfullDepth);
        long bankfull = units(river.bankfullVolume() * UNITS);
        state.storage.transfer(RIVER, FLOODPLAIN, Math.max(0, state.stored(RIVER) - bankfull));
        long released = downstream == null ? 0
                : state.routeTo(downstream, RIVER, units(river.discharge() * dt * UNITS));
        state.discharge = released / UNITS / dt;
        state.velocity = state.discharge > 0 ? river.velocity() : 0;
        state.stage = lake ? state.stored(LAKE) / UNITS / RegionalHydrologyState.AREA : river.depth();
        if (lake) {
            long lakeBanks = units(Math.max(parameters.bankfullDepth,
                    state.spillElevation - state.elevation) * RegionalHydrologyState.AREA * UNITS);
            state.storage.transfer(LAKE, FLOODPLAIN, Math.max(0, state.stored(LAKE) - lakeBanks));
        }
        state.storage.transfer(FLOODPLAIN, RIVER,
                Math.min(Math.max(0, bankfull - state.stored(RIVER)),
                        units(state.stored(FLOODPLAIN) * -Math.expm1(-dt / 120.0))));
        if (parameters.thermalEnabled) advanceTemperature(state, dt);
        state.lastFlux = new HydrologicFlux(rain, snowfall ? rain : 0, melt, soil.infiltrationMilliUnits(),
                runoff, et, recharge, baseflow, lake ? 0 : runoff, released,
                state.stored(FLOODPLAIN) - initialFlood, state.stored(LAKE) - initialLake);
        return new Exchange(rain, et);
    }

    private static void advanceTemperature(RegionalHydrologyState state, double dt) {
        double target = state.airTemperature + state.sunlight * 3.0;
        state.temperatureCelsius += (target - state.temperatureCelsius) * -Math.expm1(-dt / 1200.0);
        long liquid = state.stored(LAKE) + state.stored(RIVER) + state.stored(SURFACE_RUNOFF);
        // Aggregate phase storage only: no duplicate hidden liquid under decorative ice blocks.
        long phase = units(Math.abs(state.temperatureCelsius) * 0.00002 * RegionalHydrologyState.AREA * dt * UNITS);
        if (state.temperatureCelsius < 0 && liquid > 0) {
            for (HydrologicReservoir reservoir : new HydrologicReservoir[]{LAKE, RIVER, SURFACE_RUNOFF}) {
                phase -= state.storage.transfer(reservoir, ICE, phase);
            }
        } else if (state.temperatureCelsius > 0) {
            state.storage.transfer(ICE, SURFACE_RUNOFF, phase);
        }
    }

    static long units(double value) { return (long) Math.floor(Math.max(0, value)); }

    public record Exchange(long precipitationMilliUnits, long evaporationMilliUnits) { }
    public record Parameters(double rainfallMmPerGameHour, double evaporationMmPerGameHour,
                             double roughness, double bankfullDepth, double aquiferSeconds,
                             boolean groundwaterEnabled, boolean thermalEnabled) { }
}
