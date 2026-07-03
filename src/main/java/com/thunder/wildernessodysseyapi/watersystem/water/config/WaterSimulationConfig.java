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
    public static final ModConfigSpec.BooleanValue CONVERT_SEEDED_WORLD_WATER_TO_WILDERNESS;
    public static final ModConfigSpec.BooleanValue ENABLE_AUTOMATIC_WATER_MIGRATION;
    public static final ModConfigSpec.IntValue WORLD_SEED_MAX_COLUMN_DEPTH;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_COLUMNS_PER_TICK;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_CONVERTED_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_MAX_QUEUED_CHUNKS;
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
        CONVERT_SEEDED_WORLD_WATER_TO_WILDERNESS = builder
                .comment("Replace imported plain vanilla water blocks with namespaced Wilderness water during manual or budgeted automatic seeding.")
                .define("convertSeededWorldWaterToWilderness", true);
        ENABLE_AUTOMATIC_WATER_MIGRATION = builder
                .comment("Queue loaded chunks for deferred vanilla-to-Wilderness water migration after the world is running.")
                .define("enableAutomaticWaterMigration", true);
        WORLD_SEED_MAX_COLUMN_DEPTH = builder
                .comment("Maximum water depth imported per X/Z column during chunk-load seeding.")
                .defineInRange("worldSeedMaxColumnDepth", 16, 1, 64);
        AUTOMATIC_MIGRATION_CHUNKS_PER_TICK = builder
                .comment("Maximum queued chunks touched by automatic water migration per server tick.")
                .defineInRange("automaticMigrationChunksPerTick", 1, 1, 16);
        AUTOMATIC_MIGRATION_COLUMNS_PER_TICK = builder
                .comment("Maximum X/Z water columns scanned by automatic water migration per server tick.")
                .defineInRange("automaticMigrationColumnsPerTick", 64, 1, 1024);
        AUTOMATIC_MIGRATION_CONVERTED_BLOCKS_PER_TICK = builder
                .comment("Maximum vanilla water blocks converted to Wilderness water by automatic migration per server tick.")
                .defineInRange("automaticMigrationConvertedBlocksPerTick", 256, 1, 4096);
        AUTOMATIC_MIGRATION_MAX_QUEUED_CHUNKS = builder
                .comment("Maximum loaded chunks waiting for automatic water migration.")
                .defineInRange("automaticMigrationMaxQueuedChunks", 4096, 128, 65536);
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

    /** Returns whether manual and budgeted automatic seeding may migrate plain vanilla water to Wilderness blocks. */
    public static boolean convertSeededWorldWaterToWilderness() {
        return CONVERT_SEEDED_WORLD_WATER_TO_WILDERNESS.get();
    }

    /** Returns whether loaded chunks should be queued for budgeted automatic water migration. */
    public static boolean automaticWaterMigrationEnabled() {
        return ENABLE_AUTOMATIC_WATER_MIGRATION.get();
    }

    /** Returns the number of queued chunks automatic migration may touch per server tick. */
    public static int automaticMigrationChunksPerTick() {
        return AUTOMATIC_MIGRATION_CHUNKS_PER_TICK.get();
    }

    /** Returns the number of X/Z columns automatic migration may scan per server tick. */
    public static int automaticMigrationColumnsPerTick() {
        return AUTOMATIC_MIGRATION_COLUMNS_PER_TICK.get();
    }

    /** Returns the number of water blocks automatic migration may convert per server tick. */
    public static int automaticMigrationConvertedBlocksPerTick() {
        return AUTOMATIC_MIGRATION_CONVERTED_BLOCKS_PER_TICK.get();
    }

    /** Returns the maximum queued chunk positions retained by automatic migration. */
    public static int automaticMigrationMaxQueuedChunks() {
        return AUTOMATIC_MIGRATION_MAX_QUEUED_CHUNKS.get();
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
