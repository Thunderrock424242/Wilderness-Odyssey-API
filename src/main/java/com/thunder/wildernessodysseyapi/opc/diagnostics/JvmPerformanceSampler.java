package com.thunder.wildernessodysseyapi.opc.diagnostics;

import com.thunder.wildernessodysseyapi.opc.core.JvmMetrics;

/** Cached MXBeans sampled infrequently. GC collection time is a pressure proxy, not exact STW pause time. */
final class JvmPerformanceSampler {
    private long previousGcMillis = -1;
    private long previousNanos;

    JvmPerformanceSample sample(long nowNanos) {
        var raw = JvmMetrics.shared().sample(nowNanos);
        long gcMillis = raw.gcCollectionMillis();
        double gcFraction = previousGcMillis >= 0 && gcMillis >= previousGcMillis && raw.nanoTime() > previousNanos
                ? Math.min(1, (gcMillis - previousGcMillis) * 1_000_000.0 / (raw.nanoTime() - previousNanos)) : -1;
        previousGcMillis = gcMillis;
        previousNanos = raw.nanoTime();
        return new JvmPerformanceSample(raw.heapUsedBytes(), raw.heapMaximumBytes(),
                raw.processors(), gcMillis, raw.processCpuLoad(), gcFraction);
    }
}
