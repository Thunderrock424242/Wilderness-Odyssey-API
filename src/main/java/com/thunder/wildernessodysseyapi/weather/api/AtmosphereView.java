package com.thunder.wildernessodysseyapi.weather.api;

import java.util.Objects;

/**
 * Immutable view of one authoritative atmospheric cell.
 *
 * <p>The revision supports stale-result rejection and ordered networking. Tick
 * fields describe server simulation activity without exposing the mutable cell.</p>
 *
 * @param key atmospheric cell coordinate
 * @param sample immutable cell-center weather values
 * @param revision monotonic cell revision
 * @param lastSimulatedTick last server tick on which the cell was advanced
 * @param lastActiveTick last server tick on which activity kept the cell awake
 */
public record AtmosphereView(
        AtmosphereCellKey key,
        WeatherSample sample,
        long revision,
        long lastSimulatedTick,
        long lastActiveTick
) {
    public AtmosphereView {
        key = Objects.requireNonNull(key, "key");
        sample = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
        revision = Math.max(0L, revision);
        lastSimulatedTick = Math.max(0L, lastSimulatedTick);
        lastActiveTick = Math.max(0L, lastActiveTick);
    }
}
