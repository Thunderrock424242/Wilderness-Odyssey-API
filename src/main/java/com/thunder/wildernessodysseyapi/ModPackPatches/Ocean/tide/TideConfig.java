package com.thunder.wildernessodysseyapi.ModPackPatches.Ocean.tide;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Map;

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
    public static final ModConfigSpec.DoubleValue SOLAR_WEIGHT;
    public static final ModConfigSpec.DoubleValue SOLAR_CYCLE_RATIO;
    public static final ModConfigSpec.DoubleValue HARMONIC_WEIGHT;
    public static final ModConfigSpec.DoubleValue RAIN_OFFSET_NORMALIZED;
    public static final ModConfigSpec.DoubleValue THUNDER_OFFSET_NORMALIZED;
    public static final ModConfigSpec.IntValue RIVER_OCEAN_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue RIVER_OCEAN_SEARCH_STEP;
    public static final ModConfigSpec.DoubleValue RIVER_INLAND_MIN_FACTOR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_AMPLITUDE_MULTIPLIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_AMPLITUDE_MULTIPLIERS;
    public static final ModConfigSpec.DoubleValue CURRENT_STRENGTH;
    public static final ModConfigSpec.DoubleValue PLAYER_PROXIMITY_BLOCKS;

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

        SOLAR_WEIGHT = BUILDER.comment("Weight of a secondary solar tide component blended with the primary lunar tide.")
                .defineInRange("solarWeight", 0.35D, 0.0D, 2.0D);

        SOLAR_CYCLE_RATIO = BUILDER.comment("Cycle length multiplier for the solar tide component relative to the main tide cycle.")
                .defineInRange("solarCycleRatio", 1.08D, 0.5D, 2.0D);

        HARMONIC_WEIGHT = BUILDER.comment("Weight of a harmonic (overtide) term to better emulate mixed semidiurnal tides.")
                .defineInRange("harmonicWeight", 0.2D, 0.0D, 2.0D);

        RAIN_OFFSET_NORMALIZED = BUILDER.comment("Normalized tide offset applied during rain (scaled to the tide's [-1, 1] range).")
                .defineInRange("rainOffsetNormalized", 0.05D, -0.5D, 0.5D);

        THUNDER_OFFSET_NORMALIZED = BUILDER.comment("Normalized tide offset applied during thunderstorms (scaled to the tide's [-1, 1] range).")
                .defineInRange("thunderOffsetNormalized", 0.1D, -0.5D, 0.5D);

        RIVER_OCEAN_SEARCH_RADIUS = BUILDER.comment("Max distance (in blocks) to search for nearby ocean biomes to attenuate river tides.")
                .defineInRange("riverOceanSearchRadius", 128, 0, 1024);

        RIVER_OCEAN_SEARCH_STEP = BUILDER.comment("Search step size (in blocks) when scanning for nearby ocean biomes.")
                .defineInRange("riverOceanSearchStep", 16, 4, 64);

        RIVER_INLAND_MIN_FACTOR = BUILDER.comment("Minimum tide multiplier for inland rivers that never find an ocean biome.")
                .defineInRange("riverInlandMinFactor", 0.2D, 0.0D, 1.0D);

        DIMENSION_AMPLITUDE_MULTIPLIERS = BUILDER.comment("Per-dimension amplitude multipliers: \"namespace:dimension=multiplier\".")
                .define("dimensionAmplitudeMultipliers", List.of());

        BIOME_AMPLITUDE_MULTIPLIERS = BUILDER.comment("Per-biome amplitude multipliers: \"namespace:biome=multiplier\".")
                .define("biomeAmplitudeMultipliers", List.of());

        CURRENT_STRENGTH = BUILDER.comment("Vertical force multiplier applied to entities in water to reflect rising/falling tides.")
                .defineInRange("currentStrength", 0.004D, 0.0D, 0.05D);

        PLAYER_PROXIMITY_BLOCKS = BUILDER.comment("Maximum distance to the nearest player before tide currents apply. Set to 0 to always apply.")
                .defineInRange("playerProximityBlocks", 128.0D, 0.0D, 1024.0D);

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
                SOLAR_WEIGHT.get(),
                SOLAR_CYCLE_RATIO.get(),
                HARMONIC_WEIGHT.get(),
                RAIN_OFFSET_NORMALIZED.get(),
                THUNDER_OFFSET_NORMALIZED.get(),
                RIVER_OCEAN_SEARCH_RADIUS.get(),
                RIVER_OCEAN_SEARCH_STEP.get(),
                RIVER_INLAND_MIN_FACTOR.get(),
                TideConfigParsers.parseMultiplierMap(DIMENSION_AMPLITUDE_MULTIPLIERS.get()),
                TideConfigParsers.parseMultiplierMap(BIOME_AMPLITUDE_MULTIPLIERS.get()),
                CURRENT_STRENGTH.get(),
                PLAYER_PROXIMITY_BLOCKS.get()
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
                SOLAR_WEIGHT.getDefault(),
                SOLAR_CYCLE_RATIO.getDefault(),
                HARMONIC_WEIGHT.getDefault(),
                RAIN_OFFSET_NORMALIZED.getDefault(),
                THUNDER_OFFSET_NORMALIZED.getDefault(),
                RIVER_OCEAN_SEARCH_RADIUS.getDefault(),
                RIVER_OCEAN_SEARCH_STEP.getDefault(),
                RIVER_INLAND_MIN_FACTOR.getDefault(),
                TideConfigParsers.parseMultiplierMap(DIMENSION_AMPLITUDE_MULTIPLIERS.getDefault()),
                TideConfigParsers.parseMultiplierMap(BIOME_AMPLITUDE_MULTIPLIERS.getDefault()),
                CURRENT_STRENGTH.getDefault(),
                PLAYER_PROXIMITY_BLOCKS.getDefault()
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
            double solarWeight,
            double solarCycleRatio,
            double harmonicWeight,
            double rainOffsetNormalized,
            double thunderOffsetNormalized,
            int riverOceanSearchRadius,
            int riverOceanSearchStep,
            double riverInlandMinFactor,
            Map<String, Double> dimensionAmplitudeOverrides,
            Map<String, Double> biomeAmplitudeOverrides,
            double currentStrength,
            double playerProximityBlocks
    ) {
        public long cycleTicks() {
            return (long) Math.max(1L, Math.round(cycleMinutes * 60.0D * 20.0D));
        }

        public long phaseOffsetTicks() {
            return (long) Math.max(0L, Math.round(phaseOffsetMinutes * 60.0D * 20.0D));
        }
    }
}
