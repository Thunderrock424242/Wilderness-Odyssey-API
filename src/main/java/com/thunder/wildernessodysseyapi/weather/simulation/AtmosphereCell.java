package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

import java.util.Objects;

/**
 * Mutable server-owned state for one atmospheric cell.
 *
 * <p>This class is package-private so consumers cannot retain or modify live
 * simulation state. Public callers receive only {@link AtmosphereView}
 * records through {@link AtmosphereGrid}.</p>
 */
final class AtmosphereCell {

    private final AtmosphereCellKey key;
    private WeatherSample sample;
    private long revision;
    private long lastSimulatedTick;
    private long lastActiveTick;

    AtmosphereCell(
            AtmosphereCellKey key,
            WeatherSample sample,
            long revision,
            long lastSimulatedTick,
            long lastActiveTick
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.sample = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
        this.revision = Math.max(0L, revision);
        this.lastSimulatedTick = Math.max(0L, lastSimulatedTick);
        this.lastActiveTick = Math.max(0L, lastActiveTick);
    }

    AtmosphereView view() {
        return new AtmosphereView(key, sample, revision, lastSimulatedTick, lastActiveTick);
    }

    long revision() {
        return revision;
    }

    void markActive(long gameTick) {
        lastActiveTick = Math.max(lastActiveTick, Math.max(0L, gameTick));
    }

    boolean applyIfRevision(long expectedRevision, WeatherSample next, long gameTick) {
        if (revision != expectedRevision) {
            return false;
        }
        WeatherSample safeNext = Objects.requireNonNullElse(next, WeatherSample.CLEAR);
        lastSimulatedTick = Math.max(lastSimulatedTick, Math.max(0L, gameTick));
        if (sample.equals(safeNext)) {
            return false;
        }
        apply(safeNext, gameTick);
        return true;
    }

    void force(WeatherSample next, long gameTick) {
        apply(next, gameTick);
    }

    private void apply(WeatherSample next, long gameTick) {
        sample = Objects.requireNonNullElse(next, WeatherSample.CLEAR);
        revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1L;
        lastSimulatedTick = Math.max(lastSimulatedTick, Math.max(0L, gameTick));
    }
}
