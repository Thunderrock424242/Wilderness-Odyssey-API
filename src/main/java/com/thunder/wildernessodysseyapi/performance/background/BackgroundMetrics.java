package com.thunder.wildernessodysseyapi.performance.background;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Aggregates cheap counters for the background framework and future debug UI.
 *
 * <p>Hot paths update adders and atomic gauges. Immutable maps are allocated
 * only when a consumer explicitly requests a snapshot.</p>
 */
public final class BackgroundMetrics {
    private final LongAdder processedTasks = new LongAdder();
    private final LongAdder deferredTasks = new LongAdder();
    private final LongAdder failedTasks = new LongAdder();
    private final LongAdder rejectedTasks = new LongAdder();
    private final LongAdder completedAsyncJobs = new LongAdder();
    private final LongAdder failedAsyncJobs = new LongAdder();
    private final LongAdder rejectedAsyncJobs = new LongAdder();
    private final AtomicInteger queuedTasks = new AtomicInteger();
    private final AtomicInteger activeAsyncJobs = new AtomicInteger();
    private final AtomicInteger queuedAsyncJobs = new AtomicInteger();
    private final AtomicInteger queuedNetworkUpdates = new AtomicInteger();
    private final AtomicInteger queuedAnalyticsEvents = new AtomicInteger();
    private final ConcurrentHashMap<String, SubsystemCounters> subsystemCounters = new ConcurrentHashMap<>();
    private final EnumMap<ActivityLevel, AtomicLong> activityCounts = new EnumMap<>(ActivityLevel.class);

    public BackgroundMetrics() {
        for (ActivityLevel level : ActivityLevel.values()) {
            activityCounts.put(level, new AtomicLong());
        }
    }

    void reset() {
        processedTasks.reset();
        deferredTasks.reset();
        failedTasks.reset();
        rejectedTasks.reset();
        completedAsyncJobs.reset();
        failedAsyncJobs.reset();
        rejectedAsyncJobs.reset();
        queuedTasks.set(0);
        activeAsyncJobs.set(0);
        queuedAsyncJobs.set(0);
        queuedNetworkUpdates.set(0);
        queuedAnalyticsEvents.set(0);
        subsystemCounters.clear();
        activityCounts.values().forEach(count -> count.set(0L));
    }

    void setQueuedTasks(int value) {
        queuedTasks.set(Math.max(0, value));
    }

    void recordProcessed(String subsystem, long elapsedNanos) {
        processedTasks.increment();
        SubsystemCounters counters = subsystemCounters.computeIfAbsent(subsystem, ignored -> new SubsystemCounters());
        counters.processed.increment();
        counters.totalNanos.add(Math.max(0L, elapsedNanos));
        counters.recentNanos.set(Math.max(0L, elapsedNanos));
        counters.maximumNanos.accumulateAndGet(Math.max(0L, elapsedNanos), Math::max);
    }

    void recordDeferred(String subsystem) {
        deferredTasks.increment();
        subsystemCounters.computeIfAbsent(subsystem, ignored -> new SubsystemCounters()).deferred.increment();
    }

    void recordFailed(String subsystem) {
        failedTasks.increment();
        subsystemCounters.computeIfAbsent(subsystem, ignored -> new SubsystemCounters()).failed.increment();
    }

    void recordRejected(String subsystem) {
        rejectedTasks.increment();
        subsystemCounters.computeIfAbsent(subsystem, ignored -> new SubsystemCounters()).rejected.increment();
    }

    void setAsyncState(int active, int queued) {
        activeAsyncJobs.set(Math.max(0, active));
        queuedAsyncJobs.set(Math.max(0, queued));
    }

    void recordAsyncCompleted() {
        completedAsyncJobs.increment();
    }

    void recordAsyncFailed() {
        failedAsyncJobs.increment();
    }

    void recordAsyncRejected() {
        rejectedAsyncJobs.increment();
    }

    void setQueuedNetworkUpdates(int value) {
        queuedNetworkUpdates.set(Math.max(0, value));
    }

    void setQueuedAnalyticsEvents(int value) {
        queuedAnalyticsEvents.set(Math.max(0, value));
    }

    /** Sets a caller-owned activity gauge rather than incrementing a lifetime event counter. */
    public void setActivityCount(ActivityLevel level, long count) {
        activityCounts.get(level).set(Math.max(0L, count));
    }

    /** Builds an immutable read-only view for diagnostics. */
    public Snapshot snapshot() {
        Map<String, SubsystemSnapshot> subsystems = new java.util.HashMap<>();
        subsystemCounters.forEach((name, counters) -> subsystems.put(name, counters.snapshot()));
        EnumMap<ActivityLevel, Long> activities = new EnumMap<>(ActivityLevel.class);
        activityCounts.forEach((level, count) -> activities.put(level, count.get()));
        return new Snapshot(
                queuedTasks.get(),
                processedTasks.sum(),
                deferredTasks.sum(),
                failedTasks.sum(),
                rejectedTasks.sum(),
                activeAsyncJobs.get(),
                queuedAsyncJobs.get(),
                completedAsyncJobs.sum(),
                failedAsyncJobs.sum(),
                rejectedAsyncJobs.sum(),
                queuedNetworkUpdates.get(),
                queuedAnalyticsEvents.get(),
                Map.copyOf(activities),
                Map.copyOf(subsystems)
        );
    }

    /** Immutable metrics payload suitable for debug presentation. */
    public record Snapshot(
            int queuedTasks,
            long processedTasks,
            long deferredTasks,
            long failedTasks,
            long rejectedTasks,
            int activeAsyncJobs,
            int queuedAsyncJobs,
            long completedAsyncJobs,
            long failedAsyncJobs,
            long rejectedAsyncJobs,
            int queuedNetworkUpdates,
            int queuedAnalyticsEvents,
            Map<ActivityLevel, Long> activityCounts,
            Map<String, SubsystemSnapshot> subsystems
    ) {
    }

    /** Aggregated timing and queue outcomes for one subsystem. */
    public record SubsystemSnapshot(
            long processed,
            long deferred,
            long failed,
            long rejected,
            long totalNanos,
            long recentNanos,
            long maximumNanos
    ) {
        public double averageMillis() {
            return processed == 0L ? 0.0D : totalNanos / 1_000_000.0D / processed;
        }
    }

    private static final class SubsystemCounters {
        private final LongAdder processed = new LongAdder();
        private final LongAdder deferred = new LongAdder();
        private final LongAdder failed = new LongAdder();
        private final LongAdder rejected = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong recentNanos = new AtomicLong();
        private final AtomicLong maximumNanos = new AtomicLong();

        private SubsystemSnapshot snapshot() {
            return new SubsystemSnapshot(
                    processed.sum(), deferred.sum(), failed.sum(), rejected.sum(),
                    totalNanos.sum(), recentNanos.get(), maximumNanos.get()
            );
        }
    }
}
