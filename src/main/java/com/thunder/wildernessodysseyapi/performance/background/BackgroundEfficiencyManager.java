package com.thunder.wildernessodysseyapi.performance.background;

import com.thunder.wildernessodysseyapi.performance.background.config.BackgroundEfficiencyConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Stable integration API for Wilderness Odyssey background services.
 *
 * <p>Feature systems depend on these focused public services rather than the
 * server lifecycle bridge or Tick Engine implementation.</p>
 */
public final class BackgroundEfficiencyManager {
    private static final BackgroundMetrics METRICS = new BackgroundMetrics();
    private static final BackgroundWorkScheduler SCHEDULER = new BackgroundWorkScheduler(METRICS);
    private static final ActivityManager ACTIVITY = new ActivityManager();
    private static final AsyncComputeManager ASYNC = new AsyncComputeManager(METRICS);
    private static final NetworkBatcher NETWORK = new NetworkBatcher(METRICS);
    private static final AnalyticsBatcher ANALYTICS = new AnalyticsBatcher(METRICS);

    private static volatile BackgroundEfficiencyConfig.Values values = BackgroundEfficiencyConfig.defaults();
    private static volatile boolean started;
    private static volatile boolean serverRunning;

    private BackgroundEfficiencyManager() {
    }

    /** Initializes server-owned workers and applies the current server config. */
    public static synchronized void start(BackgroundEfficiencyConfig.Values configuredValues) {
        serverRunning = true;
        // Discard anything submitted before a server owner existed. Registered
        // batching channels remain available, but their prior-session data does not.
        SCHEDULER.clear();
        NETWORK.clear();
        ANALYTICS.clear();
        METRICS.reset();
        applyConfiguration(configuredValues, true);
        started = values.enabled();
    }

    /** Applies a validated config snapshot and safely rebuilds bounded async workers. */
    public static synchronized void reload(BackgroundEfficiencyConfig.Values configuredValues) {
        applyConfiguration(configuredValues, serverRunning);
    }

    private static void applyConfiguration(
            BackgroundEfficiencyConfig.Values configuredValues,
            boolean initializeWorkers
    ) {
        values = BackgroundEfficiencyConfig.sanitize(configuredValues);
        SCHEDULER.configure(values.schedulerSettings());
        ACTIVITY.setEnabled(values.activityEnabled());
        NETWORK.configure(new NetworkBatcher.Settings(
                values.enabled() && values.networkBatchingEnabled(),
                values.networkMaximumBatchSize(),
                values.networkMaximumDelayTicks(),
                values.networkMaximumQueuedUpdates()
        ));
        ANALYTICS.configure(new AnalyticsBatcher.Settings(
                values.enabled() && values.analyticsBatchingEnabled(),
                values.analyticsMaximumBatchSize(),
                values.analyticsMaximumDelayTicks(),
                values.analyticsMaximumQueuedEvents()
        ));
        if (initializeWorkers) {
            ASYNC.initialize(new AsyncComputeManager.Settings(
                    values.enabled() && values.asyncEnabled(),
                    values.asyncWorkerThreads(),
                    values.asyncMaximumQueuedJobs()
            ));
            started = serverRunning && values.enabled();
            if (!values.enabled()) {
                SCHEDULER.clear();
                NETWORK.clear();
                ANALYTICS.clear();
            }
        }
    }

    /**
     * Drains bounded framework work at the end of a server tick.
     *
     * <p>The caller supplies the remaining Tick Engine capacity. Every service
     * shares one monotonic deadline so batching cannot silently exceed it before
     * the scheduler runs.</p>
     */
    public static void tick(MinecraftServer server, long currentTick, long availableBudgetNanos) {
        Objects.requireNonNull(server, "server");
        if (!started || !values.enabled()) {
            return;
        }

        long startedNanos = System.nanoTime();
        long boundedBudget = Math.max(0L, Math.min(
                availableBudgetNanos,
                (long) (values.maximumBackgroundTimeMillis() * TimeUnit.MILLISECONDS.toNanos(1L))
        ));
        long deadline = saturatedAdd(startedNanos, boundedBudget);

        ASYNC.drainServerThreadResults(
                server,
                values.asyncMaximumApplyPerTick(),
                deadline
        );
        NETWORK.flushDue(server, currentTick, deadline);
        ANALYTICS.flushDue(currentTick, ASYNC, deadline);
        long remaining = Math.max(0L, deadline - System.nanoTime());
        SCHEDULER.processTick(currentTick, remaining);
    }

    /** Stops workers and releases process-scoped queues after the server saves. */
    public static synchronized void shutdown() {
        serverRunning = false;
        started = false;
        // Reject late producers before clearing so shutdown/start races cannot
        // carry process-scoped work into the next server session.
        SCHEDULER.configure(new BackgroundWorkScheduler.Settings(false, 1, 0L, 1, 1));
        NETWORK.configure(new NetworkBatcher.Settings(false, 1, 1, 1));
        ANALYTICS.configure(new AnalyticsBatcher.Settings(false, 1, 1, 1));
        ASYNC.shutdown();
        SCHEDULER.clear();
        NETWORK.clear();
        ANALYTICS.clear();
    }

    /** Returns the central server-thread background scheduler. */
    public static BackgroundWorkScheduler scheduler() {
        return SCHEDULER;
    }

    /** Returns the stateless activity classification helper. */
    public static ActivityManager activity() {
        return ACTIVITY;
    }

    /** Returns the bounded snapshot-compute-apply service. */
    public static AsyncComputeManager async() {
        return ASYNC;
    }

    /** Returns the opt-in typed network aggregation service. */
    public static NetworkBatcher network() {
        return NETWORK;
    }

    /** Returns the opt-in analytics/IO aggregation service. */
    public static AnalyticsBatcher analytics() {
        return ANALYTICS;
    }

    /** Returns cheap aggregated metrics; call {@link BackgroundMetrics#snapshot()} for immutable data. */
    public static BackgroundMetrics metrics() {
        return METRICS;
    }

    /** Returns the bridge implemented by the background scheduler for the future/current Tick Engine. */
    public static BackgroundSchedulerControl schedulerControl() {
        return SCHEDULER;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

}
