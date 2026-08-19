package com.thunder.wildernessodysseyapi.performance.tickengine;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Cheap lock-free aggregate timing for one Wilderness Odyssey subsystem. */
public final class SubsystemTiming {
    private final LongAdder totalNanos = new LongAdder();
    private final LongAdder executionCount = new LongAdder();
    private final LongAdder deferredCount = new LongAdder();
    private final LongAdder throttledCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();
    private final AtomicLong recentNanos = new AtomicLong();
    private final AtomicLong maximumNanos = new AtomicLong();

    /** Records one completed timed section. */
    public void recordExecution(long elapsedNanos) {
        long bounded = Math.max(0L, elapsedNanos);
        totalNanos.add(bounded);
        executionCount.increment();
        recentNanos.set(bounded);
        maximumNanos.accumulateAndGet(bounded, Math::max);
    }

    public void recordDeferred() {
        deferredCount.increment();
    }

    public void recordThrottled() {
        throttledCount.increment();
    }

    public void recordFailed() {
        failedCount.increment();
    }

    long executionCount() {
        return executionCount.sum();
    }

    double averageMillis() {
        long count = executionCount.sum();
        return count == 0L ? 0.0D : totalNanos.sum() / 1_000_000.0D / count;
    }

    /** Builds immutable presentation data outside the hot timing path. */
    public Snapshot snapshot() {
        return new Snapshot(
                totalNanos.sum(),
                executionCount.sum(),
                recentNanos.get(),
                maximumNanos.get(),
                deferredCount.sum(),
                throttledCount.sum(),
                failedCount.sum()
        );
    }

    /** Immutable subsystem profiler values. */
    public record Snapshot(
            long totalNanos,
            long executionCount,
            long recentNanos,
            long maximumNanos,
            long deferredCount,
            long throttledCount,
            long failedCount
    ) {
        public double averageMillis() {
            return executionCount == 0L ? 0.0D : totalNanos / 1_000_000.0D / executionCount;
        }

        public double recentMillis() {
            return recentNanos / 1_000_000.0D;
        }

        public double maximumMillis() {
            return maximumNanos / 1_000_000.0D;
        }
    }
}
