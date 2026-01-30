package com.thunder.wildernessodysseyapi.ModPackPatches.Ocean.wave;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for surface wave simulation.
 */
public final class WaveConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue WAVE_PERIOD_SECONDS;
    public static final ModConfigSpec.DoubleValue BASE_AMPLITUDE_BLOCKS;
    public static final ModConfigSpec.DoubleValue CURRENT_STRENGTH;
    public static final ModConfigSpec.DoubleValue PLAYER_PROXIMITY_BLOCKS;
    public static final ModConfigSpec.DoubleValue RAIN_WIND_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue THUNDER_WIND_MULTIPLIER;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("waves");

        ENABLED = BUILDER.comment("Master toggle for surface wave motion.")
                .define("enabled", true);

        WAVE_PERIOD_SECONDS = BUILDER.comment("Duration of a full wave cycle (crest to crest) in seconds.")
                .defineInRange("wavePeriodSeconds", 6.0D, 1.0D, 30.0D);

        BASE_AMPLITUDE_BLOCKS = BUILDER.comment("Base vertical wave amplitude (in blocks) applied in oceans.")
                .defineInRange("baseAmplitudeBlocks", 0.18D, 0.0D, 4.0D);

        CURRENT_STRENGTH = BUILDER.comment("Vertical force multiplier applied to entities riding surface waves.")
                .defineInRange("currentStrength", 0.02D, 0.0D, 0.2D);

        PLAYER_PROXIMITY_BLOCKS = BUILDER.comment("Maximum distance to the nearest player before waves apply. Set to 0 to always apply.")
                .defineInRange("playerProximityBlocks", 128.0D, 0.0D, 1024.0D);

        RAIN_WIND_MULTIPLIER = BUILDER.comment("Amplitude multiplier applied when it is raining (windy conditions).")
                .defineInRange("rainWindMultiplier", 1.35D, 1.0D, 3.0D);

        THUNDER_WIND_MULTIPLIER = BUILDER.comment("Amplitude multiplier applied when it is thundering (very windy conditions).")
                .defineInRange("thunderWindMultiplier", 1.75D, 1.0D, 4.0D);

        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    private WaveConfig() {
    }

    public static WaveConfigValues values() {
        return new WaveConfigValues(
                ENABLED.get(),
                WAVE_PERIOD_SECONDS.get(),
                BASE_AMPLITUDE_BLOCKS.get(),
                CURRENT_STRENGTH.get(),
                PLAYER_PROXIMITY_BLOCKS.get(),
                RAIN_WIND_MULTIPLIER.get(),
                THUNDER_WIND_MULTIPLIER.get()
        );
    }

    /**
     * Returns configuration defaults without requiring the config file to be loaded.
     */
    public static WaveConfigValues defaultValues() {
        return new WaveConfigValues(
                ENABLED.getDefault(),
                WAVE_PERIOD_SECONDS.getDefault(),
                BASE_AMPLITUDE_BLOCKS.getDefault(),
                CURRENT_STRENGTH.getDefault(),
                PLAYER_PROXIMITY_BLOCKS.getDefault(),
                RAIN_WIND_MULTIPLIER.getDefault(),
                THUNDER_WIND_MULTIPLIER.getDefault()
        );
    }

    /**
     * Convenience record used to avoid repeated config lookups every tick.
     */
    public record WaveConfigValues(
            boolean enabled,
            double wavePeriodSeconds,
            double baseAmplitudeBlocks,
            double currentStrength,
            double playerProximityBlocks,
            double rainWindMultiplier,
            double thunderWindMultiplier
    ) {
        public long wavePeriodTicks() {
            return (long) Math.max(1L, Math.round(wavePeriodSeconds * 20.0D));
        }
    }
}
