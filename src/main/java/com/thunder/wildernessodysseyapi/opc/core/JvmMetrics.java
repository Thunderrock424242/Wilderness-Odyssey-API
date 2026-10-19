package com.thunder.wildernessodysseyapi.opc.core;

import java.lang.management.ManagementFactory;
import java.util.function.LongFunction;

/**
 * On-demand raw JVM observations shared by RAM advice and OPC diagnostics. Cached MXBeans and
 * one-second full/pressure caches avoid repeated reads without serializing render pressure
 * behind system diagnostics. Each consumer retains its own GC delta window;
 * GC notifications used for live-set recommendations are a separate measurement.
 */
public final class JvmMetrics {
    private static final JvmMetrics SHARED = new JvmMetrics(new Probe());
    private final LongFunction<Sample> probe;
    private final LongFunction<Sample> pressureProbe;
    private final Object pressureLock = new Object();
    private Sample last;
    private Sample lastPressure;

    private JvmMetrics(Probe probe) { this(probe, probe::pressure); }
    JvmMetrics(LongFunction<Sample> probe) { this(probe, probe); }
    JvmMetrics(LongFunction<Sample> probe, LongFunction<Sample> pressureProbe) {
        this.probe = probe;
        this.pressureProbe = pressureProbe;
    }
    public static JvmMetrics shared() { return SHARED; }

    public synchronized Sample sample(long now) {
        if (last == null || now < last.nanoTime() || now - last.nanoTime() >= 1_000_000_000L) last = probe.apply(now);
        return last;
    }

    /**
     * Heap/GC observations for render-thread policy. Never requests system memory
     * or process CPU counters, which can stall on Windows. A separate cache/lock
     * also prevents a full diagnostic read from blocking this pressure consumer.
     * Fields outside heap/GC pressure are unknown in this sample.
     */
    public Sample samplePressure(long now) {
        synchronized (pressureLock) {
            if (lastPressure == null || now < lastPressure.nanoTime()
                    || now - lastPressure.nanoTime() >= 1_000_000_000L) lastPressure = pressureProbe.apply(now);
            return lastPressure;
        }
    }

    public record Sample(long nanoTime, long timestampMillis, long heapUsedBytes, long heapCommittedBytes,
                         long heapMaximumBytes, long nonHeapUsedBytes, long systemTotalBytes, long systemFreeBytes,
                         int processors, long gcCollectionMillis, double processCpuLoad) { }

    private static final class Probe implements LongFunction<Sample> {
        private final java.lang.management.MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        private java.lang.management.OperatingSystemMXBean os;
        private final java.util.List<java.lang.management.GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();

        Sample pressure(long now) {
            var heap = memory.getHeapMemoryUsage();
            long gc = 0;
            boolean supported = false;
            for (var collector : collectors) {
                long value = collector.getCollectionTime();
                if (value >= 0) { gc += value; supported = true; }
            }
            return new Sample(now, System.currentTimeMillis(), heap.getUsed(), heap.getCommitted(), heap.getMax(),
                    -1, -1, -1, -1, supported ? gc : -1, -1);
        }

        @Override public Sample apply(long now) {
            Sample pressure = pressure(now);
            var nonHeap = memory.getNonHeapMemoryUsage();
            if (os == null) os = ManagementFactory.getOperatingSystemMXBean();
            long total = -1;
            long free = -1;
            double cpu = -1;
            if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
                total = extended.getTotalMemorySize();
                free = extended.getFreeMemorySize();
                cpu = extended.getProcessCpuLoad();
            }
            if (!Double.isFinite(cpu) || cpu < 0) cpu = -1;
            return new Sample(now, pressure.timestampMillis(), pressure.heapUsedBytes(), pressure.heapCommittedBytes(), pressure.heapMaximumBytes(),
                    nonHeap.getUsed(), total, free, Runtime.getRuntime().availableProcessors(), pressure.gcCollectionMillis(), cpu);
        }
    }
}
