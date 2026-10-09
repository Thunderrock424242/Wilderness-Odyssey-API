package com.thunder.wildernessodysseyapi.diagnostics.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceDiagnosticsConfigTest {
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
