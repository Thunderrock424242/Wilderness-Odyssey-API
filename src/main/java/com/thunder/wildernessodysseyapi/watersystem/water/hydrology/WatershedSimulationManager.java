package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Existing lifecycle facade; regional finite storage now owns hydrologic evolution. */
public final class WatershedSimulationManager {
    private WatershedSimulationManager() { }

    /** Initializes metadata and physical storage only for a normally loaded chunk. */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (WaterSimulationConfig.watershedSimulationEnabled() || WaterSimulationConfig.weatherHydrologyEnabled()) {
            WatershedChunkState state = WatershedSavedData.get(level).getOrCreate(level, chunk);
            RegionalHydrologyManager.onChunkLoad(level, chunk, state);
        }
    }

    /** Advances bounded coarse state even without players; detailed mutations remain loaded-only. */
    public static void tickLevel(ServerLevel level) {
        RegionalHydrologyManager.tickLevel(level, WatershedSavedData.get(level));
    }

    public static void clearLevel(ServerLevel level) {
        RegionalHydrologyManager.clearLevel(level);
        WatershedSimulationDiagnostics.clear(level);
    }
}
