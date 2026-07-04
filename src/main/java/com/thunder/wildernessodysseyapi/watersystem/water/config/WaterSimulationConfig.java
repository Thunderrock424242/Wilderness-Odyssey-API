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
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_PLAYER_CHUNK_RADIUS;
    public static final ModConfigSpec.BooleanValue AUTOMATIC_MIGRATION_FOLLOW_PLAYER_VIEW_DISTANCE;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_VIEW_DISTANCE_PADDING_CHUNKS;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_PLAYER_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_VISIBLE_CHUNK_WATER_FINALIZATION;
    public static final ModConfigSpec.IntValue VISIBLE_FINALIZATION_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue VISIBLE_FINALIZATION_COLUMNS_PER_TICK;
    public static final ModConfigSpec.IntValue VISIBLE_FINALIZATION_CONVERTED_BLOCKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue SEED_ONLY_PLAIN_WATER_BLOCKS;
    public static final ModConfigSpec.BooleanValue IMPORT_WATERLOGGED_HOST_WATER;
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
                .comment("Maximum queued chunks touched by automatic water migration per server tick. Visible player chunks are prioritized before older background work.")
                .defineInRange("automaticMigrationChunksPerTick", 4, 1, 16);
        AUTOMATIC_MIGRATION_COLUMNS_PER_TICK = builder
                .comment("Maximum X/Z water columns scanned by automatic water migration per server tick. Canonical authority import uses this budget even when block conversion is exhausted.")
                .defineInRange("automaticMigrationColumnsPerTick", 512, 1, 2048);
        AUTOMATIC_MIGRATION_CONVERTED_BLOCKS_PER_TICK = builder
                .comment("Maximum vanilla water blocks converted to Wilderness water by automatic migration per server tick.")
                .defineInRange("automaticMigrationConvertedBlocksPerTick", 1024, 1, 4096);
        AUTOMATIC_MIGRATION_MAX_QUEUED_CHUNKS = builder
                .comment("Maximum loaded chunks waiting for automatic water migration. Kept above a large visible-radius square so player-priority work does not evict nearby ocean chunks.")
                .defineInRange("automaticMigrationMaxQueuedChunks", 8192, 128, 65536);
        AUTOMATIC_MIGRATION_PLAYER_CHUNK_RADIUS = builder
                .comment("Minimum loaded chunk radius around each player that automatic water migration keeps prioritized. Clamped to server view distance; set 0 to disable player-centered priority scans.")
                .defineInRange("automaticMigrationPlayerChunkRadius", 16, 0, 32);
        AUTOMATIC_MIGRATION_FOLLOW_PLAYER_VIEW_DISTANCE = builder
                .comment("Expand player-priority water migration up to each player's requested render distance, clamped to chunks the server has already loaded.")
                .define("automaticMigrationFollowPlayerViewDistance", true);
        AUTOMATIC_MIGRATION_VIEW_DISTANCE_PADDING_CHUNKS = builder
                .comment("Extra loaded chunk padding around the player render-distance migration radius. Helps authority arrive before the visible edge scrolls into view.")
                .defineInRange("automaticMigrationViewDistancePaddingChunks", 1, 0, 4);
        AUTOMATIC_MIGRATION_PLAYER_SCAN_INTERVAL_TICKS = builder
                .comment("How often automatic water migration rescans already-loaded chunks around players.")
                .defineInRange("automaticMigrationPlayerScanIntervalTicks", 20, 10, 600);
        ENABLE_VISIBLE_CHUNK_WATER_FINALIZATION = builder
                .comment("Finalize generated plain water into Wilderness water when a loaded chunk starts being watched by a player. Work remains bounded per tick so world creation and chunk sending do not stall.")
                .define("enableVisibleChunkWaterFinalization", true);
        VISIBLE_FINALIZATION_CHUNKS_PER_TICK = builder
                .comment("Maximum player-visible chunks that may receive immediate water finalization in one server tick.")
                .defineInRange("visibleFinalizationChunksPerTick", 2, 1, 16);
        VISIBLE_FINALIZATION_COLUMNS_PER_TICK = builder
                .comment("Maximum X/Z water columns finalized from player-visible chunks in one server tick.")
                .defineInRange("visibleFinalizationColumnsPerTick", 512, 1, 2048);
        VISIBLE_FINALIZATION_CONVERTED_BLOCKS_PER_TICK = builder
                .comment("Maximum plain minecraft:water blocks rewritten to Wilderness water while finalizing player-visible chunks in one server tick.")
                .defineInRange("visibleFinalizationConvertedBlocksPerTick", 1024, 1, 4096);
        SEED_ONLY_PLAIN_WATER_BLOCKS = builder
                .comment("Restrict normal world seeding to plain water blocks. Non-plain tagged water can still import as hosted water when importWaterloggedHostWater is enabled.")
                .define("seedOnlyPlainWaterBlocks", true);
        IMPORT_WATERLOGGED_HOST_WATER = builder
                .comment("Import water inside waterlogged host blocks into canonical volume without replacing the host block.")
                .define("importWaterloggedHostWater", true);
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
        int configured = AUTOMATIC_MIGRATION_CHUNKS_PER_TICK.get();
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(4, configured) : configured;
    }

    /** Returns the number of X/Z columns automatic migration may scan per server tick. */
    public static int automaticMigrationColumnsPerTick() {
        int configured = AUTOMATIC_MIGRATION_COLUMNS_PER_TICK.get();
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(512, configured) : configured;
    }

    /** Returns the number of water blocks automatic migration may convert per server tick. */
    public static int automaticMigrationConvertedBlocksPerTick() {
        int configured = AUTOMATIC_MIGRATION_CONVERTED_BLOCKS_PER_TICK.get();
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(1024, configured) : configured;
    }

    /** Returns the maximum queued chunk positions retained by automatic migration. */
    public static int automaticMigrationMaxQueuedChunks() {
        int configured = AUTOMATIC_MIGRATION_MAX_QUEUED_CHUNKS.get();
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(8192, configured) : configured;
    }

    /** Returns the loaded chunk radius that receives player-centered migration priority. */
    public static int automaticMigrationPlayerChunkRadius() {
        int configured = AUTOMATIC_MIGRATION_PLAYER_CHUNK_RADIUS.get();
        if (configured <= 0) {
            return 0;
        }
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(16, configured) : configured;
    }

    /** Returns whether priority migration should follow each player's requested view distance. */
    public static boolean automaticMigrationFollowsPlayerViewDistance() {
        return AUTOMATIC_MIGRATION_FOLLOW_PLAYER_VIEW_DISTANCE.get();
    }

    /** Returns the small loaded-chunk padding added to the visible authority radius. */
    public static int automaticMigrationViewDistancePaddingChunks() {
        return AUTOMATIC_MIGRATION_VIEW_DISTANCE_PADDING_CHUNKS.get();
    }

    /** Returns how often loaded chunks around players are rescanned for automatic migration. */
    public static int automaticMigrationPlayerScanIntervalTicks() {
        int configured = AUTOMATIC_MIGRATION_PLAYER_SCAN_INTERVAL_TICKS.get();
        return automaticMigrationFollowsPlayerViewDistance() ? Math.min(20, configured) : configured;
    }

    /** Returns whether watched chunks should get a bounded pre-visibility water finalization pass. */
    public static boolean visibleChunkWaterFinalizationEnabled() {
        return ENABLE_VISIBLE_CHUNK_WATER_FINALIZATION.get();
    }

    /** Returns how many watched chunks can be finalized in one server tick. */
    public static int visibleFinalizationChunksPerTick() {
        return effectiveVisibleFinalizationChunksPerTick();
    }

    /** Returns how many watched-chunk columns can be finalized in one server tick. */
    public static int visibleFinalizationColumnsPerTick() {
        int configured = VISIBLE_FINALIZATION_COLUMNS_PER_TICK.get();
        int fullChunkColumns = effectiveVisibleFinalizationChunksPerTick() * 16 * 16;
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(fullChunkColumns, configured) : configured;
    }

    /** Returns how many plain water blocks can be rewritten during watched-chunk finalization in one server tick. */
    public static int visibleFinalizationConvertedBlocksPerTick() {
        int configured = VISIBLE_FINALIZATION_CONVERTED_BLOCKS_PER_TICK.get();
        int fullChunkWaterBlocks = effectiveVisibleFinalizationChunksPerTick() * 16 * 16 * worldSeedMaxColumnDepth();
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(fullChunkWaterBlocks, configured) : configured;
    }

    private static int effectiveVisibleFinalizationChunksPerTick() {
        int configured = VISIBLE_FINALIZATION_CHUNKS_PER_TICK.get();
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(2, configured) : configured;
    }

    /** Returns whether waterlogged host blocks contribute hosted canonical water cells. */
    public static boolean importWaterloggedHostWater() {
        return IMPORT_WATERLOGGED_HOST_WATER.get();
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
