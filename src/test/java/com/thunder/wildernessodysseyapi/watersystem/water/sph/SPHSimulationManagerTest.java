package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SPHSimulationManagerTest {

    private final SPHSimulationManager manager = SPHSimulationManager.get();

    @AfterEach
    void resetSingletonManager() {
        manager.shutdown();
    }

    @Test
    void localVisualEffectDoesNotOwnCanonicalVolume() {
        SPHSimulator splash = manager.createLocalVisualEffect(
                0.5f,
                64.65f,
                0.5f,
                null,
                SPHConstants.SHORE_WAVE_PARTICLES,
                0.0f,
                0.0f,
                0.0f,
                SPHConstants.SHORE_WAVE_LIFETIME_TICKS
        );

        assertNotNull(splash);
        assertTrue(splash.isTransientSimulation());
        assertEquals(0, splash.getCanonicalVolumeUnits());
    }
}
