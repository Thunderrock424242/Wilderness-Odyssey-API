package com.thunder.wildernessodysseyapi.simulation.region;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;

import java.util.Objects;

/** Immutable diagnostic state retained only for recently processed regions. */
public record RegionalSimulationState(
        SimulationRegion region,
        ActivityLevel activity,
        SimulationTrigger lastTrigger,
        long lastRequestedTick,
        long lastProcessedTick,
        long updateCount
) {
    public RegionalSimulationState {
        region = Objects.requireNonNull(region, "Simulation region is required");
        activity = Objects.requireNonNull(activity, "Activity level is required");
        lastTrigger = Objects.requireNonNull(lastTrigger, "Simulation trigger is required");
        lastRequestedTick = Math.max(0L, lastRequestedTick);
        lastProcessedTick = Math.max(0L, lastProcessedTick);
        updateCount = Math.max(0L, updateCount);
    }
}
