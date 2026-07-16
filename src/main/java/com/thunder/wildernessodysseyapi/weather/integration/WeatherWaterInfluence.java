package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import net.minecraft.server.level.ServerLevel;

/**
 * Read-only boundary through which atmosphere sampling observes surface water.
 *
 * <p>Implementations must run on the server thread, skip unloaded chunks, and
 * return only immutable aggregate data. They must never import, add, remove, or
 * otherwise mutate water authority state.</p>
 */
public interface WeatherWaterInfluence {

    /**
     * Samples or returns a cached surface-water aggregate for one atmosphere cell.
     *
     * @param level authoritative server level
     * @param cell cell being environmentally sampled
     * @param cellSize current atmosphere-cell width in blocks
     * @param refreshIntervalTicks minimum age before a cached aggregate is resampled
     * @return immutable normalized coverage values
     */
    WaterInfluenceSample sample(
            ServerLevel level,
            AtmosphereCellKey cell,
            int cellSize,
            int refreshIntervalTicks
    );

    /** Clears world-derived cached samples when their level unloads. */
    void clear();
}
