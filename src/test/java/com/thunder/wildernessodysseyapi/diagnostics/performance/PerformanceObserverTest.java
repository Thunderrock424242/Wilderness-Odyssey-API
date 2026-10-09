package com.thunder.wildernessodysseyapi.diagnostics.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceObserverTest {
    @Test
    void computesExactNearestRankPercentilesWithoutSortingOnTick() {
        var observer = new PerformanceObserver(100);
        for (int millis = 1; millis <= 100; millis++) observer.recordTick(millis * 1_000_000L);
        var result = observer.snapshot();
        assertEquals(50.0, result.p50Millis());
        assertEquals(95.0, result.p95Millis());
        assertEquals(99.0, result.p99Millis());
        assertEquals(100, result.windowSamples());
        assertTrue(result.ewmaMillis() > 90 && result.ewmaMillis() < 100);
    }

    @Test
    void evictsOldSpikesFromTheRollingWindow() {
        var observer = new PerformanceObserver(4);
        observer.recordTick(900_000_000L);
        for (int count = 0; count < 4; count++) observer.recordTick(10_000_000L);
        assertEquals(10.0, observer.snapshot().p99Millis());
        assertEquals(5, observer.snapshot().totalTicks());
    }

    @Test
    void ignoresInvalidDurationsAndDoesNotInventUnsampledJvmValues() {
        var observer = new PerformanceObserver(4);
        observer.recordTick(-1);
        assertEquals(0, observer.snapshot().totalTicks());
        assertEquals(-1, observer.snapshot().jvm().gcCollectionMillis());
        assertEquals(-1, observer.snapshot().jvm().processCpuLoad());
    }

    @Test
    void keepsHeapPeakWhenUsageFalls() {
        var observer = new PerformanceObserver(4);
        observer.recordJvm(new JvmPerformanceSample(100, 1000, 2, 10, 0.2, 0.1));
        observer.recordJvm(new JvmPerformanceSample(40, 1000, 2, 12, 0.1, 0.1));
        assertEquals(100, observer.snapshot().heapPeakBytes());
        assertEquals(40, observer.snapshot().jvm().heapUsedBytes());
    }
}
