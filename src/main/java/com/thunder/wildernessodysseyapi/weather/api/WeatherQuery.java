package com.thunder.wildernessodysseyapi.weather.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-authoritative entry point for localized weather consumers.
 *
 * <p>Gameplay integrations query positions through this interface instead of
 * depending on atmospheric cell layout or Minecraft's global rain flag.</p>
 */
@FunctionalInterface
public interface WeatherQuery {

    /** Returns the interpolated immutable weather sample at a server position. */
    WeatherSample sample(ServerLevel level, BlockPos position);

    /** Returns whether localized rain is active at the position. */
    default boolean isRainingAt(ServerLevel level, BlockPos position) {
        return sample(level, position).isRaining();
    }

    /** Returns whether localized snow is active at the position. */
    default boolean isSnowingAt(ServerLevel level, BlockPos position) {
        return sample(level, position).isSnowing();
    }

    /** Returns normalized localized precipitation intensity. */
    default double precipitationIntensityAt(ServerLevel level, BlockPos position) {
        return sample(level, position).precipitationIntensity();
    }

    /** Returns normalized horizontal wind at the position. */
    default WindVector windAt(ServerLevel level, BlockPos position) {
        return sample(level, position).wind();
    }

    /** Returns relative humidity at the position. */
    default double humidityAt(ServerLevel level, BlockPos position) {
        return sample(level, position).humidity();
    }

    /** Returns local air temperature in degrees Celsius. */
    default double temperatureAt(ServerLevel level, BlockPos position) {
        return sample(level, position).temperature();
    }

    /** Returns normalized local atmospheric pressure. */
    default double pressureAt(ServerLevel level, BlockPos position) {
        return sample(level, position).pressure();
    }
}
