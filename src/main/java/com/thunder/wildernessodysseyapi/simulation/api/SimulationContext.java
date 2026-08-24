package com.thunder.wildernessodysseyapi.simulation.api;

import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemRegionSnapshot;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationRegion;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationTrigger;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-thread context for one bounded regional orchestration decision.
 *
 * <p>The live {@link ServerLevel} must never be captured by a worker. Use
 * {@link #immutableSnapshot()} when submitting pure calculation through the
 * existing Data Engine.</p>
 */
public record SimulationContext(
        ServerLevel level,
        SimulationRegion region,
        RegionalEnvironmentSnapshot environment,
        Optional<EcosystemRegionSnapshot> ecosystem,
        ActivityLevel activity,
        SimulationTrigger trigger,
        long gameTime,
        long elapsedTicks
) {
    public SimulationContext {
        level = Objects.requireNonNull(level, "Server level is required");
        region = Objects.requireNonNull(region, "Simulation region is required");
        environment = Objects.requireNonNull(environment, "Environment snapshot is required");
        ecosystem = ecosystem == null ? Optional.empty() : ecosystem;
        activity = Objects.requireNonNull(activity, "Activity level is required");
        trigger = Objects.requireNonNull(trigger, "Simulation trigger is required");
        gameTime = Math.max(0L, gameTime);
        elapsedTicks = Math.max(0L, elapsedTicks);
    }

    /** Copies the worker-safe portion of this server-thread context. */
    public SimulationSnapshot immutableSnapshot() {
        return new SimulationSnapshot(
                region,
                environment,
                ecosystem,
                activity,
                trigger,
                gameTime,
                elapsedTicks
        );
    }
}
