package com.thunder.wildernessodysseyapi.performance.background.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies safe fallback and normalization without requiring a loaded config file. */
class BackgroundEfficiencyConfigTest {

    @Test
    void nullValuesFallBackToConservativeDefaults() {
        BackgroundEfficiencyConfig.Values values = BackgroundEfficiencyConfig.sanitize(null);

        assertTrue(values.enabled());
        assertEquals(64, values.maximumTasksPerTick());
        assertTrue(values.asyncWorkerThreads() >= 1);
        assertTrue(values.maximumBackgroundTimeMillis() < 50.0D);
    }
}
