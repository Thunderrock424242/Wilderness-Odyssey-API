package com.thunder.wildernessodysseyapi.opc.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JvmMetricsTest {
    @Test
    void pressureSamplingNeverRequestsFullSystemMetrics() {
        AtomicInteger pressureReads = new AtomicInteger();
        JvmMetrics metrics = new JvmMetrics(now -> {
            throw new AssertionError("Render pressure must not read system-memory or process-CPU metrics");
        }, now -> {
            pressureReads.incrementAndGet();
            return sample(now, -1);
        });
        assertEquals(100, metrics.samplePressure(100).nanoTime());
        assertSame(metrics.samplePressure(100), metrics.samplePressure(500_000_000));
        assertEquals(1, pressureReads.get());
        assertEquals(1_000_000_100L, metrics.samplePressure(1_000_000_100L).nanoTime());
        assertEquals(2, pressureReads.get());
    }

    @Test
    void fullAndPressureConsumersHaveIndependentCachesAndClockRollbackInvalidatesPressure() {
        AtomicInteger fullReads = new AtomicInteger();
        AtomicInteger pressureReads = new AtomicInteger();
        JvmMetrics metrics = new JvmMetrics(now -> {
            fullReads.incrementAndGet();
            return sample(now, 0.5);
        }, now -> {
            pressureReads.incrementAndGet();
            return sample(now, -1);
        });
        assertEquals(0.5, metrics.sample(100).processCpuLoad());
        assertEquals(-1, metrics.samplePressure(100).processCpuLoad());
        metrics.samplePressure(99);
        assertEquals(1, fullReads.get());
        assertEquals(2, pressureReads.get());
        assertEquals(100, metrics.sample(500).nanoTime());
    }

    private static JvmMetrics.Sample sample(long now, double cpu) {
        return new JvmMetrics.Sample(now, 0, 100, 200, 400, 20, -1, -1, 4, 5, cpu);
    }
}
