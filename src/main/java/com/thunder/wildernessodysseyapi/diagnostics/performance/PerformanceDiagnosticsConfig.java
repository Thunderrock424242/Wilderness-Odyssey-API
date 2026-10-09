package com.thunder.wildernessodysseyapi.diagnostics.performance;

import com.thunder.wildernessodysseyapi.config.PerformanceServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned observation settings; none authorizes an optimization in Phase 0. */
public final class PerformanceDiagnosticsConfig {
    public enum Mode { OFF, AUTO, ON }

    private static ModConfigSpec.BooleanValue safeMode;
    private static ModConfigSpec.EnumValue<Mode> observation;
    private static ModConfigSpec.BooleanValue recording;
    private static ModConfigSpec.BooleanValue chunkWaitStacks;
    private static ModConfigSpec.IntValue sampleIntervalTicks;
    private static ModConfigSpec.IntValue windowTicks;
    private static ModConfigSpec.DoubleValue chunkWaitThresholdMillis;

    private PerformanceDiagnosticsConfig() { }

    /** Called while the existing performance category is open in the unified server spec. */
    public static void define(ModConfigSpec.Builder builder) {
        safeMode = builder.comment("Keep the performance coordinator observation-only; Phase 0 has no optimization modules.")
                .define("safeMode", true);
        builder.push("coordinator");
        observation = builder.comment("OFF disables collection; AUTO and ON observe with the same bounded Phase 0 limits.",
                        "These modes never alter Minecraft saves, chunk scheduling or worker limits. Restart the server to apply.")
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
        builder.pop();
    }

    public static Values values() {
        PerformanceServerConfig.initialize();
        try {
            return new Values(safeMode.get(), observation.get(), recording.get(), sampleIntervalTicks.get(),
                    windowTicks.get(), chunkWaitThresholdMillis.get(), chunkWaitStacks.get());
        } catch (IllegalStateException unloaded) {
            return new Values(safeMode.getDefault(), observation.getDefault(), recording.getDefault(),
                    sampleIntervalTicks.getDefault(), windowTicks.getDefault(), chunkWaitThresholdMillis.getDefault(),
                    chunkWaitStacks.getDefault());
        }
    }

    public record Values(boolean safeMode, Mode observation, boolean recording, int sampleIntervalTicks,
                         int windowTicks, double chunkWaitThresholdMillis, boolean chunkWaitStacks) { }
}
