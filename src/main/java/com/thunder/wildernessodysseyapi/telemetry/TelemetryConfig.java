package com.thunder.wildernessodysseyapi.telemetry;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-wide telemetry configuration options.
 */
public final class TelemetryConfig {
    public static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.BooleanValue MASTER_ENABLED;
    public static ModConfigSpec.IntValue QUEUE_MAX_SIZE;
    public static ModConfigSpec.IntValue QUEUE_FLUSH_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue QUEUE_FLUSH_BATCH_SIZE;

    private static ModConfigSpec.Builder BUILDER;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the telemetry-master category in the unified server config. */
    public static void define(ModConfigSpec.Builder builder) {
        BUILDER = builder;
        BUILDER.push("telemetry");

        MASTER_ENABLED = BUILDER.comment("Master toggle for all telemetry features.")
                .define("enabled", true);

        QUEUE_MAX_SIZE = BUILDER.comment("Maximum number of telemetry payloads to keep in the retry queue.")
                .defineInRange("queueMaxSize", 512, 1, 10000);

        QUEUE_FLUSH_INTERVAL_TICKS = BUILDER.comment("How often (in ticks) to flush queued telemetry payloads.")
                .defineInRange("queueFlushIntervalTicks", 200, 20, 12000);

        QUEUE_FLUSH_BATCH_SIZE = BUILDER.comment("Maximum number of queued payloads to attempt per flush.")
                .defineInRange("queueFlushBatchSize", 32, 1, 512);

        BUILDER.pop();
        BUILDER = null;
    }

    private TelemetryConfig() {
    }

    public static TelemetryValues values() {
        return new TelemetryValues(
                MASTER_ENABLED.get(),
                QUEUE_MAX_SIZE.get(),
                QUEUE_FLUSH_INTERVAL_TICKS.get(),
                QUEUE_FLUSH_BATCH_SIZE.get()
        );
    }

    public record TelemetryValues(
            boolean enabled,
            int queueMaxSize,
            int queueFlushIntervalTicks,
            int queueFlushBatchSize
    ) {
    }
}
