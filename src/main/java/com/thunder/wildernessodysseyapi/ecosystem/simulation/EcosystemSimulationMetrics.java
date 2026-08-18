package com.thunder.wildernessodysseyapi.ecosystem.simulation;

/** Immutable performance counters for one dimension's most recent ecosystem tick. */
public final class EcosystemSimulationMetrics {

    private EcosystemSimulationMetrics() {
    }

    /**
     * Cell, population, queue, and measured CPU-work counters.
     *
     * <p>{@code updateNanos} includes zone-manager work and ecosystem behavior
     * evaluations recorded during the same game tick; it does not claim to
     * measure vanilla entity ticking outside this subsystem.</p>
     */
    public record Snapshot(
            long tick,
            int activeCells,
            int nearCells,
            int distantCells,
            int dormantCells,
            int fullySimulatedEntityCount,
            int abstractPopulationCount,
            int regionUpdates,
            int pendingRegionalUpdates,
            long updateNanos
    ) {
        public static final Snapshot EMPTY = new Snapshot(0L, 0, 0, 0, 0, 0, 0, 0, 0, 0L);

        public Snapshot {
            tick = Math.max(0L, tick);
            activeCells = Math.max(0, activeCells);
            nearCells = Math.max(0, nearCells);
            distantCells = Math.max(0, distantCells);
            dormantCells = Math.max(0, dormantCells);
            fullySimulatedEntityCount = Math.max(0, fullySimulatedEntityCount);
            abstractPopulationCount = Math.max(0, abstractPopulationCount);
            regionUpdates = Math.max(0, regionUpdates);
            pendingRegionalUpdates = Math.max(0, pendingRegionalUpdates);
            updateNanos = Math.max(0L, updateNanos);
        }
    }
}
