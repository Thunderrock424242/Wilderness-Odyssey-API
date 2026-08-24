package com.thunder.wildernessodysseyapi.performance.background.config;

import com.thunder.wildernessodysseyapi.config.PerformanceServerConfig;
import com.thunder.wildernessodysseyapi.performance.background.BackgroundWorkScheduler;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.concurrent.TimeUnit;

/**
 * Server configuration for Wilderness Odyssey's opt-in background framework.
 */
public final class BackgroundEfficiencyConfig {
    public static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.IntValue MAX_TASKS_PER_TICK;
    public static ModConfigSpec.DoubleValue MAX_BACKGROUND_TIME_MS;
    public static ModConfigSpec.IntValue MAX_QUEUED_TASKS;
    public static ModConfigSpec.IntValue MAX_TASKS_PER_SUBSYSTEM;
    public static ModConfigSpec.BooleanValue ASYNC_ENABLED;
    public static ModConfigSpec.IntValue ASYNC_WORKER_THREADS;
    public static ModConfigSpec.IntValue ASYNC_MAX_QUEUED_JOBS;
    public static ModConfigSpec.IntValue ASYNC_APPLY_PER_TICK;
    public static ModConfigSpec.BooleanValue ACTIVITY_ENABLED;
    public static ModConfigSpec.BooleanValue NETWORK_BATCHING_ENABLED;
    public static ModConfigSpec.IntValue NETWORK_MAX_BATCH_SIZE;
    public static ModConfigSpec.IntValue NETWORK_MAX_DELAY_TICKS;
    public static ModConfigSpec.IntValue NETWORK_MAX_QUEUED_UPDATES;
    public static ModConfigSpec.BooleanValue ANALYTICS_BATCHING_ENABLED;
    public static ModConfigSpec.IntValue ANALYTICS_MAX_BATCH_SIZE;
    public static ModConfigSpec.IntValue ANALYTICS_MAX_DELAY_TICKS;
    public static ModConfigSpec.IntValue ANALYTICS_MAX_QUEUED_EVENTS;

    /** Defines this section inside the unified performance spec. */
    public static void define(ModConfigSpec.Builder builder) {
        if (ENABLED != null) {
            throw new IllegalStateException("Background Efficiency config section is already defined");
        }
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int conservativeWorkers = Math.max(1, Math.min(4, processors - 2));

        builder.push("backgroundEfficiency");
        ENABLED = builder.comment("Master toggle for Wilderness Odyssey background scheduling and batching.")
                .define("enabled", true);

        builder.push("scheduler");
        MAX_TASKS_PER_TICK = builder.comment("Maximum background task steps executed in one server-tick pass.")
                .defineInRange("maxTasksPerTick", 64, 1, 4096);
        MAX_BACKGROUND_TIME_MS = builder.comment("Maximum nominal server-thread time reserved for background work.")
                .defineInRange("maxBackgroundTimeMs", 2.0D, 0.0D, 25.0D);
        MAX_QUEUED_TASKS = builder.comment("Global bounded task queue capacity.")
                .defineInRange("maxQueuedTasks", 2048, 16, 65536);
        MAX_TASKS_PER_SUBSYSTEM = builder.comment("Per-subsystem queue limit that prevents a single producer from flooding work.")
                .defineInRange("maxTasksPerSubsystem", 256, 1, 8192);
        builder.pop();

        builder.push("async");
        ASYNC_ENABLED = builder.comment(
                        "Allows pure computation over immutable snapshots on bounded worker threads.",
                        "Disabled by default until a production subsystem explicitly adopts this secondary pool."
                )
                .define("enabled", false);
        ASYNC_WORKER_THREADS = builder.comment("CPU worker count; defaults conservatively below the available processor count.")
                .defineInRange("workerThreads", conservativeWorkers, 1, 16);
        ASYNC_MAX_QUEUED_JOBS = builder.comment("Maximum computations waiting for a worker or server-thread application.")
                .defineInRange("maxQueuedJobs", 128, 1, 8192);
        ASYNC_APPLY_PER_TICK = builder.comment("Maximum completed calculation results applied in one tick.")
                .defineInRange("maxApplyPerTick", 32, 1, 1024);
        builder.pop();

        builder.push("activity");
        ACTIVITY_ENABLED = builder.comment("Allows callers to classify work by player proximity.")
                .define("enabled", true);
        builder.pop();

        builder.push("networkBatching");
        NETWORK_BATCHING_ENABLED = builder.comment("Enables opt-in WO packet aggregation channels.")
                .define("enabled", true);
        NETWORK_MAX_BATCH_SIZE = builder.comment("Maximum updates combined into one channel dispatch.")
                .defineInRange("maxBatchSize", 32, 1, 1024);
        NETWORK_MAX_DELAY_TICKS = builder.comment("Maximum age before a non-empty batch becomes eligible to flush.")
                .defineInRange("maxDelayTicks", 5, 1, 200);
        NETWORK_MAX_QUEUED_UPDATES = builder.comment("Global bounded number of pending network updates.")
                .defineInRange("maxQueuedUpdates", 4096, 16, 65536);
        builder.pop();

        builder.push("analyticsBatching");
        ANALYTICS_BATCHING_ENABLED = builder.comment("Enables opt-in batching for non-save WO analytics and IO events.")
                .define("enabled", true);
        ANALYTICS_MAX_BATCH_SIZE = builder.comment("Maximum analytics events in one immutable worker batch.")
                .defineInRange("maxBatchSize", 64, 1, 2048);
        ANALYTICS_MAX_DELAY_TICKS = builder.comment("Maximum ticks before a partial analytics batch is submitted.")
                .defineInRange("maxDelayTicks", 100, 1, 12000);
        ANALYTICS_MAX_QUEUED_EVENTS = builder.comment("Global bounded analytics event capacity.")
                .defineInRange("maxQueuedEvents", 4096, 16, 65536);
        builder.pop();

        builder.pop();
    }

    private BackgroundEfficiencyConfig() {
    }

    /** Returns sanitized values or safe defaults if NeoForge has not loaded the file yet. */
    public static Values values() {
        ensureDefined();
        try {
            Values values = sanitize(new Values(
                    ENABLED.get(),
                    MAX_TASKS_PER_TICK.get(),
                    MAX_BACKGROUND_TIME_MS.get(),
                    MAX_QUEUED_TASKS.get(),
                    MAX_TASKS_PER_SUBSYSTEM.get(),
                    ASYNC_ENABLED.get(),
                    ASYNC_WORKER_THREADS.get(),
                    ASYNC_MAX_QUEUED_JOBS.get(),
                    ASYNC_APPLY_PER_TICK.get(),
                    ACTIVITY_ENABLED.get(),
                    NETWORK_BATCHING_ENABLED.get(),
                    NETWORK_MAX_BATCH_SIZE.get(),
                    NETWORK_MAX_DELAY_TICKS.get(),
                    NETWORK_MAX_QUEUED_UPDATES.get(),
                    ANALYTICS_BATCHING_ENABLED.get(),
                    ANALYTICS_MAX_BATCH_SIZE.get(),
                    ANALYTICS_MAX_DELAY_TICKS.get(),
                    ANALYTICS_MAX_QUEUED_EVENTS.get()
            ));
            return PerformanceServerConfig.enabled() ? values : disabled(values);
        } catch (RuntimeException exception) {
            Values values = defaults();
            return PerformanceServerConfig.enabled() ? values : disabled(values);
        }
    }

    /** Returns spec defaults without requiring an installed config. */
    public static Values defaults() {
        ensureDefined();
        return sanitize(new Values(
                ENABLED.getDefault(),
                MAX_TASKS_PER_TICK.getDefault(),
                MAX_BACKGROUND_TIME_MS.getDefault(),
                MAX_QUEUED_TASKS.getDefault(),
                MAX_TASKS_PER_SUBSYSTEM.getDefault(),
                ASYNC_ENABLED.getDefault(),
                ASYNC_WORKER_THREADS.getDefault(),
                ASYNC_MAX_QUEUED_JOBS.getDefault(),
                ASYNC_APPLY_PER_TICK.getDefault(),
                ACTIVITY_ENABLED.getDefault(),
                NETWORK_BATCHING_ENABLED.getDefault(),
                NETWORK_MAX_BATCH_SIZE.getDefault(),
                NETWORK_MAX_DELAY_TICKS.getDefault(),
                NETWORK_MAX_QUEUED_UPDATES.getDefault(),
                ANALYTICS_BATCHING_ENABLED.getDefault(),
                ANALYTICS_MAX_BATCH_SIZE.getDefault(),
                ANALYTICS_MAX_DELAY_TICKS.getDefault(),
                ANALYTICS_MAX_QUEUED_EVENTS.getDefault()
        ));
    }

    /** Attaches the compatibility alias after the unified spec has been assembled. */
    public static void attachSpec(ModConfigSpec spec) {
        if (CONFIG_SPEC != null && CONFIG_SPEC != spec) {
            throw new IllegalStateException("Background Efficiency config spec is already attached");
        }
        CONFIG_SPEC = spec;
    }

    private static Values disabled(Values values) {
        return new Values(
                false,
                values.maximumTasksPerTick(),
                values.maximumBackgroundTimeMillis(),
                values.maximumQueuedTasks(),
                values.maximumTasksPerSubsystem(),
                values.asyncEnabled(),
                values.asyncWorkerThreads(),
                values.asyncMaximumQueuedJobs(),
                values.asyncMaximumApplyPerTick(),
                values.activityEnabled(),
                values.networkBatchingEnabled(),
                values.networkMaximumBatchSize(),
                values.networkMaximumDelayTicks(),
                values.networkMaximumQueuedUpdates(),
                values.analyticsBatchingEnabled(),
                values.analyticsMaximumBatchSize(),
                values.analyticsMaximumDelayTicks(),
                values.analyticsMaximumQueuedEvents()
        );
    }

    private static void ensureDefined() {
        PerformanceServerConfig.initialize();
    }

    /** Normalizes externally constructed values for tests and defensive API use. */
    public static Values sanitize(Values values) {
        if (values == null || !Double.isFinite(values.maximumBackgroundTimeMillis())) {
            return rawDefaults();
        }
        int maxQueued = Math.max(1, values.maximumQueuedTasks());
        return new Values(
                values.enabled(),
                Math.max(1, values.maximumTasksPerTick()),
                Math.max(0.0D, Math.min(25.0D, values.maximumBackgroundTimeMillis())),
                maxQueued,
                Math.max(1, Math.min(maxQueued, values.maximumTasksPerSubsystem())),
                values.asyncEnabled(),
                Math.max(1, Math.min(16, values.asyncWorkerThreads())),
                Math.max(1, values.asyncMaximumQueuedJobs()),
                Math.max(1, values.asyncMaximumApplyPerTick()),
                values.activityEnabled(),
                values.networkBatchingEnabled(),
                Math.max(1, values.networkMaximumBatchSize()),
                Math.max(1, values.networkMaximumDelayTicks()),
                Math.max(1, values.networkMaximumQueuedUpdates()),
                values.analyticsBatchingEnabled(),
                Math.max(1, values.analyticsMaximumBatchSize()),
                Math.max(1, values.analyticsMaximumDelayTicks()),
                Math.max(1, values.analyticsMaximumQueuedEvents())
        );
    }

    private static Values rawDefaults() {
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new Values(true, 64, 2.0D, 2048, 256, false,
                Math.max(1, Math.min(4, processors - 2)), 128, 32, true,
                true, 32, 5, 4096, true, 64, 100, 4096);
    }

    /** Immutable runtime values shared by the background services. */
    public record Values(
            boolean enabled,
            int maximumTasksPerTick,
            double maximumBackgroundTimeMillis,
            int maximumQueuedTasks,
            int maximumTasksPerSubsystem,
            boolean asyncEnabled,
            int asyncWorkerThreads,
            int asyncMaximumQueuedJobs,
            int asyncMaximumApplyPerTick,
            boolean activityEnabled,
            boolean networkBatchingEnabled,
            int networkMaximumBatchSize,
            int networkMaximumDelayTicks,
            int networkMaximumQueuedUpdates,
            boolean analyticsBatchingEnabled,
            int analyticsMaximumBatchSize,
            int analyticsMaximumDelayTicks,
            int analyticsMaximumQueuedEvents
    ) {
        /** Converts server-thread limits into the scheduler's independent settings type. */
        public BackgroundWorkScheduler.Settings schedulerSettings() {
            return new BackgroundWorkScheduler.Settings(
                    enabled,
                    maximumTasksPerTick,
                    (long) (maximumBackgroundTimeMillis * TimeUnit.MILLISECONDS.toNanos(1L)),
                    maximumQueuedTasks,
                    maximumTasksPerSubsystem
            );
        }
    }
}
