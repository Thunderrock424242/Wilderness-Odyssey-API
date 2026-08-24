package com.thunder.wildernessodysseyapi.dataengine.config;

import com.thunder.wildernessodysseyapi.config.PerformanceServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration for bounded Data Engine work and synchronization. */
public final class DataEngineConfig {
    public static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.DoubleValue TICK_BUDGET_MS;
    public static ModConfigSpec.IntValue MAX_QUEUE_SIZE;
    public static ModConfigSpec.IntValue MAX_DIRTY_ENTRIES;
    public static ModConfigSpec.IntValue MAX_COMPLETED_TASKS;
    public static ModConfigSpec.IntValue MAX_ASYNC_IN_FLIGHT;
    public static ModConfigSpec.BooleanValue NETWORK_BATCHING;
    public static ModConfigSpec.IntValue MAX_BATCH_ENTRIES;
    public static ModConfigSpec.IntValue MAX_BATCH_BYTES;
    public static ModConfigSpec.IntValue MAX_BATCH_DELAY_TICKS;
    public static ModConfigSpec.IntValue MAX_PENDING_NETWORK_BYTES;
    public static ModConfigSpec.BooleanValue INTEREST_MANAGEMENT;
    public static ModConfigSpec.IntValue DEFAULT_CACHE_MAX_ENTRIES;
    public static ModConfigSpec.BooleanValue METRICS;
    public static ModConfigSpec.BooleanValue DEBUG_LOGGING;

    /** Defines this section inside the unified performance spec. */
    public static void define(ModConfigSpec.Builder builder) {
        if (ENABLED != null) {
            throw new IllegalStateException("Data Engine config section is already defined");
        }
        builder.push("dataEngine");
        ENABLED = builder.comment("Master switch for Wilderness Data Engine processing.")
                .define("enabled", true);
        TICK_BUDGET_MS = builder.comment("Shared server-tick budget for non-critical Data Engine work.")
                .defineInRange("tickBudgetMs", 2.0D, 0.1D, 20.0D);
        MAX_QUEUE_SIZE = builder.comment("Maximum queued update actions before coalescing/backpressure.")
                .defineInRange("maxQueueSize", 10_000, 256, 100_000);
        MAX_DIRTY_ENTRIES = builder.comment("Maximum pushed dirty keys waiting to become work.")
                .defineInRange("maxDirtyEntries", 10_000, 256, 100_000);
        MAX_COMPLETED_TASKS = builder.comment("Maximum worker results waiting for server-thread validation/application.")
                .defineInRange("maxCompletedTasks", 1_024, 32, 16_384);
        MAX_ASYNC_IN_FLIGHT = builder.comment("Maximum Data Engine calculations running or waiting in the shared worker pool.")
                .defineInRange("maxAsyncInFlight", 128, 4, 4_096);
        NETWORK_BATCHING = builder.comment("Groups compatible small deltas into bounded packets.")
                .define("networkBatching", true);
        MAX_BATCH_ENTRIES = builder.comment("Maximum deltas in one Data Engine packet.")
                .defineInRange("maxBatchEntries", 256, 1, 512);
        MAX_BATCH_BYTES = builder.comment("Approximate maximum encoded bytes in one Data Engine packet.")
                .defineInRange("maxBatchBytes", 32_768, 1_024, 262_144);
        MAX_BATCH_DELAY_TICKS = builder.comment("Ticks a non-critical delta may wait to collect batch peers.")
                .defineInRange("maxBatchDelayTicks", 2, 0, 20);
        MAX_PENDING_NETWORK_BYTES = builder.comment("Global byte bound for pending batched deltas.")
                .defineInRange("maxPendingNetworkBytes", 8_388_608, 65_536, 67_108_864);
        INTEREST_MANAGEMENT = builder.comment("Filters spatial and explicitly subscribed deltas before queueing.")
                .define("interestManagement", true);
        DEFAULT_CACHE_MAX_ENTRIES = builder.comment("Default LRU bound used by caches created through the root API.")
                .defineInRange("defaultCacheMaxEntries", 4_096, 64, 65_536);
        METRICS = builder.comment("Collects inexpensive Data Engine counters and timings.")
                .define("metrics", true);
        DEBUG_LOGGING = builder.comment("Enables lifecycle/backpressure diagnostics; never logs every tick.")
                .define("debugLogging", false);
        builder.pop();
    }

    private DataEngineConfig() {
    }

    /** Returns loaded values, or spec defaults during early construction/tests. */
    public static Values values() {
        ensureDefined();
        try {
            Values values = new Values(
                    ENABLED.get(),
                    millisecondsToNanos(TICK_BUDGET_MS.get()),
                    MAX_QUEUE_SIZE.get(),
                    MAX_DIRTY_ENTRIES.get(),
                    MAX_COMPLETED_TASKS.get(),
                    MAX_ASYNC_IN_FLIGHT.get(),
                    NETWORK_BATCHING.get(),
                    MAX_BATCH_ENTRIES.get(),
                    MAX_BATCH_BYTES.get(),
                    MAX_BATCH_DELAY_TICKS.get(),
                    MAX_PENDING_NETWORK_BYTES.get(),
                    INTEREST_MANAGEMENT.get(),
                    DEFAULT_CACHE_MAX_ENTRIES.get(),
                    METRICS.get(),
                    DEBUG_LOGGING.get()
            );
            return PerformanceServerConfig.enabled() ? values : disabled(values);
        } catch (IllegalStateException exception) {
            Values values = defaults();
            return PerformanceServerConfig.enabled() ? values : disabled(values);
        }
    }

    public static Values defaults() {
        ensureDefined();
        return new Values(
                ENABLED.getDefault(),
                millisecondsToNanos(TICK_BUDGET_MS.getDefault()),
                MAX_QUEUE_SIZE.getDefault(),
                MAX_DIRTY_ENTRIES.getDefault(),
                MAX_COMPLETED_TASKS.getDefault(),
                MAX_ASYNC_IN_FLIGHT.getDefault(),
                NETWORK_BATCHING.getDefault(),
                MAX_BATCH_ENTRIES.getDefault(),
                MAX_BATCH_BYTES.getDefault(),
                MAX_BATCH_DELAY_TICKS.getDefault(),
                MAX_PENDING_NETWORK_BYTES.getDefault(),
                INTEREST_MANAGEMENT.getDefault(),
                DEFAULT_CACHE_MAX_ENTRIES.getDefault(),
                METRICS.getDefault(),
                DEBUG_LOGGING.getDefault()
        );
    }

    /** Attaches the compatibility alias after the unified spec has been assembled. */
    public static void attachSpec(ModConfigSpec spec) {
        if (CONFIG_SPEC != null && CONFIG_SPEC != spec) {
            throw new IllegalStateException("Data Engine config spec is already attached");
        }
        CONFIG_SPEC = spec;
    }

    private static Values disabled(Values values) {
        return new Values(
                false,
                values.tickBudgetNanos(),
                values.maxQueueSize(),
                values.maxDirtyEntries(),
                values.maxCompletedTasks(),
                values.maxAsyncInFlight(),
                values.networkBatching(),
                values.maxBatchEntries(),
                values.maxBatchBytes(),
                values.maxBatchDelayTicks(),
                values.maxPendingNetworkBytes(),
                values.interestManagement(),
                values.defaultCacheMaxEntries(),
                values.metrics(),
                values.debugLogging()
        );
    }

    private static void ensureDefined() {
        PerformanceServerConfig.initialize();
    }

    private static long millisecondsToNanos(double milliseconds) {
        return Math.max(1L, Math.round(milliseconds * 1_000_000.0D));
    }

    /** Immutable runtime config snapshot. Worker threads come from the existing shared async config. */
    public record Values(
            boolean enabled,
            long tickBudgetNanos,
            int maxQueueSize,
            int maxDirtyEntries,
            int maxCompletedTasks,
            int maxAsyncInFlight,
            boolean networkBatching,
            int maxBatchEntries,
            int maxBatchBytes,
            int maxBatchDelayTicks,
            int maxPendingNetworkBytes,
            boolean interestManagement,
            int defaultCacheMaxEntries,
            boolean metrics,
            boolean debugLogging
    ) {
    }
}
