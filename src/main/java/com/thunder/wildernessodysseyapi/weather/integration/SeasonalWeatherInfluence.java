package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional read-only boundary for future season mods or pack-owned calendars.
 *
 * <p>Adapters run on the server thread while environment inputs are captured.
 * They must not retain the level or force-load terrain. The default adapter is
 * neutral, so this foundation has no season-mod dependency.</p>
 */
@FunctionalInterface
public interface SeasonalWeatherInfluence {

    SeasonalWeatherInfluence NONE = (level, cell) -> SeasonalOffset.NONE;

    /** Returns immutable temperature and humidity adjustments for one cell. */
    SeasonalOffset sample(ServerLevel level, AtmosphereCellKey cell);

    /** Immutable optional seasonal adjustment. */
    record SeasonalOffset(double temperatureCelsius, double humidity) {
        public static final SeasonalOffset NONE = new SeasonalOffset(0.0, 0.0);

        public SeasonalOffset {
            temperatureCelsius = Double.isFinite(temperatureCelsius)
                    ? Math.max(-30.0, Math.min(30.0, temperatureCelsius)) : 0.0;
            humidity = Double.isFinite(humidity)
                    ? Math.max(-1.0, Math.min(1.0, humidity)) : 0.0;
        }
    }
}
