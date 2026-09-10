package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/** Builds a developer-facing exact storage and residual report for one region or basin. */
public final class WaterBudgetAuditor {

    private WaterBudgetAuditor() {
    }

    /** Adapts persisted physical receipts to the same exact diagnostic equation. */
    public static Report audit(RegionalHydrologyState state, long tolerance) {
        WaterBudget.Snapshot snapshot = new WaterBudget.Snapshot(
                state.receipt(RegionalHydrologyState.Boundary.PRECIPITATION),
                state.receipt(RegionalHydrologyState.Boundary.UPSTREAM),
                state.receipt(RegionalHydrologyState.Boundary.EVAPORATION),
                state.receipt(RegionalHydrologyState.Boundary.DOWNSTREAM),
                state.receipt(RegionalHydrologyState.Boundary.OCEAN_IN)
                        + state.receipt(RegionalHydrologyState.Boundary.PROJECTION_IN)
                        + state.receipt(RegionalHydrologyState.Boundary.LEGACY_RETURN),
                state.receipt(RegionalHydrologyState.Boundary.OCEAN_OUT)
                        + state.receipt(RegionalHydrologyState.Boundary.PROJECTION_OUT), state.openingStorage,
                state.totalStored(), state.residualMilliUnits());
        return audit(state.basin, state.storage, snapshot, tolerance);
    }

    public static Report audit(
            long basinId,
            HydrologicStorage storage,
            WaterBudget.Snapshot budget,
            long toleranceMilliUnits
    ) {
        if (storage == null || budget == null) {
            throw new IllegalArgumentException("Storage and budget snapshot are required");
        }
        long tolerance = Math.max(0L, toleranceMilliUnits);
        long residual = budget.residual();
        return new Report(
                basinId,
                budget.precipitationInput(),
                budget.upstreamInput(),
                budget.evapotranspirationOutput(),
                budget.downstreamOutput(),
                storage.stored(HydrologicReservoir.SNOW),
                storage.stored(HydrologicReservoir.SOIL),
                storage.stored(HydrologicReservoir.GROUNDWATER),
                storage.stored(HydrologicReservoir.SURFACE_RUNOFF),
                storage.stored(HydrologicReservoir.RIVER),
                storage.stored(HydrologicReservoir.LAKE),
                storage.stored(HydrologicReservoir.FLOODPLAIN),
                storage.stored(HydrologicReservoir.CANONICAL_PROJECTION),
                residual,
                tolerance,
                absoluteExceeds(residual, tolerance)
        );
    }

    private static boolean absoluteExceeds(long value, long tolerance) {
        if (value == Long.MIN_VALUE) {
            return true;
        }
        return Math.abs(value) > tolerance;
    }

    /** Exact fields suitable for `/wowater` diagnostics and bounded warning logs. */
    public record Report(
            long basinId,
            long precipitationInput,
            long upstreamInput,
            long evapotranspirationOutput,
            long downstreamOutput,
            long snowStorage,
            long soilStorage,
            long groundwaterStorage,
            long surfaceRunoffStorage,
            long riverStorage,
            long lakeStorage,
            long floodplainStorage,
            long canonicalDetailedStorage,
            long residual,
            long tolerance,
            boolean warning
    ) {
    }
}
