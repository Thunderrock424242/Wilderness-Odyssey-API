package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only operator budget inspection; no imports, terrain scans, repairs or chunk tickets. */
public final class RegionalBudgetDiagnostics {
    private RegionalBudgetDiagnostics() { }

    public static List<String> describe(ServerLevel level, BlockPos position) {
        long key = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
        RegionalHydrologyState state = RegionalHydrologySavedData.get(level).state(key);
        if (state == null) return List.of("No physical regional inventory here (not admitted, disabled, or at admission cap).");
        List<String> lines = new ArrayList<>();
        lines.add("Regional water " + new ChunkPos(key) + ", basin=" + state.basin
                + ", last simulated tick=" + state.lastSimulationTick + ", cached forcing tick=" + state.forcingTick);
        for (HydrologicReservoir reservoir : HydrologicReservoir.values()) {
            if (reservoir == HydrologicReservoir.CANONICAL_PROJECTION) continue;
            lines.add(reservoir.name().toLowerCase(Locale.ROOT) + "=" + quantity(state.stored(reservoir)));
        }
        for (RegionalHydrologyState.Boundary boundary : RegionalHydrologyState.Boundary.values()) {
            lines.add(boundary.name().toLowerCase(Locale.ROOT) + "=" + quantity(state.receipt(boundary)));
        }
        long detailed = TemporaryFloodSavedData.get(level).ownedMilliUnits(key);
        lines.add("canonical detailed claims=" + quantity(detailed) + " (already debited; not regional storage)");
        WaterBudgetAuditor.Report report = WaterBudgetAuditor.audit(state, RegionalHydrologyConfig.tolerance());
        long residual = report.residual();
        boolean warning = report.warning();
        lines.add((warning ? "WARNING: " : "OK: ") + "regional residual=" + residual + " milli-units"
                + ", tolerance=" + RegionalHydrologyConfig.tolerance());
        lines.add(String.format(Locale.ROOT, "Q=%.5f m3/s, velocity=%.3f m/s, stage=%.3f m, area=%.0f m2, spill=%.2f m, temperature=%.2f C",
                state.discharge, state.velocity, state.stage, state.contributingArea, state.spillElevation, state.temperatureCelsius));
        lines.add("downstream=" + (state.downstream == RegionalHydrologyState.NO_OUTLET
                ? state.ocean ? "ocean boundary" : "closed unknown frontier" : new ChunkPos(state.downstream)));
        lines.add("last coarse interval (milli-units)=" + state.lastFlux);
        lines.add(com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry.diagnostics(level));
        lines.add(com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager.get().diagnostics(level));
        var hydrology = WatershedSimulationDiagnostics.snapshot(level);
        lines.add("hydrology/river intervals updated=" + hydrology.processedChunks()
                + ", admitted regions=" + hydrology.queuedChunks() + ", projected cells=" + hydrology.floodPlacements()
                + ", returned cells=" + hydrology.floodRemovals() + ", solver microseconds=" + hydrology.elapsedMicros());
        var coast = com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManager.get().balanceAt(level, position);
        if (coast != null) lines.add("coastal derived-grid balance=" + coast + " (not physical inventory)");
        return List.copyOf(lines);
    }

    private static String quantity(long milliUnits) {
        return String.format(Locale.ROOT, "%.6f m3 [%d milli-units]",
                milliUnits / (double) HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK, milliUnits);
    }
}
