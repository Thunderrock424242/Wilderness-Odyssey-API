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
    private AtmosphericPhysicalState physicalState;
    private AtmosphereEnvironment environment;

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
        this.physicalState = AtmosphericPhysicalState.fromLegacy(this.sample);
        this.environment = AtmosphereEnvironment.TEMPERATE;
    }

    AtmosphereCell(AtmosphereView view) {
        this(view.key(), view.sample(), view.revision(), view.lastSimulatedTick(), view.lastActiveTick());
        physicalState = view.physicalState();
        environment = view.environment();
    }

    AtmosphereView view() {
        return new AtmosphereView(key, sample, revision, lastSimulatedTick, lastActiveTick, physicalState, environment);
    }

    /** Returns the immutable sample without allocating a public cell view. */
    WeatherSample sample() {
        return sample;
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

    /** Atomically commits physical inventory and its presentation under the existing revision guard. */
    boolean applyPhysicalIfRevision(long expectedRevision, AtmosphericPhysicalState nextState,
            WeatherSample nextSample, AtmosphereEnvironment nextEnvironment, long gameTick) {
        if (revision != expectedRevision) {
            return false;
        }
        boolean changed = !physicalState.equals(nextState) || !sample.equals(nextSample)
                || !environment.equals(nextEnvironment) || gameTick > lastSimulatedTick;
        physicalState = Objects.requireNonNull(nextState);
        sample = Objects.requireNonNull(nextSample);
        environment = Objects.requireNonNull(nextEnvironment);
        lastSimulatedTick = Math.max(lastSimulatedTick, gameTick);
        if (changed) {
            revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1L;
        }
        return changed;
    }

    private void apply(WeatherSample next, long gameTick) {
        sample = Objects.requireNonNullElse(next, WeatherSample.CLEAR);
        // Explicit legacy/operator edits start a new physical state exactly once.
        physicalState = AtmosphericPhysicalState.fromLegacy(sample);
        revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1L;
        lastSimulatedTick = Math.max(lastSimulatedTick, Math.max(0L, gameTick));
    }
}
