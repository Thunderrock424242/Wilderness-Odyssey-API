package com.thunder.wildernessodysseyapi.diagnostics.performance;

import com.thunder.wildernessodysseyapi.config.PerformanceServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned master/observation settings, assembled with the optional module limits. */
public final class PerformanceDiagnosticsConfig {
    public enum Mode { OFF, AUTO, ON }

    private static ModConfigSpec.BooleanValue safeMode;
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.EnumValue<Mode> observation;
    private static ModConfigSpec.BooleanValue recording;
    private static ModConfigSpec.BooleanValue chunkWaitStacks;
    private static ModConfigSpec.IntValue sampleIntervalTicks;
    private static ModConfigSpec.IntValue windowTicks;
    private static ModConfigSpec.DoubleValue chunkWaitThresholdMillis;

    private PerformanceDiagnosticsConfig() { }

    /** Called while the existing performance category is open in the unified server spec. */
    public static void define(ModConfigSpec.Builder builder) {
        safeMode = builder.comment("Keep experimental persistence and chunk-priority changes disabled. Supported WO controls still obey safety limits.")
                .define("safeMode", true);
        builder.push("coordinator");
        enabled = builder.comment("Master switch for the whole coordinator, including diagnostics and recording.",
                        "Disabled by default. Restart the world/server to apply; existing engine settings are independent.")
                .define("enabled", false);
        observation = builder.comment("OFF disables collection and coordinator controls; AUTO and ON use the same bounded observation.",
                        "Configured modules require observation and the master switch. Restart the world/server to apply.")
                .defineEnum("observation", Mode.AUTO);
        recording = builder.comment("Record bounded samples and export at server stop; also controlled by /wo perf record.")
                .define("recording", false);
        sampleIntervalTicks = builder.comment("Ticks between JVM sampling and optional recording samples (100 = about 5 seconds at 20 TPS).")
                .defineInRange("sampleIntervalTicks", 100, 20, 1200);
        windowTicks = builder.comment("Maximum retained tick durations for exact rolling percentiles.")
                .defineInRange("windowTicks", 600, 20, 12_000);
        chunkWaitThresholdMillis = builder.comment("Minimum synchronous chunk request duration recorded as slow, in milliseconds.")
                .defineInRange("chunkWaitThresholdMillis", 50.0, 1.0, 60_000.0);
        chunkWaitStacks = builder.comment("Capture up to 16 caller frames, at most once every 10 seconds. Coordinates still record when false.")
                .define("chunkWaitStacks", false);
        com.thunder.wildernessodysseyapi.performance.coordinator.CoordinatorConfig.define(builder);
        builder.pop();
    }

    public static Values values() {
        PerformanceServerConfig.initialize();
        try {
            return new Values(enabled.get(), safeMode.get(), observation.get(), recording.get(), sampleIntervalTicks.get(),
                    windowTicks.get(), chunkWaitThresholdMillis.get(), chunkWaitStacks.get());
        } catch (IllegalStateException unloaded) {
            return new Values(enabled.getDefault(), safeMode.getDefault(), observation.getDefault(), recording.getDefault(),
                    sampleIntervalTicks.getDefault(), windowTicks.getDefault(), chunkWaitThresholdMillis.getDefault(),
                    chunkWaitStacks.getDefault());
        }
    }

    public record Values(boolean enabled, boolean safeMode, Mode observation, boolean recording, int sampleIntervalTicks,
                         int windowTicks, double chunkWaitThresholdMillis, boolean chunkWaitStacks) { }
}
