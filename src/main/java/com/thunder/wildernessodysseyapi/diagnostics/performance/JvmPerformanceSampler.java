package com.thunder.wildernessodysseyapi.diagnostics.performance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;

/** Cached MXBeans sampled infrequently. GC collection time is a pressure proxy, not exact STW pause time. */
final class JvmPerformanceSampler {
    private final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private final OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
    private long previousGcMillis = -1;
    private long previousNanos;

    JvmPerformanceSample sample(long nowNanos) {
        Runtime runtime = Runtime.getRuntime();
        long gcMillis = 0;
        boolean supported = false;
        for (GarbageCollectorMXBean collector : collectors) {
            long value = collector.getCollectionTime();
            if (value >= 0) {
                gcMillis += value;
                supported = true;
            }
        }
        if (!supported) gcMillis = -1;
        double gcFraction = previousGcMillis >= 0 && gcMillis >= previousGcMillis && nowNanos > previousNanos
                ? Math.min(1, (gcMillis - previousGcMillis) * 1_000_000.0 / (nowNanos - previousNanos)) : -1;
        previousGcMillis = gcMillis;
        previousNanos = nowNanos;
        double cpu = operatingSystem instanceof com.sun.management.OperatingSystemMXBean bean
                ? bean.getProcessCpuLoad() : -1;
        if (!Double.isFinite(cpu) || cpu < 0) cpu = -1;
        return new JvmPerformanceSample(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory(),
                runtime.availableProcessors(), gcMillis, cpu, gcFraction);
    }
}
