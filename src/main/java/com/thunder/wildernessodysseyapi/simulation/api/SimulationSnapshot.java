package com.thunder.wildernessodysseyapi.simulation.api;

import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemRegionSnapshot;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationRegion;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationTrigger;

import java.util.Optional;

/**
 * Immutable, world-reference-free input suitable for pure Data Engine work.
 *
 * <p>Workers may calculate from this record, but validation and mutation still
 * belong on the logical server thread through the owning subsystem's API.</p>
 */
public record SimulationSnapshot(
        SimulationRegion region,
        RegionalEnvironmentSnapshot environment,
        Optional<EcosystemRegionSnapshot> ecosystem,
        ActivityLevel activity,
        SimulationTrigger trigger,
        long gameTime,
        long elapsedTicks
) {
    public SimulationSnapshot {
        ecosystem = ecosystem == null ? Optional.empty() : ecosystem;
        gameTime = Math.max(0L, gameTime);
        elapsedTicks = Math.max(0L, elapsedTicks);
    }
}
