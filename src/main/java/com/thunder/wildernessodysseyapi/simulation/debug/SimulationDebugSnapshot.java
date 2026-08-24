package com.thunder.wildernessodysseyapi.simulation.debug;

import com.thunder.wildernessodysseyapi.performance.tickengine.TickPressure;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Immutable server-owned diagnostics for commands and integrated-server F3. */
public record SimulationDebugSnapshot(
        boolean running,
        int registeredSystems,
        int enabledSystems,
        int eventListeners,
        int pendingRegions,
        int trackedRegions,
        int activeRegions,
        int nearbyRegions,
        int backgroundRegions,
        int dormantRegions,
        long acceptedRequests,
        long coalescedRequests,
        long rejectedRequests,
        long processedRegions,
        long deferredPasses,
        long systemFailures,
        long eventsDispatched,
        long eventListenerFailures,
        long lastPassTick,
        long lastPassNanos,
        TickPressure pressure,
        Map<ResourceLocation, SystemSnapshot> systems
) {
    public SimulationDebugSnapshot {
        systems = Map.copyOf(systems);
    }

    /** Per-participant timings and failure totals. */
    public record SystemSnapshot(
            long updates,
            long skipped,
            long failures,
            long totalNanos,
            long lastNanos
    ) {
    }
}
