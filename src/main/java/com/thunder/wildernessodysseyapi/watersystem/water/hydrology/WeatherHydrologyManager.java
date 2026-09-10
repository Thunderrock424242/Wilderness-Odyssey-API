package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.minecraft.server.level.ServerLevel;

/**
 * Compatibility entry point for the old weather-only hydrology path. It now
 * shares finite regional storage instead of independently manufacturing thaw
 * or calculating a second evaporation demand against canonical blocks.
 */
public final class WeatherHydrologyManager {
    private WeatherHydrologyManager() { }

    public static void tickLevel(ServerLevel level) {
        if (level != null) WatershedSimulationManager.tickLevel(level);
    }
}
