package com.thunder.wildernessodysseyapi.dataengine.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration for bounded Data Engine work and synchronization. */
public final class DataEngineConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue TICK_BUDGET_MS;
    public static final ModConfigSpec.IntValue MAX_QUEUE_SIZE;
    public static final ModConfigSpec.IntValue MAX_DIRTY_ENTRIES;
    public static final ModConfigSpec.IntValue MAX_COMPLETED_TASKS;
    public static final ModConfigSpec.IntValue MAX_ASYNC_IN_FLIGHT;
    public static final ModConfigSpec.BooleanValue NETWORK_BATCHING;
    public static final ModConfigSpec.IntValue MAX_BATCH_ENTRIES;
    public static final ModConfigSpec.IntValue MAX_BATCH_BYTES;
    public static final ModConfigSpec.IntValue MAX_BATCH_DELAY_TICKS;
    public static final ModConfigSpec.IntValue MAX_PENDING_NETWORK_BYTES;
    public static final ModConfigSpec.BooleanValue INTEREST_MANAGEMENT;
    public static final ModConfigSpec.IntValue DEFAULT_CACHE_MAX_ENTRIES;
    public static final ModConfigSpec.BooleanValue METRICS;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("dataEngine");
        ENABLED = BUILDER.comment("Master switch for Wilderness Data Engine processing.")
                .define("enabled", true);
        TICK_BUDGET_MS = BUILDER.comment("Shared server-tick budget for non-critical Data Engine work.")
                .defineInRange("tickBudgetMs", 2.0D, 0.1D, 20.0D);
        MAX_QUEUE_SIZE = BUILDER.comment("Maximum queued update actions before coalescing/backpressure.")
                .defineInRange("maxQueueSize", 10_000, 256, 100_000);
        MAX_DIRTY_ENTRIES = BUILDER.comment("Maximum pushed dirty keys waiting to become work.")
                .defineInRange("maxDirtyEntries", 10_000, 256, 100_000);
        MAX_COMPLETED_TASKS = BUILDER.comment("Maximum worker results waiting for server-thread validation/application.")
                .defineInRange("maxCompletedTasks", 1_024, 32, 16_384);
        MAX_ASYNC_IN_FLIGHT = BUILDER.comment("Maximum Data Engine calculations running or waiting in the shared worker pool.")
                .defineInRange("maxAsyncInFlight", 128, 4, 4_096);
        NETWORK_BATCHING = BUILDER.comment("Groups compatible small deltas into bounded packets.")
                .define("networkBatching", true);
        MAX_BATCH_ENTRIES = BUILDER.comment("Maximum deltas in one Data Engine packet.")
                .defineInRange("maxBatchEntries", 256, 1, 512);
        MAX_BATCH_BYTES = BUILDER.comment("Approximate maximum encoded bytes in one Data Engine packet.")
                .defineInRange("maxBatchBytes", 32_768, 1_024, 262_144);
        MAX_BATCH_DELAY_TICKS = BUILDER.comment("Ticks a non-critical delta may wait to collect batch peers.")
                .defineInRange("maxBatchDelayTicks", 2, 0, 20);
        MAX_PENDING_NETWORK_BYTES = BUILDER.comment("Global byte bound for pending batched deltas.")
                .defineInRange("maxPendingNetworkBytes", 8_388_608, 65_536, 67_108_864);
        INTEREST_MANAGEMENT = BUILDER.comment("Filters spatial and explicitly subscribed deltas before queueing.")
                .define("interestManagement", true);
        DEFAULT_CACHE_MAX_ENTRIES = BUILDER.comment("Default LRU bound used by caches created through the root API.")
                .defineInRange("defaultCacheMaxEntries", 4_096, 64, 65_536);
        METRICS = BUILDER.comment("Collects inexpensive Data Engine counters and timings.")
                .define("metrics", true);
        DEBUG_LOGGING = BUILDER.comment("Enables lifecycle/backpressure diagnostics; never logs every tick.")
                .define("debugLogging", false);
        BUILDER.pop();
        CONFIG_SPEC = BUILDER.build();
    }

    private DataEngineConfig() {
    }

    /** Returns loaded values, or spec defaults during early construction/tests. */
    public static Values values() {
        try {
            return new Values(
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
        } catch (IllegalStateException exception) {
            return defaults();
        }
    }

    public static Values defaults() {
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
