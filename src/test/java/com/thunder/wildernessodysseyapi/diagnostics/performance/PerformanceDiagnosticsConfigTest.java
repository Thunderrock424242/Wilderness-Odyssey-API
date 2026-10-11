package com.thunder.wildernessodysseyapi.diagnostics.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceDiagnosticsConfigTest {
    @Test
    void entireCoordinatorHasAnExplicitDisabledDefault() {
        com.thunder.wildernessodysseyapi.config.PerformanceServerConfig.initialize();
        net.neoforged.neoforge.common.ModConfigSpec.BooleanValue master =
                com.thunder.wildernessodysseyapi.config.PerformanceServerConfig.CONFIG_SPEC.getValues()
                        .get("performance.coordinator.enabled");
        assertNotNull(master, "The whole coordinator needs its own opt-in gate");
        assertFalse(master.getDefault());
    }

    @Test
    void defaultsObserveConservativelyWithoutRecordingOrStacks() {
        var values = PerformanceDiagnosticsConfig.values();
        assertTrue(values.safeMode());
        assertEquals(PerformanceDiagnosticsConfig.Mode.AUTO, values.observation());
        assertFalse(values.recording());
        assertFalse(values.chunkWaitStacks());
        assertEquals(100, values.sampleIntervalTicks());
        assertEquals(600, values.windowTicks());
        assertEquals(50.0, values.chunkWaitThresholdMillis());
    }
}
