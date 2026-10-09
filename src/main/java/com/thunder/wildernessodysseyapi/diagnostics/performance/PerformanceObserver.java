package com.thunder.wildernessodysseyapi.diagnostics.performance;

import java.util.Arrays;

/**
 * Server-thread-owned, allocation-free tick recording. Sorting happens only on
 * an explicit snapshot, never per tick. It observes and cannot schedule work.
 */
public final class PerformanceObserver {
    private final long[] ticks;
    private int cursor;
    private int count;
    private long totalTicks;
    private double ewmaMillis;
    private long lastTickNanos;
    private long heapPeakBytes = -1;
    private JvmPerformanceSample jvm = JvmPerformanceSample.unavailable();

    public PerformanceObserver(int windowTicks) {
        if (windowTicks < 1 || windowTicks > 12_000) throw new IllegalArgumentException("Invalid tick window");
        ticks = new long[windowTicks];
    }

    public void recordTick(long durationNanos) {
        if (durationNanos < 0) return;
        ticks[cursor] = durationNanos;
        cursor = (cursor + 1) % ticks.length;
        count = Math.min(ticks.length, count + 1);
        lastTickNanos = durationNanos;
        double millis = durationNanos / 1_000_000.0;
        ewmaMillis = totalTicks++ == 0 ? millis : ewmaMillis + 0.2 * (millis - ewmaMillis);
    }

    public void recordJvm(JvmPerformanceSample sample) {
        jvm = sample;
        heapPeakBytes = Math.max(heapPeakBytes, sample.heapUsedBytes());
    }

    public Snapshot snapshot() {
        long[] sorted = Arrays.copyOf(ticks, count);
        Arrays.sort(sorted);
        return new Snapshot(totalTicks, count, ewmaMillis, percentile(sorted, 0.50),
                percentile(sorted, 0.95), percentile(sorted, 0.99), lastTickNanos / 1_000_000.0,
                count == 0 ? 0 : sorted[count - 1] / 1_000_000.0, heapPeakBytes, jvm);
    }

    private static double percentile(long[] sorted, double percentile) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(sorted.length * percentile) - 1] / 1_000_000.0;
    }

    public record Snapshot(long totalTicks, int windowSamples, double ewmaMillis, double p50Millis,
                           double p95Millis, double p99Millis, double lastTickMillis, double windowMaximumMillis,
                           long heapPeakBytes, JvmPerformanceSample jvm) { }
}
