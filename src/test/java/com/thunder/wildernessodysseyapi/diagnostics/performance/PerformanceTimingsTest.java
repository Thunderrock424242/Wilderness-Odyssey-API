package com.thunder.wildernessodysseyapi.diagnostics.performance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceTimingsTest {
    @Test
    void boundsPhaseKeysAndStillUpdatesAlreadyKnownPhases() {
        var timings = new PerformanceTimings(2, 2, 50_000_000L, 1000L);
        timings.recordPhase("save/overworld", 10_000_000L, true);
        timings.recordPhase("save/nether", 20_000_000L, false);
        timings.recordPhase("extra", 1, true);
        assertEquals(1, timings.snapshot().omittedPhaseMeasurements());
        timings.recordPhase("save/overworld", 30_000_000L, true);
        var phases = timings.snapshot().phases();
        assertEquals(2, phases.size());
        assertEquals(2, phases.get("save/overworld").calls());
        assertEquals(40_000_000L, phases.get("save/overworld").totalNanos());
        assertEquals(1, phases.get("save/nether").failures());
    }

    @Test
    void rateLimitsStackCaptureBeforeInvokingTheSupplierAndBoundsWaitHistory() {
        var timings = new PerformanceTimings(2, 2, 50_000_000L, 1000L);
        int[] captures = {0};
        java.util.function.Supplier<List<String>> stack = () -> { captures[0]++; return List.of("caller"); };
        assertFalse(timings.recordChunkWait("overworld", 1, 2, 10_000_000L, "server", 0, stack));
        assertTrue(timings.recordChunkWait("overworld", 1, 2, 70_000_000L, "server", 0, stack));
        assertFalse(timings.recordChunkWait("nether", 1, 2, 80_000_000L, "server", 500, stack));
        assertTrue(timings.recordChunkWait("nether", 1, 2, 80_000_000L, "server", 1000, stack));
        assertTrue(timings.recordChunkWait("end", 3, 4, 90_000_000L, "server", 2000, stack));
        assertEquals(3, captures[0]);
        assertEquals(2, timings.snapshot().waits().size());
        assertEquals("nether", timings.snapshot().waits().getFirst().dimension());
        assertEquals(4, timings.snapshot().slowChunkWaitSpans());
    }

    @Test
    void aBrokenOptionalStackSamplerDoesNotEraseWaitEvidence() {
        var timings = new PerformanceTimings(2, 2, 1, 1);
        assertDoesNotThrow(() -> timings.recordChunkWait("overworld", 0, 0, 2, "worker", 0,
                () -> { throw new IllegalStateException("unsupported"); }));
        assertEquals(1, timings.snapshot().waits().size());
        assertTrue(timings.snapshot().waits().getFirst().stack().isEmpty());
    }
}
