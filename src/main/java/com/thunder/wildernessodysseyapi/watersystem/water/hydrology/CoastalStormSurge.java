package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.FLOODPLAIN;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;

/** Coarse, explicit open-ocean boundary exchange, not a second water inventory. */
public final class CoastalStormSurge {
    private CoastalStormSurge() { }

    /** Normalized pressure/wind drive a bounded target; rainfall has its own conserved owner. */
    public static double targetMetres(double pressure, double stormEnergy, double onshoreWind, double maximum) {
        if (!Double.isFinite(pressure + stormEnergy + onshoreWind + maximum)) return 0;
        double storm = Math.max(0, Math.min(1, (stormEnergy - .1) / .9));
        double onshore = Math.max(0, Math.min(1, onshoreWind));
        return Math.min(Math.max(0, Math.min(4, maximum)), storm
                * (Math.max(0, 1 - pressure) * 2 + onshore * onshore * 1.25));
    }

    static void advance(RegionalHydrologyState state, double targetMetres, double responseSeconds, double seconds) {
        advance(state, targetMetres, responseSeconds, seconds, 0);
    }

    /** Live projection comes from the exact ownership ledger, never lifetime export receipts. */
    static void advance(RegionalHydrologyState state, double targetMetres, double responseSeconds, double seconds,
                        long liveProjectionMilliUnits) {
        if (!Double.isFinite(targetMetres + responseSeconds + seconds) || responseSeconds <= 0
                || seconds < 0 || seconds > 3600 || liveProjectionMilliUnits < 0)
            throw new IllegalArgumentException("Invalid storm surge interval");
        if (!state.ocean || seconds == 0) return;
        long target = (long) (Math.max(0, Math.min(4, targetMetres)) * RegionalHydrologyState.AREA
                * HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK);
        long projected = liveProjectionMilliUnits;
        long total = Math.addExact(state.stored(FLOODPLAIN), projected);
        double response = -Math.expm1(-seconds / responseSeconds);
        if (target > total) {
            state.credit(FLOODPLAIN, Math.min(target - total, (long) Math.ceil((target - total) * response)), OCEAN_IN);
        } else if (total > target) {
            // Only unprojected storage can be withdrawn here. The canonical
            // recession owner returns detailed claims after this store empties.
            state.debit(FLOODPLAIN, Math.min(total - target, (long) Math.ceil((total - target) * response)), OCEAN_OUT);
        }
        state.stage = (state.stored(FLOODPLAIN) + projected)
                / (double) HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK / RegionalHydrologyState.AREA;
    }

}
