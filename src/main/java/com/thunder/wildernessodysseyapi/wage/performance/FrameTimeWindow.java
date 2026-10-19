package com.thunder.wildernessodysseyapi.wage.performance;

import java.util.Arrays;

/** Render-thread fixed-size observations. Sorting/formatting happens only on diagnostic requests. */
public final class FrameTimeWindow {
    private final long[] values;
    private int cursor;
    private int size;

    public FrameTimeWindow(int capacity) {
        if (capacity < 1 || capacity > 4096) throw new IllegalArgumentException("capacity must be 1..4096");
        values = new long[capacity];
    }

    /** Records elapsed time without allocations and without hiding long stalls by clamping them. */
    public void record(long nanos) {
        if (nanos <= 0) return;
        values[cursor] = nanos;
        cursor = (cursor + 1) % values.length;
        size = Math.min(size + 1, values.length);
    }

    public void clear() {
        cursor = 0;
        size = 0;
    }

    /** Nearest-rank percentiles over the retained observations, calculated on demand. */
    public Snapshot snapshot() {
        if (size == 0) return new Snapshot(0, 0, 0, 0, 0);
        long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        double total = 0;
        for (long value : sorted) total += value;
        return new Snapshot(size, Math.round(total / size), sorted[(size - 1) / 2],
                sorted[(int) Math.ceil(size * 0.95) - 1], sorted[(int) Math.ceil(size * 0.99) - 1]);
    }

    public record Snapshot(int samples, long averageNanos, long medianNanos, long p95Nanos, long p99Nanos) {
        /** This is FPS only when the observations are frame-start cadence intervals. */
        public double framesPerSecond() {
            return averageNanos > 0 ? 1_000_000_000.0 / averageNanos : 0;
        }
    }
}
