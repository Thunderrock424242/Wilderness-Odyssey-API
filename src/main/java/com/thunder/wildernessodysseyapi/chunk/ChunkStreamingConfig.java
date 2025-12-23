package com.thunder.wildernessodysseyapi.chunk;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config entries for the chunk streaming pipeline and caches.
 */
public final class ChunkStreamingConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue HOT_CACHE_LIMIT;
    public static final ModConfigSpec.IntValue WARM_CACHE_LIMIT;
    public static final ModConfigSpec.IntValue SAVE_DEBOUNCE_TICKS;
    public static final ModConfigSpec.IntValue PLAYER_TICKET_TTL;
    public static final ModConfigSpec.IntValue ENTITY_TICKET_TTL;
    public static final ModConfigSpec.IntValue REDSTONE_TICKET_TTL;
    public static final ModConfigSpec.IntValue STRUCTURE_TICKET_TTL;
    public static final ModConfigSpec.IntValue MAX_PARALLEL_IO;
    public static final ModConfigSpec.IntValue COMPRESSION_LEVEL;
    public static final ModConfigSpec.IntValue DELTA_CHANGE_BUDGET;
    public static final ModConfigSpec.IntValue LIGHT_COMPRESSION_LEVEL;
    public static final ModConfigSpec.IntValue WRITE_FLUSH_INTERVAL_TICKS;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("chunkStreaming");
        ENABLED = BUILDER.comment("Master toggle for the chunk streaming pipeline.")
                .define("enabled", true);
        HOT_CACHE_LIMIT = BUILDER.comment("Maximum number of hot (ticking/rendered) chunks to retain before demoting to warm cache.")
                .defineInRange("hotCacheLimit", 128, 16, 4096);
        WARM_CACHE_LIMIT = BUILDER.comment("Maximum number of warm cached chunks to keep around without ticking.")
                .defineInRange("warmCacheLimit", 256, 32, 8192);
        SAVE_DEBOUNCE_TICKS = BUILDER.comment("Ticks to wait before flushing a chunk save so rapid edits can be coalesced.")
                .defineInRange("saveDebounceTicks", 20, 0, 200);
        PLAYER_TICKET_TTL = BUILDER.comment("How long (in ticks) a player ticket should keep a chunk alive after the player leaves range.")
                .defineInRange("playerTicketTtl", 200, 20, 1200);
        ENTITY_TICKET_TTL = BUILDER.comment("How long (in ticks) an entity ticket should keep a chunk alive after the entity departs.")
                .defineInRange("entityTicketTtl", 160, 20, 1200);
        REDSTONE_TICKET_TTL = BUILDER.comment("How long (in ticks) redstone activity should keep a chunk loaded.")
                .defineInRange("redstoneTicketTtl", 80, 10, 600);
        STRUCTURE_TICKET_TTL = BUILDER.comment("How long (in ticks) structure generation tickets remain valid.")
                .defineInRange("structureTicketTtl", 400, 40, 2400);
        MAX_PARALLEL_IO = BUILDER.comment("Maximum parallel chunk I/O operations submitted at once.")
                .defineInRange("maxParallelIo", 4, 1, 64);
        COMPRESSION_LEVEL = BUILDER.comment("GZIP compression level to use when writing chunk NBT.")
                .defineInRange("compressionLevel", 6, 1, 9);
        DELTA_CHANGE_BUDGET = BUILDER.comment("Number of chunk changes that can be streamed as deltas before forcing a full sync.")
                .defineInRange("deltaChangeBudget", 256, 32, 4096);
        LIGHT_COMPRESSION_LEVEL = BUILDER.comment("Compression level to use when sending individual light bands to players.")
                .defineInRange("lightCompressionLevel", 6, 1, 9);
        WRITE_FLUSH_INTERVAL_TICKS = BUILDER.comment("Interval (in ticks) to batch dirty chunk writes using the scheduled flush task.")
                .defineInRange("writeFlushIntervalTicks", 20, 1, 200);
        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    private ChunkStreamingConfig() {
    }

    public static ChunkConfigValues values() {
        return new ChunkConfigValues(
                ENABLED.get(),
                HOT_CACHE_LIMIT.get(),
                WARM_CACHE_LIMIT.get(),
                SAVE_DEBOUNCE_TICKS.get(),
                PLAYER_TICKET_TTL.get(),
                ENTITY_TICKET_TTL.get(),
                REDSTONE_TICKET_TTL.get(),
                STRUCTURE_TICKET_TTL.get(),
                MAX_PARALLEL_IO.get(),
                COMPRESSION_LEVEL.get(),
                DELTA_CHANGE_BUDGET.get(),
                LIGHT_COMPRESSION_LEVEL.get()
                WRITE_FLUSH_INTERVAL_TICKS.get()
        );
    }

    public record ChunkConfigValues(
            boolean enabled,
            int hotCacheLimit,
            int warmCacheLimit,
            int saveDebounceTicks,
            int playerTicketTtl,
            int entityTicketTtl,
            int redstoneTicketTtl,
            int structureTicketTtl,
            int maxParallelIo,
            int compressionLevel,
            int deltaChangeBudget,
            int lightCompressionLevel
            int writeFlushIntervalTicks
    ) {
    }
}
