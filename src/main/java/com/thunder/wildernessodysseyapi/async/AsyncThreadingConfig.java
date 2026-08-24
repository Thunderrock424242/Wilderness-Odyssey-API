package com.thunder.wildernessodysseyapi.async;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config entries for the async task system.
 */
public final class AsyncThreadingConfig {
    public static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.IntValue MAX_THREADS;
    public static ModConfigSpec.IntValue QUEUE_SIZE;
    public static ModConfigSpec.IntValue APPLY_PER_TICK;
    public static ModConfigSpec.IntValue TASK_TIMEOUT_MS;
    public static ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the async-threading category in the unified common config. */
    public static void define(ModConfigSpec.Builder builder) {
        int hardwareThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        int defaultPoolSize = Math.max(1, hardwareThreads - 1);

        builder.push("asyncThreading");
        ENABLED = builder.comment("Master toggle for the async task system.")
                .define("enabled", true);
        MAX_THREADS = builder.comment("Worker pool size for CPU-bound tasks (recommended: cores - 1).")
                .defineInRange("maxThreads", defaultPoolSize, 1, 64);
        QUEUE_SIZE = builder.comment("Maximum tasks waiting for a worker thread before new submissions are rejected.")
                .defineInRange("queueSize", 256, 32, 4096);
        APPLY_PER_TICK = builder.comment("Maximum main-thread tasks applied per server tick to avoid long stalls.")
                .defineInRange("applyPerTick", 64, 1, 512);
        TASK_TIMEOUT_MS = builder.comment("Optional timeout for long-running worker tasks (0 to disable).")
                .defineInRange("taskTimeoutMs", 20000, 0, 600000);
        DEBUG_LOGGING = builder.comment("Enables verbose logging for async task scheduling and application.")
                .define("debugLogging", false);
        builder.pop();
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
