package com.thunder.wildernessodysseyapi.chunk;

import net.neoforged.neoforge.common.ModConfigSpec;
import com.thunder.wildernessodysseyapi.io.CompressionCodec;

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
    public static final ModConfigSpec.EnumValue<CompressionCodec> COMPRESSION_CODEC;
    public static final ModConfigSpec.BooleanValue PER_DIMENSION_EXECUTORS;
    public static final ModConfigSpec.IntValue IO_THREADS;
    public static final ModConfigSpec.IntValue IO_QUEUE_SIZE;
    public static final ModConfigSpec.IntValue BUFFER_SLICE_BYTES;
    public static final ModConfigSpec.IntValue BUFFER_SLICES_PER_THREAD;

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
        COMPRESSION_LEVEL = BUILDER.comment("Compression level to use when writing chunk NBT.")
                .defineInRange("compressionLevel", 6, 1, 9);
        COMPRESSION_CODEC = BUILDER.comment("Compression codec to use for chunk payloads.")
                .defineEnum("compressionCodec", CompressionCodec.VANILLA_GZIP);
        PER_DIMENSION_EXECUTORS = BUILDER.comment("Whether to spin up one I/O executor per dimension instead of sharing a global pool.")
                .define("perDimensionExecutors", false);
        IO_THREADS = BUILDER.comment("Worker count for dedicated chunk I/O executors.")
                .defineInRange("ioThreads", Math.max(2, Runtime.getRuntime().availableProcessors() / 4), 1, 32);
        IO_QUEUE_SIZE = BUILDER.comment("Maximum queued chunk I/O tasks per executor before new submissions are rejected.")
                .defineInRange("ioQueueSize", 128, 16, 4096);
        BUFFER_SLICE_BYTES = BUILDER.comment("Default slice size (in bytes) for pooled buffers used by NBT and mesh processing.")
                .defineInRange("bufferSliceBytes", 16384, 1024, 262144);
        BUFFER_SLICES_PER_THREAD = BUILDER.comment("Maximum number of pooled slices retained per worker thread.")
                .defineInRange("bufferSlicesPerThread", 8, 1, 64);
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
                COMPRESSION_CODEC.get(),
                PER_DIMENSION_EXECUTORS.get(),
                IO_THREADS.get(),
                IO_QUEUE_SIZE.get(),
                BUFFER_SLICE_BYTES.get(),
                BUFFER_SLICES_PER_THREAD.get()
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
            CompressionCodec compressionCodec,
            boolean perDimensionExecutors,
            int ioThreads,
            int ioQueueSize,
            int bufferSliceBytes,
            int bufferSlicesPerThread
    ) {
    }
}
