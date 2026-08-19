package com.thunder.wildernessodysseyapi.performance.tickengine;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight WO subsystem profiler with rate-limited slow-system warnings.
 */
public final class TickEngineMetrics {
    private final ConcurrentHashMap<String, SubsystemTiming> timings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastWarningTicks = new ConcurrentHashMap<>();
    private final AtomicInteger deferredTasks = new AtomicInteger();
    private final AtomicInteger throttledSubsystems = new AtomicInteger();
    private volatile boolean profilingEnabled = true;
    private volatile double warningThresholdMillis = 5.0D;
    private volatile long warningIntervalTicks = 1200L;

    void reset() {
        timings.clear();
        lastWarningTicks.clear();
        deferredTasks.set(0);
        throttledSubsystems.set(0);
    }

    /** Applies profiler and warning settings without clearing accumulated evidence. */
    public void configure(boolean profilingEnabled, double warningThresholdMillis, long warningIntervalTicks) {
        this.profilingEnabled = profilingEnabled;
        this.warningThresholdMillis = Double.isFinite(warningThresholdMillis)
                ? Math.max(0.0D, warningThresholdMillis)
                : 5.0D;
        this.warningIntervalTicks = Math.max(1L, warningIntervalTicks);
    }

    /** Times one synchronous WO operation and rethrows its runtime failures. */
    public void time(String subsystem, Runnable work, long currentTick) {
        Objects.requireNonNull(work, "work");
        if (!profilingEnabled) {
            work.run();
            return;
        }
        long started = System.nanoTime();
        try {
            work.run();
        } finally {
            recordExecution(subsystem, System.nanoTime() - started, currentTick);
        }
    }

    /** Records scheduler-owned timing without nesting another timer. */
    public void recordExecution(String subsystem, long elapsedNanos, long currentTick) {
        if (!profilingEnabled) {
            return;
        }
        String normalized = normalize(subsystem);
        SubsystemTiming timing = timings.computeIfAbsent(normalized, ignored -> new SubsystemTiming());
        timing.recordExecution(elapsedNanos);
        maybeWarn(normalized, timing, currentTick);
    }

    public void recordDeferred(String subsystem) {
        timings.computeIfAbsent(normalize(subsystem), ignored -> new SubsystemTiming()).recordDeferred();
    }

    public void recordThrottled(String subsystem) {
        timings.computeIfAbsent(normalize(subsystem), ignored -> new SubsystemTiming()).recordThrottled();
    }

    public void recordFailed(String subsystem) {
        timings.computeIfAbsent(normalize(subsystem), ignored -> new SubsystemTiming()).recordFailed();
    }

    public void setDeferredTasks(int deferredTasks) {
        this.deferredTasks.set(Math.max(0, deferredTasks));
    }

    public void setThrottledSubsystems(int throttledSubsystems) {
        this.throttledSubsystems.set(Math.max(0, throttledSubsystems));
    }

    public int deferredTasks() {
        return deferredTasks.get();
    }

    public int throttledSubsystems() {
        return throttledSubsystems.get();
    }

    /** Builds immutable subsystem timing data for the debug API. */
    public Snapshot snapshot() {
        Map<String, SubsystemTiming.Snapshot> values = new HashMap<>();
        String worstSubsystem = "none";
        double worstAverage = 0.0D;
        for (Map.Entry<String, SubsystemTiming> entry : timings.entrySet()) {
            SubsystemTiming.Snapshot snapshot = entry.getValue().snapshot();
            values.put(entry.getKey(), snapshot);
            if (snapshot.averageMillis() > worstAverage) {
                worstAverage = snapshot.averageMillis();
                worstSubsystem = entry.getKey();
            }
        }
        return new Snapshot(deferredTasks.get(), throttledSubsystems.get(), worstSubsystem, Map.copyOf(values));
    }

    private void maybeWarn(String subsystem, SubsystemTiming timing, long currentTick) {
        long executionCount = timing.executionCount();
        if (executionCount < 20L || executionCount % 20L != 0L) {
            return;
        }
        double averageMillis = timing.averageMillis();
        if (averageMillis < warningThresholdMillis) {
            return;
        }
        AtomicLong lastWarning = lastWarningTicks.computeIfAbsent(subsystem, ignored -> new AtomicLong(Long.MIN_VALUE));
        long previous = lastWarning.get();
        if ((previous == Long.MIN_VALUE || currentTick - previous >= warningIntervalTicks)
                && lastWarning.compareAndSet(previous, currentTick)) {
            ModConstants.LOGGER.warn(
                    "[WO TickEngine] Subsystem '{}' averaged {} ms over {} executions.",
                    subsystem,
                    String.format(java.util.Locale.ROOT, "%.3f", averageMillis),
                    executionCount
            );
        }
    }

    private static String normalize(String subsystem) {
        String value = Objects.requireNonNullElse(subsystem, "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        return value.isEmpty() ? "unknown" : value;
    }

    /** Immutable profiler overview. */
    public record Snapshot(
            int deferredTasks,
            int throttledSubsystems,
            String worstSubsystem,
            Map<String, SubsystemTiming.Snapshot> subsystemTimings
    ) {
    }
}
