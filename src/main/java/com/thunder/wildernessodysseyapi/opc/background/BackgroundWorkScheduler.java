package com.thunder.wildernessodysseyapi.opc.background;

import com.thunder.wildernessodysseyapi.opc.core.DeferredWorkScheduler;
import com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore;
import com.thunder.wildernessodysseyapi.opc.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.opc.tickengine.TickPriority;
import com.thunder.wildernessodysseyapi.opc.tickengine.TickTask;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Compatibility lane over OPC's deferred scheduler; it owns no independent processing algorithm. */
public final class BackgroundWorkScheduler implements BackgroundSchedulerControl {
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private final BackgroundMetrics metrics;
    private final DeferredWorkScheduler.Lane lane;
    private final NanoClock clock;
    private volatile Settings settings = Settings.defaults();
    private volatile BackgroundBudgetControl externalControl = BackgroundBudgetControl.UNRESTRICTED;

    public BackgroundWorkScheduler(BackgroundMetrics metrics) { this(metrics, System::nanoTime); }
    BackgroundWorkScheduler(BackgroundMetrics metrics, NanoClock clock) {
        this(metrics, new DeferredWorkScheduler(clock::nanoTime), clock);
    }
    public BackgroundWorkScheduler(BackgroundMetrics metrics, DeferredWorkScheduler scheduler) {
        this(metrics, scheduler, System::nanoTime);
    }
    private BackgroundWorkScheduler(BackgroundMetrics metrics, DeferredWorkScheduler scheduler, NanoClock clock) {
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        lane = scheduler.lane(new DeferredWorkScheduler.Listener() {
            public void executed(String subsystem, long nanos, long tick) { metrics.recordProcessed(subsystem, nanos); }
            public void deferred(String subsystem) { metrics.recordDeferred(subsystem); }
            public void throttled(String subsystem) { }
            public void failed(String subsystem) { metrics.recordFailed(subsystem); }
            public void queued(int count) { metrics.setQueuedTasks(count); }
        }, this::permitted, false);
        configure(settings);
    }
    public void configure(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
        lane.configure(new DeferredWorkScheduler.Limits(settings.enabled(), settings.maximumTasksPerPass(),
                settings.maximumQueuedTasks(), settings.maximumTasksPerSubsystem()));
    }
    public boolean submit(BackgroundTask task) {
        Objects.requireNonNull(task);
        boolean accepted = lane.submit(new TickTask(task.subsystem(), TickPriority.valueOf(task.priority().name()),
                task.creationTick(), 0, "", task.context(), () -> task.work().run() == BackgroundTask.Result.DEFER
                ? TickTask.Result.DEFER : TickTask.Result.COMPLETE));
        if (!accepted) metrics.recordRejected(task.subsystem());
        return accepted;
    }
    public ProcessingReport process(long tick, long budget) { return process(tick, budget, ALWAYS_HAS_TIME); }
    ProcessingReport process(long tick, long budget, BooleanSupplier hasTime) {
        var report = processInternal(tick, budget, hasTime, true);
        return new ProcessingReport(report.processed(), report.deferred(), report.remaining(), report.elapsedNanos());
    }
    void processTick(long tick, long budget, BooleanSupplier hasTime) { processInternal(tick, budget, hasTime, false); }
    private DeferredWorkScheduler.Report processInternal(long tick, long callerBudget, BooleanSupplier hasTime, boolean report) {
        BackgroundBudgetControl control = externalControl;
        long local = (long) (Math.max(0, settings.maximumTimeNanos()) * control.budgetMultiplier());
        long budget = Math.max(0, Math.min(callerBudget, Math.min(local, control.maximumBudgetNanos())));
        long now = clock.nanoTime();
        long deadline = now > Long.MAX_VALUE - budget ? Long.MAX_VALUE : now + budget;
        return lane.process(tick, deadline, TickPressure.RELAXED, hasTime, report);
    }
    public void clear() { lane.clear(); externalControl = BackgroundBudgetControl.UNRESTRICTED; }
    @Override public void setExternalControl(BackgroundBudgetControl control) { externalControl = Objects.requireNonNullElse(control, BackgroundBudgetControl.UNRESTRICTED); }
    @Override public double queuePressure() { return lane.pressure(); }
    @Override public int queuedTasks() { return lane.size(); }
    private boolean permitted(TickPriority priority, TickPressure pressure, long tick) {
        if (priority == TickPriority.BACKGROUND) return externalControl.backgroundAllowed() && OdysseyPerformanceCore.backgroundAllowed();
        return priority != TickPriority.IDLE || externalControl.idleAllowed() && OdysseyPerformanceCore.backgroundAllowed();
    }
    public record Settings(boolean enabled, int maximumTasksPerPass, long maximumTimeNanos, int maximumQueuedTasks, int maximumTasksPerSubsystem) {
        public static Settings defaults() { return new Settings(true, 64, TimeUnit.MILLISECONDS.toNanos(2), 2048, 256); }
    }
    public record ProcessingReport(int processed, int deferred, int remaining, long elapsedNanos) { }
    @FunctionalInterface interface NanoClock { long nanoTime(); }
}