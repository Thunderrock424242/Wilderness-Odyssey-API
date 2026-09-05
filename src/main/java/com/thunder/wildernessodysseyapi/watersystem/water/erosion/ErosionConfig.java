package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server controls installed inside the existing water simulation specification. */
public final class ErosionConfig {
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.IntValue checks;
    private static ModConfigSpec.IntValue changes;
    private static ModConfigSpec.DoubleValue resistance;

    private ErosionConfig() { }

    /** Appends conservative erosion controls to the existing water config owner. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("erosion");
        enabled = builder.comment("Allow slow erosion in newly generated, eligible natural chunks only. Legacy chunks remain protected.")
                .define("enabled", true);
        checks = builder.comment("Maximum loaded natural terrain candidates evaluated per dimension per second.")
                .defineInRange("checksPerSecond", 8, 0, 64);
        changes = builder.comment("Combined erosion and deposition changes per dimension per rolling minute. Each chunk also waits a minute between changes.")
                .defineInRange("changesPerMinute", 4, 0, 16);
        resistance = builder.comment("Exposure-seconds multiplier for material resistance. Higher values slow terrain changes.")
                .defineInRange("resistanceScale", 1.0, 0.1, 100.0);
        builder.pop();
    }

    /** Returns whether the optional slow terrain response is enabled. */
    public static boolean enabled() { return enabled != null && enabled.get(); }
    /** Returns the per-second candidate budget. */
    public static int checks() { return checks == null ? 0 : checks.get(); }
    /** Returns the rolling per-minute mutation budget. */
    public static int changes() { return changes == null ? 0 : changes.get(); }
    /** Returns the configured material exposure multiplier. */
    public static float resistanceScale() { return resistance == null ? 1.0f : resistance.get().floatValue(); }
}
