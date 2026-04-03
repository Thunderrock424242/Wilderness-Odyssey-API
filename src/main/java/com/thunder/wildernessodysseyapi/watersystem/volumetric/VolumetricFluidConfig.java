package com.thunder.wildernessodysseyapi.watersystem.volumetric;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side tuning values for the sparse volumetric water simulation.
 */
public final class VolumetricFluidConfig {
    private static final java.util.Set<String> VALID_PRESETS = java.util.Set.of("safe", "realism", "custom");

    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue TICK_INTERVAL;
    public static final ModConfigSpec.IntValue PLAYER_SAMPLE_RADIUS;
    public static final ModConfigSpec.IntValue PLAYER_SAMPLE_STEP;
    public static final ModConfigSpec.IntValue ACTIVE_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> PRESET;
    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_ENGINE;
    public static final ModConfigSpec.BooleanValue ENABLE_LAVA;
    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_LAVA_ENGINE;
    public static final ModConfigSpec.DoubleValue DOWNWARD_TRANSFER;
    public static final ModConfigSpec.DoubleValue LATERAL_TRANSFER;
    public static final ModConfigSpec.DoubleValue ADVECTION_TRANSFER;
    public static final ModConfigSpec.DoubleValue PRESSURE_STRENGTH;
    public static final ModConfigSpec.IntValue PRESSURE_ITERATIONS;
    public static final ModConfigSpec.DoubleValue INERTIA_DAMPING;
    public static final ModConfigSpec.DoubleValue PLACE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue REMOVE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue MIN_CELL_VOLUME;
    public static final ModConfigSpec.IntValue MAX_CELLS_PER_STEP;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("volumetricFluid");

        ENABLED = BUILDER.comment("Master toggle for sparse volumetric water simulation.")
                .define("enabled", true);

        TICK_INTERVAL = BUILDER.comment("Simulation step cadence in game ticks.")
                .defineInRange("tickInterval", 2, 1, 20);

        PLAYER_SAMPLE_RADIUS = BUILDER.comment("Radius around each player used to ingest vanilla water into the simulation.")
                .defineInRange("playerSampleRadius", 16, 1, 64);

        PLAYER_SAMPLE_STEP = BUILDER.comment("Stride used when sampling world water around players. Higher = cheaper.")
                .defineInRange("playerSampleStep", 2, 1, 8);

        ACTIVE_RADIUS = BUILDER.comment("Cells farther than this many blocks from all players are skipped for expensive updates.")
                .defineInRange("activeRadius", 80, 8, 256);

        PRESET = BUILDER.comment("Preset profile: safe, realism, or custom. Presets override many tunables below.")
                .define("preset", "safe", value -> value instanceof String preset
                        && VALID_PRESETS.contains(preset.toLowerCase(java.util.Locale.ROOT)));

        REPLACE_VANILLA_ENGINE = BUILDER.comment("If true, cancels vanilla water fluid ticks and routes behavior through the volumetric solver.")
                .define("replaceVanillaWaterEngine", true);

        ENABLE_LAVA = BUILDER.comment("If true, runs the hybrid volumetric solver for lava in addition to water.")
                .define("enableLava", false);

        REPLACE_VANILLA_LAVA_ENGINE = BUILDER.comment("If true, cancels vanilla lava fluid ticks and routes behavior through the volumetric solver.")
                .define("replaceVanillaLavaEngine", false);

        DOWNWARD_TRANSFER = BUILDER.comment("Max water volume moved downward per step from one cell.")
                .defineInRange("downwardTransfer", 0.35D, 0.01D, 1.0D);

        LATERAL_TRANSFER = BUILDER.comment("Max water volume moved horizontally per neighbor per step.")
                .defineInRange("lateralTransfer", 0.10D, 0.0D, 0.5D);

        ADVECTION_TRANSFER = BUILDER.comment("Volume moved along the velocity vector after pressure solving.")
                .defineInRange("advectionTransfer", 0.08D, 0.0D, 0.5D);

        PRESSURE_STRENGTH = BUILDER.comment("Pressure gradient response strength. Higher values equalize cells faster.")
                .defineInRange("pressureStrength", 0.18D, 0.01D, 1.0D);

        PRESSURE_ITERATIONS = BUILDER.comment("Jacobi-like pressure relaxation iterations per simulation step.")
                .defineInRange("pressureIterations", 3, 1, 10);

        INERTIA_DAMPING = BUILDER.comment("Velocity damping factor applied every step.")
                .defineInRange("inertiaDamping", 0.82D, 0.1D, 0.99D);

        PLACE_THRESHOLD = BUILDER.comment("Cell volume threshold to place/update a world water block.")
                .defineInRange("placeThreshold", 0.65D, 0.05D, 1.0D);

        REMOVE_THRESHOLD = BUILDER.comment("Cell volume threshold to remove a water block that this system previously placed.")
                .defineInRange("removeThreshold", 0.10D, 0.0D, 0.95D);

        MIN_CELL_VOLUME = BUILDER.comment("Cells below this volume are discarded.")
                .defineInRange("minCellVolume", 0.01D, 0.0001D, 0.25D);

        MAX_CELLS_PER_STEP = BUILDER.comment("Hard cap of actively processed cells per dimension step.")
                .defineInRange("maxCellsPerStep", 24000, 100, 200000);

        BUILDER.pop();
        CONFIG_SPEC = BUILDER.build();
    }

    private VolumetricFluidConfig() {
    }

    public static Values values() {
        Values raw = new Values(
                ENABLED.get(),
                TICK_INTERVAL.get(),
                PLAYER_SAMPLE_RADIUS.get(),
                PLAYER_SAMPLE_STEP.get(),
                ACTIVE_RADIUS.get(),
                PRESET.get(),
                REPLACE_VANILLA_ENGINE.get(),
                ENABLE_LAVA.get(),
                REPLACE_VANILLA_LAVA_ENGINE.get(),
                DOWNWARD_TRANSFER.get(),
                LATERAL_TRANSFER.get(),
                ADVECTION_TRANSFER.get(),
                PRESSURE_STRENGTH.get(),
                PRESSURE_ITERATIONS.get(),
                INERTIA_DAMPING.get(),
                PLACE_THRESHOLD.get(),
                REMOVE_THRESHOLD.get(),
                MIN_CELL_VOLUME.get(),
                MAX_CELLS_PER_STEP.get()
        );
        return applyPreset(raw);
    }

    public static Values defaultValues() {
        Values raw = new Values(
                ENABLED.getDefault(),
                TICK_INTERVAL.getDefault(),
                PLAYER_SAMPLE_RADIUS.getDefault(),
                PLAYER_SAMPLE_STEP.getDefault(),
                ACTIVE_RADIUS.getDefault(),
                PRESET.getDefault(),
                REPLACE_VANILLA_ENGINE.getDefault(),
                ENABLE_LAVA.getDefault(),
                REPLACE_VANILLA_LAVA_ENGINE.getDefault(),
                DOWNWARD_TRANSFER.getDefault(),
                LATERAL_TRANSFER.getDefault(),
                ADVECTION_TRANSFER.getDefault(),
                PRESSURE_STRENGTH.getDefault(),
                PRESSURE_ITERATIONS.getDefault(),
                INERTIA_DAMPING.getDefault(),
                PLACE_THRESHOLD.getDefault(),
                REMOVE_THRESHOLD.getDefault(),
                MIN_CELL_VOLUME.getDefault(),
                MAX_CELLS_PER_STEP.getDefault()
        );
        return applyPreset(raw);
    }


    private static Values applyPreset(Values raw) {
        String preset = raw.preset() == null ? "safe" : raw.preset().toLowerCase(java.util.Locale.ROOT);
        return switch (preset) {
            case "realism" -> new Values(
                    raw.enabled(),
                    1,
                    24,
                    1,
                    128,
                    "realism",
                    true,
                    true,
                    true,
                    0.45D,
                    0.18D,
                    0.14D,
                    0.28D,
                    5,
                    0.90D,
                    0.55D,
                    0.05D,
                    0.005D,
                    50000
            );
            case "custom" -> raw;
            default -> new Values(
                    raw.enabled(),
                    3,
                    12,
                    3,
                    64,
                    "safe",
                    true,
                    false,
                    false,
                    0.25D,
                    0.06D,
                    0.04D,
                    0.12D,
                    2,
                    0.88D,
                    0.72D,
                    0.08D,
                    0.015D,
                    12000
            );
        };
    }

    public record Values(
            boolean enabled,
            int tickInterval,
            int playerSampleRadius,
            int playerSampleStep,
            int activeRadius,
            String preset,
            boolean replaceVanillaWaterEngine,
            boolean enableLava,
            boolean replaceVanillaLavaEngine,
            double downwardTransfer,
            double lateralTransfer,
            double advectionTransfer,
            double pressureStrength,
            int pressureIterations,
            double inertiaDamping,
            double placeThreshold,
            double removeThreshold,
            double minCellVolume,
            int maxCellsPerStep
    ) {
    }
}
