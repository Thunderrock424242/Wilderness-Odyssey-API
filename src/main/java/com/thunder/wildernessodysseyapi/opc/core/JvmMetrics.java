package com.thunder.wildernessodysseyapi.opc.core;

import java.lang.management.ManagementFactory;
import java.util.function.LongFunction;

/**
 * On-demand raw JVM observations shared by RAM advice and OPC diagnostics. Cached MXBeans and
 * a one-second raw sample avoid duplicate reads. Each consumer retains its own GC delta window;
 * GC notifications used for live-set recommendations are a separate measurement.
 */
public final class JvmMetrics {
    private static final JvmMetrics SHARED = new JvmMetrics(new Probe());
    private final LongFunction<Sample> probe;
    private Sample last;

    JvmMetrics(LongFunction<Sample> probe) { this.probe = probe; }
    public static JvmMetrics shared() { return SHARED; }

    public synchronized Sample sample(long now) {
        if (last == null || now < last.nanoTime() || now - last.nanoTime() >= 1_000_000_000L) last = probe.apply(now);
        return last;
    }

    public record Sample(long nanoTime, long timestampMillis, long heapUsedBytes, long heapCommittedBytes,
                         long heapMaximumBytes, long nonHeapUsedBytes, long systemTotalBytes, long systemFreeBytes,
                         int processors, long gcCollectionMillis, double processCpuLoad) { }

    private static final class Probe implements LongFunction<Sample> {
        private final java.lang.management.MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        private final java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        private final java.util.List<java.lang.management.GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();

        @Override public Sample apply(long now) {
            var heap = memory.getHeapMemoryUsage();
            var nonHeap = memory.getNonHeapMemoryUsage();
            long gc = 0;
            boolean supported = false;
            for (var collector : collectors) {
                long value = collector.getCollectionTime();
                if (value >= 0) { gc += value; supported = true; }
            }
            long total = -1;
            long free = -1;
            double cpu = -1;
            if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
                total = extended.getTotalMemorySize();
                free = extended.getFreeMemorySize();
                cpu = extended.getProcessCpuLoad();
            }
            if (!Double.isFinite(cpu) || cpu < 0) cpu = -1;
            return new Sample(now, System.currentTimeMillis(), heap.getUsed(), heap.getCommitted(), heap.getMax(),
                    nonHeap.getUsed(), total, free, Runtime.getRuntime().availableProcessors(), supported ? gc : -1, cpu);
        }
    }
}
