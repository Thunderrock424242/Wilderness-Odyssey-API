package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional read-only boundary for season mods or pack-owned calendars.
 *
 * <p>Adapters run on the server thread while environment inputs are captured.
 * They must not retain the level or force-load terrain. The default adapter is
 * neutral, so this foundation has no season-mod dependency.</p>
 */
@FunctionalInterface
public interface SeasonalWeatherInfluence {

    SeasonalWeatherInfluence NONE = (level, cell, cellSize) -> SeasonalOffset.NONE;

    /** Returns immutable seasonal adjustments for one atmospheric cell. */
    SeasonalOffset sample(ServerLevel level, AtmosphereCellKey cell, int cellSize);

    /**
     * Immutable optional seasonal adjustment.
     *
     * @param temperatureCelsius air-temperature shift
     * @param humidity relative-humidity shift
     * @param storminess convective development shift
     * @param evaporationMultiplier seasonal surface evaporation multiplier
     */
    record SeasonalOffset(
            double temperatureCelsius,
            double humidity,
            double storminess,
            double evaporationMultiplier
    ) {
        public static final SeasonalOffset NONE = new SeasonalOffset(0.0, 0.0, 0.0, 1.0);

        public SeasonalOffset {
            temperatureCelsius = Double.isFinite(temperatureCelsius)
                    ? Math.max(-30.0, Math.min(30.0, temperatureCelsius)) : 0.0;
            humidity = Double.isFinite(humidity)
                    ? Math.max(-1.0, Math.min(1.0, humidity)) : 0.0;
            storminess = Double.isFinite(storminess)
                    ? Math.max(-1.0, Math.min(1.0, storminess)) : 0.0;
            evaporationMultiplier = Double.isFinite(evaporationMultiplier)
                    ? Math.max(0.25, Math.min(2.0, evaporationMultiplier)) : 1.0;
        }
    }
}
