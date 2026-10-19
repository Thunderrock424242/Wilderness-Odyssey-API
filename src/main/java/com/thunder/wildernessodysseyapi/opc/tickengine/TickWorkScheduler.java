package com.thunder.wildernessodysseyapi.opc.tickengine;

import com.thunder.wildernessodysseyapi.opc.core.DeferredWorkScheduler;
import com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Public tick-aware compatibility API backed by OPC's single deferred queue implementation. */
public final class TickWorkScheduler {
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private final DeferredWorkScheduler.Lane lane;

    public TickWorkScheduler(TickEngineMetrics metrics) { this(metrics, new DeferredWorkScheduler()); }

    public TickWorkScheduler(TickEngineMetrics metrics, DeferredWorkScheduler scheduler) {
        Objects.requireNonNull(metrics, "metrics");
        lane = scheduler.lane(new DeferredWorkScheduler.Listener() {
            public void executed(String subsystem, long nanos, long tick) { metrics.recordExecution(subsystem, nanos, tick); }
            public void deferred(String subsystem) { metrics.recordDeferred(subsystem); }
            public void throttled(String subsystem) { metrics.recordThrottled(subsystem); }
            public void failed(String subsystem) { metrics.recordFailed(subsystem); }
            public void queued(int count) { metrics.setDeferredTasks(count); }
        }, TickWorkScheduler::permitted, true);
        configure(Settings.defaults());
    }

    public void configure(Settings settings) {
        Objects.requireNonNull(settings);
        lane.configure(new DeferredWorkScheduler.Limits(settings.enabled(), settings.maximumTasksPerTick(),
                settings.maximumQueuedTasks(), settings.maximumTasksPerSubsystem()));
    }

    public boolean submit(TickTask task) { return lane.submit(task); }
    public ProcessingReport process(long tick, long deadline, TickPressure pressure) {
        return process(tick, deadline, pressure, ALWAYS_HAS_TIME);
    }
    ProcessingReport process(long tick, long deadline, TickPressure pressure, BooleanSupplier hasTime) {
        var report = lane.process(tick, deadline, pressure, hasTime, true);
        return new ProcessingReport(report.processed(), report.deferred(), report.stale(), report.remaining());
    }
    void processTick(long tick, long deadline, TickPressure pressure, BooleanSupplier hasTime) {
        lane.process(tick, deadline, pressure, hasTime, false);
    }
    public void clear() { lane.clear(); }
    public int queuedTasks() { return lane.size(); }
    public double queuePressure() { return lane.pressure(); }

    private static boolean permitted(TickPriority priority, TickPressure pressure, long tick) {
        if ((priority == TickPriority.BACKGROUND || priority == TickPriority.IDLE) && !OdysseyPerformanceCore.backgroundAllowed()) return false;
        return switch (pressure) {
            case RELAXED -> true;
            case BUSY -> priority != TickPriority.IDLE || Math.floorMod(tick, 4) == 0;
            case HIGH -> priority != TickPriority.IDLE;
            case CRITICAL -> priority != TickPriority.BACKGROUND && priority != TickPriority.IDLE;
            case OVERLOADED -> priority == TickPriority.CRITICAL || priority == TickPriority.GAMEPLAY;
        };
    }

    public record Settings(boolean enabled, int maximumTasksPerTick, int maximumQueuedTasks, int maximumTasksPerSubsystem) {
        public static Settings defaults() { return new Settings(true, 64, 2048, 256); }
    }
    public record ProcessingReport(int processed, int deferred, int stale, int remaining) { }
}