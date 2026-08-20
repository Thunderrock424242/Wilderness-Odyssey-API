package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers defensive normalization for ecosystem diagnostic counters. */
class EcosystemSimulationMetricsTest {

    @Test
    void clampsNegativeWildlifeScanMeasurements() {
        EcosystemSimulationMetrics.Snapshot snapshot = new EcosystemSimulationMetrics.Snapshot(
                -1L, -1, -1, -1, -1, -1, -1, -1, -1,
                -1L, -1, -1, -1L, -1L
        );

        assertEquals(EcosystemSimulationMetrics.Snapshot.EMPTY, snapshot);
    }

    @Test
    void originalConstructorRemainsAvailableToApiConsumers() {
        EcosystemSimulationMetrics.Snapshot snapshot = new EcosystemSimulationMetrics.Snapshot(
                1L, 2, 3, 4, 5, 6, 7, 8, 9, 10L
        );

        assertEquals(10L, snapshot.updateNanos());
        assertEquals(0, snapshot.scannedLoadedEntityCount());
    }
}
