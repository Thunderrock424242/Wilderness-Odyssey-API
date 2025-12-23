package com.thunder.wildernessodysseyapi.async;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config entries for the async task system.
 */
public final class AsyncThreadingConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MAX_THREADS;
    public static final ModConfigSpec.IntValue QUEUE_SIZE;
    public static final ModConfigSpec.IntValue APPLY_PER_TICK;
    public static final ModConfigSpec.IntValue TASK_TIMEOUT_MS;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        int hardwareThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        int defaultPoolSize = Math.max(1, hardwareThreads - 1);

        BUILDER.push("asyncThreading");
        ENABLED = BUILDER.comment("Master toggle for the async task system.")
                .define("enabled", true);
        MAX_THREADS = BUILDER.comment("Worker pool size for CPU-bound tasks (recommended: cores - 1).")
                .defineInRange("maxThreads", defaultPoolSize, 1, 64);
        QUEUE_SIZE = BUILDER.comment("Maximum tasks waiting for a worker thread before new submissions are rejected.")
                .defineInRange("queueSize", 256, 32, 4096);
        APPLY_PER_TICK = BUILDER.comment("Maximum main-thread tasks applied per server tick to avoid long stalls.")
                .defineInRange("applyPerTick", 64, 1, 512);
        TASK_TIMEOUT_MS = BUILDER.comment("Optional timeout for long-running worker tasks (0 to disable).")
                .defineInRange("taskTimeoutMs", 20000, 0, 600000);
        DEBUG_LOGGING = BUILDER.comment("Enables verbose logging for async task scheduling and application.")
                .define("debugLogging", false);
        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    private AsyncThreadingConfig() {
    }

    public static AsyncConfigValues values() {
        try {
            return new AsyncConfigValues(
                    ENABLED.get(),
                    MAX_THREADS.get(),
                    QUEUE_SIZE.get(),
                    APPLY_PER_TICK.get(),
                    TASK_TIMEOUT_MS.get(),
                    DEBUG_LOGGING.get()
            );
        } catch (IllegalStateException ex) {
            return defaultValues();
        }
    }

    /**
     * Returns configuration defaults without requiring the config file to be loaded.
     */
    public static AsyncConfigValues defaultValues() {
        return new AsyncConfigValues(
                ENABLED.getDefault(),
                MAX_THREADS.getDefault(),
                QUEUE_SIZE.getDefault(),
                APPLY_PER_TICK.getDefault(),
                TASK_TIMEOUT_MS.getDefault(),
                DEBUG_LOGGING.getDefault()
        );
    }

    public record AsyncConfigValues(
            boolean enabled,
            int maxThreads,
            int queueSize,
            int applyPerTick,
            int taskTimeoutMs,
            boolean debugLogging
    ) { }
}
