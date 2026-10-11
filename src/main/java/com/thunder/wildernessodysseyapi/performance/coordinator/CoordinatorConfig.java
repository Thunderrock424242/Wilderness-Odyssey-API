package com.thunder.wildernessodysseyapi.performance.coordinator;

import com.thunder.wildernessodysseyapi.config.PerformanceServerConfig;
import com.thunder.wildernessodysseyapi.diagnostics.performance.PerformanceDiagnosticsConfig.Mode;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Session-captured limits for opt-in WO controls; required persistence is never optional. */
public final class CoordinatorConfig {
    private static ModConfigSpec.EnumValue<Mode> asyncGovernor;
    private static ModConfigSpec.EnumValue<Mode> saveDrain;
    private static ModConfigSpec.DoubleValue maximumOptionalMillis;
    private static ModConfigSpec.DoubleValue headroomFraction;
    private static ModConfigSpec.IntValue entryStableTicks;
    private static ModConfigSpec.IntValue cooldownTicks;
    private static ModConfigSpec.IntValue minimumDirtyTicks;
    private static ModConfigSpec.IntValue minimumQuietTicks;
    private static ModConfigSpec.IntValue maximumTrackedChunks;

    private CoordinatorConfig() { }

    public static void define(ModConfigSpec.Builder builder) {
        asyncGovernor = builder.comment("OFF keeps existing WO worker/scheduler policies; AUTO/ON both yield under pressure.")
                .defineEnum("asyncGovernor", Mode.AUTO);
        saveDrain = builder.comment("Native loaded-chunk save smoothing. Leave OFF until profiling identifies a cold dirty backlog.",
                        "AUTO/ON obey ownership vetoes, time limits, hot-chunk avoidance and failure fallback.")
                .defineEnum("saveDrain", Mode.OFF);
        maximumOptionalMillis = builder.comment("Maximum shared coordinator optional work allowance per tick, milliseconds.")
                .defineInRange("maximumOptionalMillis", 2.0, 0.0, 5.0);
        headroomFraction = builder.comment("Fraction of smoothed headroom below 50 MSPT available to optional WO work.")
                .defineInRange("headroomFraction", 0.10, 0.0, 0.25);
        entryStableTicks = builder.comment("Healthy ticks after the first player/server-start milestone before optional work recovers.")
                .defineInRange("entryStableTicks", 100, 20, 1200);
        cooldownTicks = builder.comment("Watchdog pause duration in server ticks after repeated optional-work overruns.")
                .defineInRange("cooldownTicks", 200, 100, 600);
        minimumDirtyTicks = builder.comment("Minimum dirty age before optional saving, server ticks (600 = 30 seconds at 20 TPS).")
                .defineInRange("minimumDirtyTicks", 600, 100, 12_000);
        minimumQuietTicks = builder.comment("Minimum time since last dirty notification before optional saving, server ticks.")
                .defineInRange("minimumQuietTicks", 100, 20, 1200);
        maximumTrackedChunks = builder.comment("Maximum coordinate-only save records. Overflow leaves native dirty state untouched.")
                .defineInRange("maximumTrackedChunks", 4096, 128, 65_536);
    }

    public static Values values() {
        PerformanceServerConfig.initialize();
        try {
            return new Values(asyncGovernor.get(), saveDrain.get(), maximumOptionalMillis.get(), headroomFraction.get(),
                    entryStableTicks.get(), cooldownTicks.get(), minimumDirtyTicks.get(), minimumQuietTicks.get(), maximumTrackedChunks.get());
        } catch (IllegalStateException unloaded) {
            return new Values(asyncGovernor.getDefault(), saveDrain.getDefault(), maximumOptionalMillis.getDefault(),
                    headroomFraction.getDefault(), entryStableTicks.getDefault(), cooldownTicks.getDefault(),
                    minimumDirtyTicks.getDefault(), minimumQuietTicks.getDefault(), maximumTrackedChunks.getDefault());
        }
    }

    public record Values(Mode asyncGovernor, Mode saveDrain, double maximumOptionalMillis, double headroomFraction,
                         int entryStableTicks, int cooldownTicks, int minimumDirtyTicks, int minimumQuietTicks,
                         int maximumTrackedChunks) { }
}
