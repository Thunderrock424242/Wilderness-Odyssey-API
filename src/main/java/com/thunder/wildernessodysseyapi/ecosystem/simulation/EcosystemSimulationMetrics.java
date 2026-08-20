package com.thunder.wildernessodysseyapi.ecosystem.simulation;

/** Immutable performance counters for one dimension's most recent ecosystem tick. */
public final class EcosystemSimulationMetrics {

    private EcosystemSimulationMetrics() {
    }

    /**
     * Cell, population, queue, entity-scan, and measured CPU-work counters.
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
            long wildlifeScanTick,
            int scannedLoadedEntityCount,
            int profiledWildlifeCount,
            long wildlifeScanNanos,
            long updateNanos
    ) {
        public static final Snapshot EMPTY = new Snapshot(
                0L, 0, 0, 0, 0, 0, 0, 0, 0,
                0L, 0, 0, 0L, 0L
        );

        /** Preserves the original diagnostics constructor for API consumers. */
        public Snapshot(
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
            this(
                    tick, activeCells, nearCells, distantCells, dormantCells,
                    fullySimulatedEntityCount, abstractPopulationCount,
                    regionUpdates, pendingRegionalUpdates,
                    0L, 0, 0, 0L, updateNanos
            );
        }

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
            wildlifeScanTick = Math.max(0L, wildlifeScanTick);
            scannedLoadedEntityCount = Math.max(0, scannedLoadedEntityCount);
            profiledWildlifeCount = Math.max(0, profiledWildlifeCount);
            wildlifeScanNanos = Math.max(0L, wildlifeScanNanos);
            updateNanos = Math.max(0L, updateNanos);
        }
    }
}
