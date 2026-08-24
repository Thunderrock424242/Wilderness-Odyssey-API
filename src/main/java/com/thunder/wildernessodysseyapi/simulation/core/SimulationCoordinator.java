package com.thunder.wildernessodysseyapi.simulation.core;

import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.performance.background.ElapsedTimeSimulation;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationContext;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationSystem;
import com.thunder.wildernessodysseyapi.simulation.integration.EcosystemSimulationBridge;
import com.thunder.wildernessodysseyapi.simulation.region.RegionalSimulationState;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds regional contexts and invokes registered systems without owning their state.
 *
 * <p>All work runs on the logical server thread after Data Engine admission.
 * The coordinator performs no world scan and processes at most the caller's
 * explicit region limit.</p>
 */
public final class SimulationCoordinator {
    private final SimulationRegistry registry;
    private final SimulationRegionManager regions;

    public SimulationCoordinator(SimulationRegistry registry, SimulationRegionManager regions) {
        this.registry = Objects.requireNonNull(registry, "Simulation registry is required");
        this.regions = Objects.requireNonNull(regions, "Simulation region manager is required");
    }

    /** Processes a bounded number of queued regions and isolates participant failures. */
    public ProcessingReport process(
            MinecraftServer server,
            int maximumRegions,
            SystemObserver observer
    ) {
        Objects.requireNonNull(server, "Minecraft server is required");
        Objects.requireNonNull(observer, "System observer is required");
        int processed = 0;
        int missingDimensions = 0;
        int systemUpdates = 0;
        int systemFailures = 0;

        while (processed < Math.max(0, maximumRegions)) {
            Optional<SimulationRegionManager.PendingRegion> next = regions.poll();
            if (next.isEmpty()) {
                break;
            }
            SimulationRegionManager.PendingRegion request = next.get();
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION,
                    request.region().dimension()
            );
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                regions.clearDimension(request.region().dimension());
                missingDimensions++;
                continue;
            }

            BlockPos anchor = request.region().anchor(level);
            long gameTime = level.getGameTime();
            Optional<RegionalSimulationState> previous = regions.state(request.region());
            long elapsedTicks = previous
                    .map(state -> ElapsedTimeSimulation.elapsedTicks(state.lastProcessedTick(), gameTime))
                    .orElse(0L);
            ActivityLevel activity = EcosystemSimulationBridge.activityAt(level, anchor);
            SimulationContext context = new SimulationContext(
                    level,
                    request.region(),
                    EnvironmentServices.query().sample(level, anchor),
                    EcosystemSimulationBridge.snapshotAt(level, anchor),
                    activity,
                    request.trigger(),
                    gameTime,
                    elapsedTicks
            );
            SystemDispatchReport dispatch = dispatchSystems(registry.systems(), context, observer);
            regions.complete(request, activity, gameTime);
            observer.onRegionProcessed(context, dispatch);
            systemUpdates += dispatch.executed();
            systemFailures += dispatch.failures();
            processed++;
        }
        return new ProcessingReport(processed, missingDimensions, systemUpdates, systemFailures);
    }

    /**
     * Executes participants in deterministic order; package access keeps the
     * failure-isolation behavior directly unit-testable without a live world.
     */
    static SystemDispatchReport dispatchSystems(
            List<SimulationSystem> systems,
            SimulationContext context,
            SystemObserver observer
    ) {
        int enabled = 0;
        int executed = 0;
        int skipped = 0;
        int failures = 0;
        for (SimulationSystem system : systems) {
            try {
                if (!system.isEnabled()) {
                    skipped++;
                    observer.onSystemSkipped(system);
                    continue;
                }
                enabled++;
                if (!system.shouldUpdate(context)) {
                    skipped++;
                    observer.onSystemSkipped(system);
                    continue;
                }
            } catch (RuntimeException exception) {
                failures++;
                observer.onSystemFailure(system, "relevance", exception);
                continue;
            }

            long started = System.nanoTime();
            try {
                system.update(context);
                long elapsed = Math.max(0L, System.nanoTime() - started);
                observer.onSystemUpdated(system, elapsed);
                executed++;
            } catch (Exception exception) {
                failures++;
                observer.onSystemFailure(system, "update", exception);
            }
        }
        return new SystemDispatchReport(enabled, executed, skipped, failures);
    }

    /** Outcomes from one bounded coordinator pass. */
    public record ProcessingReport(
            int processedRegions,
            int missingDimensions,
            int systemUpdates,
            int systemFailures
    ) {
    }

    /** Outcomes from invoking systems for one regional context. */
    public record SystemDispatchReport(int enabled, int executed, int skipped, int failures) {
    }

    /** Receives low-allocation timing/failure observations from the engine. */
    public interface SystemObserver {
        default void onSystemUpdated(SimulationSystem system, long elapsedNanos) {
        }

        default void onSystemSkipped(SimulationSystem system) {
        }

        default void onSystemFailure(SimulationSystem system, String phase, Exception exception) {
        }

        default void onRegionProcessed(SimulationContext context, SystemDispatchReport report) {
        }
    }
}
