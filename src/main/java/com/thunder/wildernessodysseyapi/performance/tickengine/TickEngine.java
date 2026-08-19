package com.thunder.wildernessodysseyapi.performance.tickengine;

import com.thunder.wildernessodysseyapi.performance.background.BackgroundBudgetControl;
import com.thunder.wildernessodysseyapi.performance.background.BackgroundEfficiencyManager;
import com.thunder.wildernessodysseyapi.performance.background.BackgroundSchedulerControl;
import com.thunder.wildernessodysseyapi.performance.tickengine.config.TickEngineConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Additive server-load governor for opt-in Wilderness Odyssey work.
 *
 * <p>The engine observes NeoForge's server tick events, budgets only work that
 * Wilderness Odyssey explicitly submits, and exposes interval helpers to WO
 * systems. It never replaces Minecraft's tick loop or modifies arbitrary
 * entities, block entities, chunks, random ticks, redstone, or other mods.</p>
 */
public final class TickEngine {
    private static final TickEngine INSTANCE = new TickEngine();

    private final TickMonitor monitor = new TickMonitor(TickMonitor.Thresholds.defaults());
    private final TickBudgetManager budgetManager = new TickBudgetManager();
    private final TickEngineMetrics metrics = new TickEngineMetrics();
    private final TickWorkScheduler scheduler = new TickWorkScheduler(metrics);
    private final AdaptiveThrottle throttle = new AdaptiveThrottle();
    private final TickDebtManager debtManager = new TickDebtManager();

    private volatile TickEngineConfig.Values values = TickEngineConfig.defaults();
    private volatile BackgroundSchedulerControl backgroundControl;
    private volatile boolean running;
    private volatile long tickStartNanos;
    private volatile double recoveryMultiplier = 1.0D;
    private TickPressure lastBackgroundPressure;
    private double lastBackgroundMultiplier = Double.NaN;
    private boolean lastBackgroundAllowed;
    private boolean lastIdleAllowed;

    private TickEngine() {
        registerDefaultSubsystems();
        scheduler.configure(new TickWorkScheduler.Settings(false, 1, 1, 1));
    }

    /** Starts a fresh server monitoring session and connects the background bridge. */
    public static synchronized void start(
            TickEngineConfig.Values configuredValues,
            BackgroundSchedulerControl backgroundControl
    ) {
        INSTANCE.scheduler.clear();
        INSTANCE.monitor.reset();
        INSTANCE.metrics.reset();
        INSTANCE.tickStartNanos = 0L;
        INSTANCE.recoveryMultiplier = 1.0D;
        INSTANCE.backgroundControl = Objects.requireNonNull(backgroundControl, "backgroundControl");
        reload(configuredValues);
        INSTANCE.running = true;
        INSTANCE.lastBackgroundMultiplier = Double.NaN;
        INSTANCE.applyBackgroundControl(0L, TickPressure.RELAXED);
    }

    /** Applies config changes without reallocating rolling timing windows. */
    public static synchronized void reload(TickEngineConfig.Values configuredValues) {
        TickEngineConfig.Values sanitized = TickEngineConfig.sanitize(configuredValues);
        INSTANCE.values = sanitized;
        INSTANCE.monitor.configure(sanitized.monitorThresholds());
        INSTANCE.budgetManager.configure(sanitized.budgetSettings());
        INSTANCE.scheduler.configure(sanitized.schedulerSettings());
        INSTANCE.throttle.setEnabled(sanitized.enabled() && sanitized.adaptiveThrottle());
        INSTANCE.debtManager.configure(
                sanitized.enabled() && sanitized.tickDebtCollapsing(),
                sanitized.maximumIndividualDebtSteps()
        );
        INSTANCE.metrics.configure(
                sanitized.profiling(),
                sanitized.slowSubsystemWarningMillis(),
                sanitized.slowWarningIntervalTicks()
        );
        INSTANCE.lastBackgroundMultiplier = Double.NaN;
        if (!sanitized.enabled()) {
            INSTANCE.scheduler.clear();
        }
        if (!sanitized.enabled() && INSTANCE.backgroundControl != null) {
            INSTANCE.backgroundControl.setExternalControl(BackgroundBudgetControl.UNRESTRICTED);
        }
    }

    /** Captures the monotonic start of a server tick from the highest-priority pre event. */
    public static void beginServerTick(long startNanos) {
        if (INSTANCE.running) {
            INSTANCE.tickStartNanos = startNanos;
        }
    }

    /**
     * Runs budgeted WO work and records the completed tick from the lowest-priority post event.
     */
    public static void finishServerTick(MinecraftServer server, BooleanSupplier serverHasTime) {
        Objects.requireNonNull(server, "server");
        INSTANCE.finishTick(server, Objects.requireNonNull(serverHasTime, "Server time allowance is required"));
    }

    /** Registers an opt-in subsystem policy. Existing identical registration is harmless. */
    public static void registerSubsystem(SubsystemPolicy policy) {
        INSTANCE.throttle.register(policy);
    }

    /** Returns the deferred tick-aware work queue. */
    public static TickWorkScheduler scheduler() {
        return INSTANCE.scheduler;
    }

    /** Returns the opt-in adaptive interval policy service. */
    public static AdaptiveThrottle throttle() {
        return INSTANCE.throttle;
    }

    /** Returns the explicit missed-tick policy service. */
    public static TickDebtManager debt() {
        return INSTANCE.debtManager;
    }

    /** Returns the lightweight WO-only profiler. */
    public static TickEngineMetrics metrics() {
        return INSTANCE.metrics;
    }

    /** Returns the current smoothed pressure without allocating a snapshot. */
    public static TickPressure pressure() {
        return INSTANCE.values.enabled() ? INSTANCE.monitor.pressure() : TickPressure.RELAXED;
    }

    /** Returns the current gradual-recovery multiplier without allocating. */
    public static double recoveryMultiplier() {
        return INSTANCE.values.enabled() ? INSTANCE.recoveryMultiplier : 1.0D;
    }

    /** Builds immutable debug data; this method is intentionally outside the tick hot path. */
    public static TickEngineSnapshot snapshot() {
        return INSTANCE.createSnapshot();
    }

    /** Stops process-scoped scheduling after worlds have saved. */
    public static synchronized void shutdown() {
        INSTANCE.running = false;
        INSTANCE.tickStartNanos = 0L;
        INSTANCE.scheduler.configure(new TickWorkScheduler.Settings(false, 1, 1, 1));
        INSTANCE.scheduler.clear();
        INSTANCE.monitor.reset();
        INSTANCE.recoveryMultiplier = 1.0D;
        if (INSTANCE.backgroundControl != null) {
            INSTANCE.backgroundControl.setExternalControl(BackgroundBudgetControl.UNRESTRICTED);
        }
        INSTANCE.backgroundControl = null;
    }

    private void finishTick(MinecraftServer server, BooleanSupplier serverHasTime) {
        if (!running) {
            return;
        }
        long optionalStart = System.nanoTime();
        long start = tickStartNanos > 0L && tickStartNanos <= optionalStart ? tickStartNanos : optionalStart;
        TickEngineConfig.Values current = values;

        if (!current.enabled()) {
            long backgroundBudget = serverHasTime.getAsBoolean() ? Long.MAX_VALUE : 0L;
            BackgroundEfficiencyManager.tick(
                    server,
                    server.getTickCount(),
                    backgroundBudget,
                    serverHasTime
            );
            monitor.recordTick(nanosToMillis(System.nanoTime() - start));
            return;
        }

        TickPressure priorPressure = monitor.pressure();
        TickBudget budget = budgetManager.begin(start, optionalStart, priorPressure, recoveryMultiplier);
        long effectiveDeadline = serverHasTime.getAsBoolean() ? budget.deadlineNanos() : optionalStart;
        scheduler.processTick(server.getTickCount(), effectiveDeadline, priorPressure, serverHasTime);

        long beforeBackground = System.nanoTime();
        long remaining = serverHasTime.getAsBoolean() ? budget.remainingNanos(beforeBackground) : 0L;
        applyBackgroundControl(server.getTickCount(), priorPressure);
        BackgroundEfficiencyManager.tick(server, server.getTickCount(), remaining, serverHasTime);

        long finished = System.nanoTime();
        budgetManager.finish(finished);
        TickPressure updatedPressure = monitor.recordTick(nanosToMillis(finished - start));
        updateRecovery(priorPressure, updatedPressure);
        metrics.setDeferredTasks(scheduler.queuedTasks());
        if (updatedPressure != priorPressure || monitor.tickCount() % 20L == 0L) {
            metrics.setThrottledSubsystems(throttle.throttledSubsystemCount(updatedPressure, recoveryMultiplier));
        }
        applyBackgroundControl(server.getTickCount(), updatedPressure);
    }

    private void updateRecovery(TickPressure previous, TickPressure current) {
        if (current.ordinal() > previous.ordinal()) {
            double immediateLimit = switch (current) {
                case RELAXED -> 1.0D;
                case BUSY -> 0.85D;
                case HIGH -> 0.60D;
                case CRITICAL -> 0.35D;
                case OVERLOADED -> 0.10D;
            };
            recoveryMultiplier = Math.min(recoveryMultiplier, immediateLimit);
            return;
        }
        double recoveryBoundary = switch (current) {
            case RELAXED, BUSY -> values.busyMspt();
            case HIGH -> values.highMspt();
            case CRITICAL -> values.criticalMspt();
            case OVERLOADED -> values.overloadedMspt();
        } - values.recoveryMarginMspt();
        if (current.ordinal() < previous.ordinal() || monitor.shortAverageMspt() < recoveryBoundary) {
            double recoveryStep = 1.0D / Math.max(1, values.recoveryTicks());
            recoveryMultiplier = Math.min(1.0D, recoveryMultiplier + recoveryStep);
        }
    }

    private void applyBackgroundControl(long currentTick, TickPressure pressure) {
        BackgroundSchedulerControl control = backgroundControl;
        if (control == null || !values.enabled()) {
            return;
        }
        boolean backgroundAllowed = pressure == TickPressure.RELAXED
                || pressure == TickPressure.BUSY
                || pressure == TickPressure.HIGH;
        boolean idleAllowed = pressure == TickPressure.RELAXED
                || pressure == TickPressure.BUSY && Math.floorMod(currentTick, 4L) == 0L;
        double pressureMultiplier = switch (pressure) {
            case RELAXED -> values.relaxedBudgetMultiplier();
            case BUSY -> values.busyBudgetMultiplier();
            case HIGH -> values.highBudgetMultiplier();
            case CRITICAL -> values.criticalBudgetMultiplier();
            case OVERLOADED -> values.overloadedBudgetMultiplier();
        };
        double effectiveMultiplier = pressureMultiplier * recoveryMultiplier;
        if (pressure == lastBackgroundPressure
                && Double.compare(effectiveMultiplier, lastBackgroundMultiplier) == 0
                && backgroundAllowed == lastBackgroundAllowed
                && idleAllowed == lastIdleAllowed) {
            return;
        }
        lastBackgroundPressure = pressure;
        lastBackgroundMultiplier = effectiveMultiplier;
        lastBackgroundAllowed = backgroundAllowed;
        lastIdleAllowed = idleAllowed;
        control.setExternalControl(new BackgroundBudgetControl(
                effectiveMultiplier,
                Long.MAX_VALUE,
                backgroundAllowed,
                idleAllowed
        ));
    }

    private TickEngineSnapshot createSnapshot() {
        TickBudget budget = budgetManager.current();
        TickEngineMetrics.Snapshot timingSnapshot = metrics.snapshot();
        BackgroundSchedulerControl control = backgroundControl;
        int backgroundQueued = control == null ? 0 : control.queuedTasks();
        double backgroundPressure = control == null ? 0.0D : control.queuePressure();
        return new TickEngineSnapshot(
                values.enabled() && running,
                monitor.estimatedTps(),
                monitor.currentMspt(),
                monitor.shortAverageMspt(),
                monitor.mediumAverageMspt(),
                monitor.recentMaximumMspt(),
                monitor.tickCount(),
                monitor.overloadedTickCount(),
                monitor.consecutiveOverloadedTicks(),
                values.enabled() ? monitor.pressure() : TickPressure.RELAXED,
                values.enabled() ? recoveryMultiplier : 1.0D,
                nanosToMillis(budget.allowedWorkNanos()),
                nanosToMillis(budget.usedWorkNanos()),
                nanosToMillis(budget.unusedWorkNanos()),
                scheduler.queuedTasks(),
                scheduler.queuePressure(),
                backgroundQueued,
                backgroundPressure,
                timingSnapshot.throttledSubsystems(),
                timingSnapshot.worstSubsystem(),
                timingSnapshot.subsystemTimings()
        );
    }

    private void registerDefaultSubsystems() {
        throttle.register(new SubsystemPolicy("weather", "Weather", TickPriority.NORMAL, 100, false));
        throttle.register(new SubsystemPolicy("ecosystem", "Ecosystem", TickPriority.BACKGROUND, 200, true));
        throttle.register(new SubsystemPolicy("water", "Water", TickPriority.NORMAL, 100, false));
        throttle.register(new SubsystemPolicy("labs", "Labs", TickPriority.GAMEPLAY, 20, false));
        throttle.register(new SubsystemPolicy("aether", "Aether", TickPriority.NORMAL, 100, true));
        throttle.register(new SubsystemPolicy("analytics", "Analytics", TickPriority.IDLE, 1200, true));
        throttle.register(new SubsystemPolicy("network", "Network", TickPriority.GAMEPLAY, 20, false));
    }

    private static double nanosToMillis(long nanos) {
        return Math.max(0L, nanos) / (double) TimeUnit.MILLISECONDS.toNanos(1L);
    }
}
