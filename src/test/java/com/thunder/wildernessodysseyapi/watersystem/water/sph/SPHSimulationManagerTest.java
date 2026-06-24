package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SPHSimulationManagerTest {

    private final SPHSimulationManager manager = SPHSimulationManager.get();

    @AfterEach
    void resetSingletonManager() {
        manager.shutdown();
    }

    @Test
    void overloadedBucketKeepsCanonicalOwnershipWhenNoBodyCanAcceptParticles() {
        manager.shutdown();
        for (int index = 0; index < SPHConstants.MAX_ACTIVE_SIMULATIONS; index++) {
            manager.createSimulation(
                    index * 32.0f,
                    64.0f,
                    0.0f,
                    null,
                    pos -> { },
                    SPHConstants.MAX_PARTICLES,
                    0.0f,
                    0.0f,
                    0.0f
            );
        }

        SPHSimulationManager.BucketPlacementResult result = manager.createBucketSimulation(
                10_000.0f,
                64.0f,
                0.0f,
                null,
                pos -> { }
        );

        assertFalse(result.sphOwnsVolume());
    }
}
