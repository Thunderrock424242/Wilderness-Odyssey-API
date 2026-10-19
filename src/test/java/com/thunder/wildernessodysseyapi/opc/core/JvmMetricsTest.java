package com.thunder.wildernessodysseyapi.opc.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JvmMetricsTest {
    @Test
    void consumersShareRawSamplesWithoutChangingTheirCaptureTimestamp() {
        AtomicInteger reads = new AtomicInteger();
        JvmMetrics metrics = new JvmMetrics(now -> {
            reads.incrementAndGet();
            return new JvmMetrics.Sample(now, 123, 1, 2, 3, 4, 5, 6, 8, 9, .25);
        });
        var ram = metrics.sample(1);
        var diagnostics = metrics.sample(500_000_000);
        assertSame(ram, diagnostics);
        assertEquals(1, diagnostics.nanoTime());
        assertEquals(1, reads.get());
        assertNotSame(ram, metrics.sample(1_000_000_001));
        assertEquals(2, reads.get());
    }
}
