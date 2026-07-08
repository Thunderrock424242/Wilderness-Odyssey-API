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

    public static final ModConfigSpec.BooleanValue ENABLE_WILDERNESS_ODYSSEY_WATER;
    public static final ModConfigSpec.BooleanValue ENABLE_CANONICAL_WORLD_SEEDING;
    public static final ModConfigSpec.BooleanValue CONVERT_SEEDED_WORLD_WATER_TO_WILDERNESS;
    public static final ModConfigSpec.BooleanValue ENABLE_AUTOMATIC_WATER_MIGRATION;
    public static final ModConfigSpec.IntValue WORLD_SEED_MAX_COLUMN_DEPTH;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_COLUMNS_PER_TICK;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_CONVERTED_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_MAX_QUEUED_CHUNKS;
    public static final ModConfigSpec.BooleanValue ENABLE_PLAYER_CENTERED_WATER_MIGRATION_SCAN;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_PLAYER_CHUNK_RADIUS;
    public static final ModConfigSpec.BooleanValue AUTOMATIC_MIGRATION_FOLLOW_PLAYER_VIEW_DISTANCE;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_VIEW_DISTANCE_PADDING_CHUNKS;
    public static final ModConfigSpec.IntValue AUTOMATIC_MIGRATION_PLAYER_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_WATER_PRE_FINALIZATION;
    public static final ModConfigSpec.IntValue SPAWN_WATER_PRE_FINALIZATION_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue SPAWN_WATER_PRE_FINALIZATION_MAX_CHUNKS;
    public static final ModConfigSpec.IntValue SPAWN_WATER_PRE_FINALIZATION_TIMEOUT_MS;
    public static final ModConfigSpec.BooleanValue ENABLE_VISIBLE_CHUNK_WATER_FINALIZATION;
    public static final ModConfigSpec.IntValue VISIBLE_FINALIZATION_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue VISIBLE_FINALIZATION_COLUMNS_PER_TICK;
    public static final ModConfigSpec.IntValue VISIBLE_FINALIZATION_CONVERTED_BLOCKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue SEED_ONLY_PLAIN_WATER_BLOCKS;
    public static final ModConfigSpec.BooleanValue IMPORT_WATERLOGGED_HOST_WATER;
    public static final ModConfigSpec.IntValue COVERED_WATER_SURFACE_SCAN_DEPTH;
    public static final ModConfigSpec.IntValue LOCAL_FLOW_CELLS_PER_TICK;
    public static final ModConfigSpec.DoubleValue LOCAL_FLOW_SLEEP_SPEED;
    public static final ModConfigSpec.IntValue LARGE_BODY_CACHE_MAX_COLUMNS;
    public static final ModConfigSpec.IntValue WATER_BODY_UPDATES_PER_TICK;
    public static final ModConfigSpec.IntValue LOCAL_WATER_NETWORK_EVENTS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVER_SPH_LOCAL_SIMULATION;
    public static final ModConfigSpec.IntValue SERVER_SPH_MAX_ACTIVE_BODIES;
    public static final ModConfigSpec.IntValue SERVER_SPH_MAX_PARTICLES_PER_BODY;
    public static final ModConfigSpec.IntValue SERVER_SPH_PARTICLE_TICK_BUDGET;
    public static final ModConfigSpec.IntValue DEBUG_COMMAND_MAX_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Server-side water simulation and compatibility options.")
                .push("water_simulation");

        ENABLE_WILDERNESS_ODYSSEY_WATER = builder
                .comment("Master server-config switch for Wilderness Odyssey water authority, migration, local flow, SPH gameplay water, and replacement rendering. The per-world gamerule enableWildernessOdysseyWater can still disable it at runtime.")
                .define("enableWildernessOdysseyWater", true);
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
        ENABLE_PLAYER_CENTERED_WATER_MIGRATION_SCAN = builder
                .comment("Legacy safety net that periodically scans loaded chunks around moving players. Disabled by default so player movement does not trigger mass water conversion work.")
                .define("enablePlayerCenteredWaterMigrationScan", false);
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
        ENABLE_SPAWN_WATER_PRE_FINALIZATION = builder
                .comment("During initial starter-bunker world creation, synchronously finalize nearby generated water into Wilderness water before the player sees spawn. This can make Create World / Joining World slower, but prevents visible live migration around the bunker.")
                .define("enableSpawnWaterPreFinalization", true);
        SPAWN_WATER_PRE_FINALIZATION_RADIUS_CHUNKS = builder
                .comment("Chunk radius around the starter bunker that may be synchronously water-finalized during world creation.")
                .defineInRange("spawnWaterPreFinalizationRadiusChunks", 5, 1, 12);
        SPAWN_WATER_PRE_FINALIZATION_MAX_CHUNKS = builder
                .comment("Hard cap for starter-bunker water pre-finalization chunks. Closest chunks are processed first so this safely bounds first-load cost.")
                .defineInRange("spawnWaterPreFinalizationMaxChunks", 121, 1, 625);
        SPAWN_WATER_PRE_FINALIZATION_TIMEOUT_MS = builder
                .comment("Soft timeout for starter-bunker water pre-finalization. Zero disables the timeout; unfinished chunks remain in the normal priority migration queue.")
                .defineInRange("spawnWaterPreFinalizationTimeoutMs", 10_000, 0, 120_000);
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
        LOCAL_FLOW_CELLS_PER_TICK = builder
                .comment("Maximum active detailed water cells processed per server tick. Sleeping cells are skipped until nearby water changes.")
                .defineInRange("localFlowCellsPerTick", 128, 16, 1024);
        LOCAL_FLOW_SLEEP_SPEED = builder
                .comment("Velocity below this value lets a detailed local water cell sleep when it cannot flow.")
                .defineInRange("localFlowSleepSpeed", 0.025, 0.001, 0.20);
        LARGE_BODY_CACHE_MAX_COLUMNS = builder
                .comment("Maximum derived large-water-body columns persisted per dimension. This is a safe per-world cache, not a second authority.")
                .defineInRange("largeBodyCacheMaxColumns", 16384, 1024, 262144);
        WATER_BODY_UPDATES_PER_TICK = builder
                .comment("Maximum high-level water-body or shoreline-grid regions updated per dimension tick. Large bodies stay as cached metadata; this cap prevents player oceans from becoming full-cell simulation work.")
                .defineInRange("waterBodyUpdatesPerTick", 6, 1, 64);
        LOCAL_WATER_NETWORK_EVENTS_PER_TICK = builder
                .comment("Maximum compact local water visual events, such as SPH splash or shore-wash events, sent per dimension tick. Events are synced instead of individual particles.")
                .defineInRange("localWaterNetworkEventsPerTick", 32, 0, 512);
        ENABLE_SERVER_SPH_LOCAL_SIMULATION = builder
                .comment("Allow tiny gameplay-critical SPH bodies for active local water such as waterfalls. Oceans, lakes, rivers, and stored world water never use SPH.")
                .define("enableServerSphLocalSimulation", true);
        SERVER_SPH_MAX_ACTIVE_BODIES = builder
                .comment("Maximum server-owned gameplay SPH bodies per dimension. Keep this small; visual-only splashes are client events.")
                .defineInRange("serverSphMaxActiveBodies", 8, 0, 24);
        SERVER_SPH_MAX_PARTICLES_PER_BODY = builder
                .comment("Maximum particles in one server-owned gameplay SPH body. Larger values are more expensive and should not be used for permanent water.")
                .defineInRange("serverSphMaxParticlesPerBody", 192, 16, 720);
        SERVER_SPH_PARTICLE_TICK_BUDGET = builder
                .comment("Maximum server-owned SPH particles advanced per dimension tick. Bodies past the budget wait for a later tick.")
                .defineInRange("serverSphParticleTickBudget", 900, 0, 4096);
        DEBUG_COMMAND_MAX_RADIUS = builder
                .comment("Maximum block radius accepted by /wowater summary and /wowater repair.")
                .defineInRange("debugCommandMaxRadius", 16, 1, 64);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private WaterSimulationConfig() {
    }

    /** Returns whether the pack-level Wilderness water master switch is enabled. */
    public static boolean wildernessWaterEnabled() {
        return ENABLE_WILDERNESS_ODYSSEY_WATER.get();
    }

    /** Returns the bounded import depth used by automatic and command seeding. */
    public static int worldSeedMaxColumnDepth() {
        return WORLD_SEED_MAX_COLUMN_DEPTH.get();
    }

    /** Returns whether manual and budgeted automatic seeding may migrate plain vanilla water to Wilderness blocks. */
    public static boolean convertSeededWorldWaterToWilderness() {
        return wildernessWaterEnabled() && CONVERT_SEEDED_WORLD_WATER_TO_WILDERNESS.get();
    }

    /** Returns whether loaded chunks should be queued for budgeted automatic water migration. */
    public static boolean automaticWaterMigrationEnabled() {
        return wildernessWaterEnabled() && ENABLE_AUTOMATIC_WATER_MIGRATION.get();
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
        if (!playerCenteredWaterMigrationScanEnabled()) {
            return 0;
        }
        int configured = AUTOMATIC_MIGRATION_PLAYER_CHUNK_RADIUS.get();
        if (configured <= 0) {
            return 0;
        }
        return automaticMigrationFollowsPlayerViewDistance() ? Math.max(16, configured) : configured;
    }

    /** Returns whether movement-centered loaded-chunk scans are enabled. */
    public static boolean playerCenteredWaterMigrationScanEnabled() {
        return wildernessWaterEnabled() && ENABLE_PLAYER_CENTERED_WATER_MIGRATION_SCAN.get();
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

    /** Returns whether the starter-bunker area should be finalized while the world is still loading. */
    public static boolean spawnWaterPreFinalizationEnabled() {
        return wildernessWaterEnabled()
                && ENABLE_SPAWN_WATER_PRE_FINALIZATION.get()
                && ENABLE_CANONICAL_WORLD_SEEDING.get()
                && convertSeededWorldWaterToWilderness();
    }

    /** Returns the configured starter-bunker pre-finalization chunk radius. */
    public static int spawnWaterPreFinalizationRadiusChunks() {
        return SPAWN_WATER_PRE_FINALIZATION_RADIUS_CHUNKS.get();
    }

    /** Returns the maximum starter-bunker chunks that may be finalized in one blocking load pass. */
    public static int spawnWaterPreFinalizationMaxChunks() {
        return SPAWN_WATER_PRE_FINALIZATION_MAX_CHUNKS.get();
    }

    /** Returns the soft timeout for starter-bunker pre-finalization. Zero means no timeout. */
    public static int spawnWaterPreFinalizationTimeoutMs() {
        return SPAWN_WATER_PRE_FINALIZATION_TIMEOUT_MS.get();
    }

    /** Returns whether watched chunks should get a bounded pre-visibility water finalization pass. */
    public static boolean visibleChunkWaterFinalizationEnabled() {
        return wildernessWaterEnabled() && ENABLE_VISIBLE_CHUNK_WATER_FINALIZATION.get();
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
        return wildernessWaterEnabled() && IMPORT_WATERLOGGED_HOST_WATER.get();
    }

    /** Returns the bounded covered-water surface scan depth. */
    public static int coveredWaterSurfaceScanDepth() {
        return COVERED_WATER_SURFACE_SCAN_DEPTH.get();
    }

    /** Returns the active local-cell flow budget for one server tick. */
    public static int localFlowCellsPerTick() {
        return wildernessWaterEnabled() ? LOCAL_FLOW_CELLS_PER_TICK.get() : 0;
    }

    /** Returns the speed threshold under which immobile local cells can sleep. */
    public static float localFlowSleepSpeed() {
        return LOCAL_FLOW_SLEEP_SPEED.get().floatValue();
    }

    /** Returns the maximum persisted large-body column cache entries per dimension. */
    public static int largeBodyCacheMaxColumns() {
        return LARGE_BODY_CACHE_MAX_COLUMNS.get();
    }

    /** Returns how many high-level water-body regions may update in one dimension tick. */
    public static int waterBodyUpdatesPerTick() {
        return wildernessWaterEnabled() ? WATER_BODY_UPDATES_PER_TICK.get() : 0;
    }

    /** Returns how many compact local water network events may be sent in one dimension tick. */
    public static int localWaterNetworkEventsPerTick() {
        return wildernessWaterEnabled() ? LOCAL_WATER_NETWORK_EVENTS_PER_TICK.get() : 0;
    }

    /** Returns whether server-owned local SPH is allowed for gameplay-critical active water. */
    public static boolean serverSphLocalSimulationEnabled() {
        return wildernessWaterEnabled()
                && ENABLE_SERVER_SPH_LOCAL_SIMULATION.get()
                && SERVER_SPH_MAX_ACTIVE_BODIES.get() > 0
                && SERVER_SPH_PARTICLE_TICK_BUDGET.get() > 0;
    }

    /** Returns the maximum number of server gameplay SPH bodies per dimension. */
    public static int serverSphMaxActiveBodies() {
        return serverSphLocalSimulationEnabled() ? SERVER_SPH_MAX_ACTIVE_BODIES.get() : 0;
    }

    /** Returns the maximum particles allowed in one server gameplay SPH body. */
    public static int serverSphMaxParticlesPerBody() {
        return serverSphLocalSimulationEnabled() ? SERVER_SPH_MAX_PARTICLES_PER_BODY.get() : 0;
    }

    /** Returns the per-dimension server SPH particle simulation budget. */
    public static int serverSphParticleTickBudget() {
        return serverSphLocalSimulationEnabled() ? SERVER_SPH_PARTICLE_TICK_BUDGET.get() : 0;
    }

    /** Returns the maximum debug radius allowed by server config. */
    public static int debugCommandMaxRadius() {
        return DEBUG_COMMAND_MAX_RADIUS.get();
    }
}
