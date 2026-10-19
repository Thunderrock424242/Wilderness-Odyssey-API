package com.thunder.wildernessodysseyapi.wage.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameTimeWindowTest {
    @Test
    void boundedWindowReportsNearestRankTailsAndCadenceFps() {
        FrameTimeWindow window = new FrameTimeWindow(100);
        for (int i = 1; i <= 200; i++) window.record(i * 1_000_000L);
        var stats = window.snapshot();
        assertEquals(100, stats.samples());
        assertEquals(150_500_000, stats.averageNanos());
        assertEquals(150_000_000, stats.medianNanos());
        assertEquals(195_000_000, stats.p95Nanos());
        assertEquals(199_000_000, stats.p99Nanos());
        assertEquals(1000.0 / 150.5, stats.framesPerSecond(), 0.0001);
    }

    @Test
    void ignoresInvalidSamplesAndClearsWorldHistory() {
        FrameTimeWindow window = new FrameTimeWindow(4);
        window.record(0);
        window.record(-1);
        assertEquals(0, window.snapshot().samples());
        window.record(8_000_000_000L);
        assertEquals(8_000_000_000L, window.snapshot().p99Nanos());
        window.clear();
        assertEquals(0, window.snapshot().samples());
        assertEquals(0, window.snapshot().framesPerSecond());
    }
}
