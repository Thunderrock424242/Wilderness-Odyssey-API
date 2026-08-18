package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies invalid hand-edited radius relationships recover deterministically. */
class EcosystemSimulationSettingsTest {

    @Test
    void normalizesAscendingRadiiAndPositiveBudgets() {
        EcosystemSimulationSettings settings = new EcosystemSimulationSettings(
                true, 64, 100, 80, 120, 0, 0, 0
        );

        assertEquals(100, settings.activeRadius());
        assertEquals(164, settings.nearRadius());
        assertEquals(228, settings.distantRadius());
        assertEquals(1, settings.regionalUpdateInterval());
        assertEquals(1, settings.maxRegionUpdatesPerTick());
        assertEquals(1, settings.entityTransitionRate());
    }
}
