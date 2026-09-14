package com.thunder.wildernessodysseyapi.weather.api;

import java.util.Objects;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericPhysicalState;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereEnvironment;

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
 * @param lastActiveTick last server tick with player interest; not a physical forcing
 * @param physicalState conserved physical column retained by the server
 * @param environment detached cached forcing, safe to retain while terrain is unloaded
 */
public record AtmosphereView(
        AtmosphereCellKey key,
        WeatherSample sample,
        long revision,
        long lastSimulatedTick,
        long lastActiveTick,
        AtmosphericPhysicalState physicalState,
        AtmosphereEnvironment environment
) {
    /** Retains the original public and network construction shape. */
    public AtmosphereView(AtmosphereCellKey key, WeatherSample sample, long revision,
            long lastSimulatedTick, long lastActiveTick) {
        this(key, sample, revision, lastSimulatedTick, lastActiveTick,
                AtmosphericPhysicalState.fromLegacy(sample), AtmosphereEnvironment.TEMPERATE);
    }

    public AtmosphereView {
        key = Objects.requireNonNull(key, "key");
        sample = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
        revision = Math.max(0L, revision);
        lastSimulatedTick = Math.max(0L, lastSimulatedTick);
        lastActiveTick = Math.max(0L, lastActiveTick);
        physicalState = physicalState == null ? AtmosphericPhysicalState.fromLegacy(sample) : physicalState;
        environment = Objects.requireNonNullElse(environment, AtmosphereEnvironment.TEMPERATE);
    }
}
