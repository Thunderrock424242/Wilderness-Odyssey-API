package com.thunder.wildernessodysseyapi.simulation.core;

import com.thunder.wildernessodysseyapi.simulation.api.SimulationSystem;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationRegionCollector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic registry for independently authoritative simulation participants. */
public final class SimulationRegistry {
    private final Map<ResourceLocation, SimulationSystem> byId = new HashMap<>();
    private List<SimulationSystem> ordered = List.of();

    /** Registers a system exactly once without coupling it to a central switch statement. */
    public synchronized void register(SimulationSystem system) {
        Objects.requireNonNull(system, "Simulation system is required");
        ResourceLocation id = Objects.requireNonNull(system.id(), "Simulation system ID is required");
        if (byId.putIfAbsent(id, system) != null) {
            throw new IllegalArgumentException("Simulation system is already registered: " + id);
        }
        List<SimulationSystem> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparing(candidate -> candidate.id().toString()));
        ordered = List.copyOf(sorted);
    }

    /** Returns systems in stable ID order. */
    public synchronized List<SimulationSystem> systems() {
        return ordered;
    }

    /** Counts systems whose current owner/config predicate is enabled. */
    public int enabledSystemCount() {
        int count = 0;
        for (SimulationSystem system : systems()) {
            try {
                if (system.isEnabled()) {
                    count++;
                }
            } catch (RuntimeException ignored) {
                // Update dispatch reports the broken predicate with its system ID.
            }
        }
        return count;
    }

    /** Returns whether any optional participant can currently consume regional work. */
    public boolean hasEnabledSystem() {
        return enabledSystemCount() > 0;
    }

    /** Collects owner-known regions in deterministic system order with failure isolation. */
    public void collectRegions(
            MinecraftServer server,
            SimulationRegionCollector collector,
            LifecycleFailureHandler failures
    ) {
        Objects.requireNonNull(server, "Minecraft server is required");
        Objects.requireNonNull(collector, "Simulation region collector is required");
        Objects.requireNonNull(failures, "Region collection failure handler is required");
        for (SimulationSystem system : systems()) {
            try {
                if (system.isEnabled()) {
                    system.collectRegions(server, collector);
                }
            } catch (RuntimeException exception) {
                failures.onFailure(system.id(), "region collection", exception);
            }
        }
    }

    /** Invokes start hooks in deterministic order with per-system failure isolation. */
    public void onServerStarted(MinecraftServer server, LifecycleFailureHandler failures) {
        Objects.requireNonNull(server, "Minecraft server is required");
        invokeLifecycle("server start", system -> system.onServerStarted(server), failures);
    }

    /** Invokes reload hooks without rebuilding or reordering registrations. */
    public void onConfigurationReload(LifecycleFailureHandler failures) {
        invokeLifecycle("configuration reload", SimulationSystem::onConfigurationReload, failures);
    }

    /** Invokes per-dimension cleanup hooks. */
    public void onLevelUnload(ResourceLocation dimension, LifecycleFailureHandler failures) {
        Objects.requireNonNull(dimension, "Dimension is required");
        invokeLifecycle("level unload", system -> system.onLevelUnload(dimension), failures);
    }

    /** Invokes final cleanup hooks for all registered definitions. */
    public void onServerStopping(LifecycleFailureHandler failures) {
        invokeLifecycle("server stop", SimulationSystem::onServerStopping, failures);
    }

    private void invokeLifecycle(
            String phase,
            LifecycleAction action,
            LifecycleFailureHandler failures
    ) {
        Objects.requireNonNull(failures, "Lifecycle failure handler is required");
        for (SimulationSystem system : systems()) {
            try {
                action.invoke(system);
            } catch (RuntimeException exception) {
                failures.onFailure(system.id(), phase, exception);
            }
        }
    }

    @FunctionalInterface
    private interface LifecycleAction {
        void invoke(SimulationSystem system);
    }

    /** Receives an isolated lifecycle failure with its system and phase. */
    @FunctionalInterface
    public interface LifecycleFailureHandler {
        void onFailure(ResourceLocation systemId, String phase, RuntimeException exception);
    }
}
