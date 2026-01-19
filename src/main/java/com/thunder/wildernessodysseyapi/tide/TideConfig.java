package com.thunder.wildernessodysseyapi.tide;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the tide simulation.
 */
public final class TideConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue CYCLE_MINUTES;
    public static final ModConfigSpec.DoubleValue OCEAN_AMPLITUDE_BLOCKS;
    public static final ModConfigSpec.DoubleValue RIVER_AMPLITUDE_BLOCKS;
    public static final ModConfigSpec.DoubleValue PHASE_OFFSET_MINUTES;
    public static final ModConfigSpec.DoubleValue CURRENT_STRENGTH;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("tides");

        ENABLED = BUILDER.comment("Master toggle for the dynamic tide simulation.")
                .define("enabled", true);

        CYCLE_MINUTES = BUILDER.comment("Duration of a full tide cycle (rise + fall) in real-time minutes.")
                .defineInRange("cycleMinutes", 40.0D, 5.0D, 240.0D);

        OCEAN_AMPLITUDE_BLOCKS = BUILDER.comment("Maximum vertical change (in blocks) applied to ocean tides.")
                .defineInRange("oceanAmplitudeBlocks", 2.5D, 0.0D, 16.0D);

        RIVER_AMPLITUDE_BLOCKS = BUILDER.comment("Maximum vertical change (in blocks) applied to river tides.")
                .defineInRange("riverAmplitudeBlocks", 1.5D, 0.0D, 16.0D);

        PHASE_OFFSET_MINUTES = BUILDER.comment("Optional offset to desynchronize tide peaks from the day/night cycle.")
                .defineInRange("phaseOffsetMinutes", 0.0D, 0.0D, 240.0D);

        CURRENT_STRENGTH = BUILDER.comment("Vertical force multiplier applied to entities in water to reflect rising/falling tides.")
                .defineInRange("currentStrength", 0.004D, 0.0D, 0.05D);

        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    private TideConfig() {
    }

    public static TideConfigValues values() {
        return new TideConfigValues(
                ENABLED.get(),
                CYCLE_MINUTES.get(),
                OCEAN_AMPLITUDE_BLOCKS.get(),
                RIVER_AMPLITUDE_BLOCKS.get(),
                PHASE_OFFSET_MINUTES.get(),
                CURRENT_STRENGTH.get()
        );
    }

    /**
     * Returns configuration defaults without requiring the config file to be loaded.
     */
    public static TideConfigValues defaultValues() {
        return new TideConfigValues(
                ENABLED.getDefault(),
                CYCLE_MINUTES.getDefault(),
                OCEAN_AMPLITUDE_BLOCKS.getDefault(),
                RIVER_AMPLITUDE_BLOCKS.getDefault(),
                PHASE_OFFSET_MINUTES.getDefault(),
                CURRENT_STRENGTH.getDefault()
        );
    }

    /**
     * Convenience record used to avoid repeated config lookups every tick.
     */
    public record TideConfigValues(
            boolean enabled,
            double cycleMinutes,
            double oceanAmplitudeBlocks,
            double riverAmplitudeBlocks,
            double phaseOffsetMinutes,
            double currentStrength
    ) {
        public long cycleTicks() {
            return (long) Math.max(1L, Math.round(cycleMinutes * 60.0D * 20.0D));
        }

        public long phaseOffsetTicks() {
            return (long) Math.max(0L, Math.round(phaseOffsetMinutes * 60.0D * 20.0D));
        }
    }
}
