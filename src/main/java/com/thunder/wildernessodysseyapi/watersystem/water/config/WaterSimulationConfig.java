package com.thunder.wildernessodysseyapi.watersystem.water.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defines server-side water simulation and compatibility controls.
 *
 * <p>These values affect persistent canonical water data, so they live in a
 * server config instead of the client rendering config. Renderers should react
 * to synchronized canonical state rather than owning these simulation choices.</p>
 */
public final class WaterSimulationConfig {

    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_CANONICAL_WORLD_SEEDING;
    public static final ModConfigSpec.IntValue WORLD_SEED_MAX_COLUMN_DEPTH;
    public static final ModConfigSpec.BooleanValue SEED_ONLY_PLAIN_WATER_BLOCKS;
    public static final ModConfigSpec.IntValue COVERED_WATER_SURFACE_SCAN_DEPTH;
    public static final ModConfigSpec.IntValue DEBUG_COMMAND_MAX_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Server-side water simulation and compatibility options.")
                .push("water_simulation");

        ENABLE_CANONICAL_WORLD_SEEDING = builder
                .comment("Import exposed vanilla ocean, river, and lake columns into canonical water when chunks load.")
                .define("enableCanonicalWorldSeeding", true);
        WORLD_SEED_MAX_COLUMN_DEPTH = builder
                .comment("Maximum water depth imported per X/Z column during chunk-load seeding.")
                .defineInRange("worldSeedMaxColumnDepth", 16, 1, 64);
        SEED_ONLY_PLAIN_WATER_BLOCKS = builder
                .comment("Only seed plain water blocks. Keep waterlogged host blocks owned by vanilla/modded blocks.")
                .define("seedOnlyPlainWaterBlocks", true);
        COVERED_WATER_SURFACE_SCAN_DEPTH = builder
                .comment("How far below the motion-blocking surface to search for water under ice or thin cover.")
                .defineInRange("coveredWaterSurfaceScanDepth", 5, 0, 16);
        DEBUG_COMMAND_MAX_RADIUS = builder
                .comment("Maximum block radius accepted by /wowater summary and /wowater repair.")
                .defineInRange("debugCommandMaxRadius", 16, 1, 64);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private WaterSimulationConfig() {
    }

    /** Returns the bounded import depth used by automatic and command seeding. */
    public static int worldSeedMaxColumnDepth() {
        return WORLD_SEED_MAX_COLUMN_DEPTH.get();
    }

    /** Returns the bounded covered-water surface scan depth. */
    public static int coveredWaterSurfaceScanDepth() {
        return COVERED_WATER_SURFACE_SCAN_DEPTH.get();
    }

    /** Returns the maximum debug radius allowed by server config. */
    public static int debugCommandMaxRadius() {
        return DEBUG_COMMAND_MAX_RADIUS.get();
    }
}
