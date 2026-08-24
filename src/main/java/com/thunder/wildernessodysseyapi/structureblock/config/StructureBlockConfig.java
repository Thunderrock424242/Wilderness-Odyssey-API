package com.thunder.wildernessodysseyapi.structureblock.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for expanded structure block behavior.
 */
public class StructureBlockConfig {

    public static StructureBlockConfig CONFIG;
    public static ModConfigSpec CONFIG_SPEC;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the structure-block category in the unified server config. */
    public static void define(ModConfigSpec.Builder builder) {
        CONFIG = new StructureBlockConfig(builder);
    }

    private final ModConfigSpec.IntValue maxStructureSize;
    private final ModConfigSpec.IntValue maxStructureOffset;
    private final ModConfigSpec.IntValue defaultDetectionRadius;
    private final ModConfigSpec.IntValue cornerSearchRadius;
    private final ModConfigSpec.IntValue nbtParseTimeoutMs;
    private final ModConfigSpec.IntValue chunkWarmupBudget;
    private final ModConfigSpec.IntValue maxLoadedChunksPerOperation;
    private final ModConfigSpec.IntValue structureCompressionLevel;
    private final ModConfigSpec.LongValue maxOperationVolume;
    private final ModConfigSpec.IntValue maxSynchronousScanBlocks;
    private final ModConfigSpec.LongValue maxStructureNbtBytes;

    StructureBlockConfig(ModConfigSpec.Builder builder) {
        builder.push("structure_blocks");

        maxStructureSize = builder.comment("Maximum allowed structure size per axis when saving with structure blocks.")
                .translation("wildernessodysseyapi.structure_blocks.max_structure_size")
                .defineInRange("maxStructureSize", 512, 1, 4096);

        maxStructureOffset = builder.comment("Maximum offset permitted between the structure block and the saved area.")
                .translation("wildernessodysseyapi.structure_blocks.max_structure_offset")
                .defineInRange("maxStructureOffset", 512, 1, 4096);

        defaultDetectionRadius = builder.comment(
                        "Default scan radius used by Detect when no bounds are present. Cannot exceed the offset limit.")
                .translation("wildernessodysseyapi.structure_blocks.default_detection_radius")
                .defineInRange("defaultDetectionRadius", 64, 0, 2048);

        cornerSearchRadius = builder.comment(
                        "Maximum distance to search for matching CORNER blocks. Cannot exceed the offset limit.")
                .translation("wildernessodysseyapi.structure_blocks.corner_search_radius")
                .defineInRange("cornerSearchRadius", 512, 0, 4096);

        nbtParseTimeoutMs = builder.comment("Timeout (milliseconds) allowed for parsing SNBT/NBT structure files.")
                .translation("wildernessodysseyapi.structure_blocks.nbt_parse_timeout_ms")
                .defineInRange("nbtParseTimeoutMs", 30_000, 1_000, 120_000);

        chunkWarmupBudget = builder.comment(
                        "Deprecated compatibility setting. Structure-block scans never force chunks to load; this"
                                + " value is retained so existing server configs remain readable.")
                .translation("wildernessodysseyapi.structure_blocks.chunk_warmup_budget")
                .defineInRange("chunkWarmupBudget", 256, 0, 4096);

        maxLoadedChunksPerOperation = builder.comment(
                        "Maximum number of already-loaded chunks one structure-block request may inspect."
                                + " No request will force an unloaded chunk to load.")
                .translation("wildernessodysseyapi.structure_blocks.max_loaded_chunks_per_operation")
                .defineInRange("maxLoadedChunksPerOperation", 256, 1, 1024);

        maxOperationVolume = builder.comment(
                        "Maximum total blocks in one Save, Load, or Detect request. Existing structure files and"
                                + " stored dimensions are not modified. Hard maximum: 16777216 blocks.")
                .translation("wildernessodysseyapi.structure_blocks.max_operation_volume")
                .defineInRange("maxOperationVolume", 4_194_304L, 1L, 16_777_216L);

        maxSynchronousScanBlocks = builder.comment(
                        "Maximum blocks the synchronous Detect or auto-fit pass may inspect before it stops."
                                + " This is lower than the operation volume to protect the server thread.")
                .translation("wildernessodysseyapi.structure_blocks.max_synchronous_scan_blocks")
                .defineInRange("maxSynchronousScanBlocks", 1_048_576, 1, 4_194_304);

        maxStructureNbtBytes = builder.comment(
                        "Maximum compressed file size and decoded NBT accounting quota for structure post-processing."
                                + " Files above this limit remain saved but are not filtered or recompressed.")
                .translation("wildernessodysseyapi.structure_blocks.max_structure_nbt_bytes")
                .defineInRange("maxStructureNbtBytes", 16_777_216L, 1_048_576L, 67_108_864L);

        structureCompressionLevel = builder.comment(
                        "GZIP compression level used when writing saved structures (1 = fastest, 9 = smallest)."
                                + " Set to 0 to skip post-save recompression.")
                .translation("wildernessodysseyapi.structure_blocks.structure_compression_level")
                .defineInRange("structureCompressionLevel", 6, 0, 9);

        builder.pop();
    }

    public int maxStructureSize() {
        return maxStructureSize.get();
    }

    public int maxStructureOffset() {
        return maxStructureOffset.get();
    }

    public int defaultDetectionRadius() {
        return defaultDetectionRadius.get();
    }

    public int cornerSearchRadius() {
        return cornerSearchRadius.get();
    }

    public int nbtParseTimeoutMs() {
        return nbtParseTimeoutMs.get();
    }

    public int chunkWarmupBudget() {
        return chunkWarmupBudget.get();
    }

    /** @return configured cap on already-loaded chunks inspected by one request */
    public int maxLoadedChunksPerOperation() {
        return maxLoadedChunksPerOperation.get();
    }

    public int structureCompressionLevel() {
        return structureCompressionLevel.get();
    }

    /** @return configured hard volume limit for one structure-block operation */
    public long maxOperationVolume() {
        return maxOperationVolume.get();
    }

    /** @return configured block-state inspection budget for synchronous helper scans */
    public int maxSynchronousScanBlocks() {
        return maxSynchronousScanBlocks.get();
    }

    /** @return configured byte quota for structure NBT post-processing */
    public long maxStructureNbtBytes() {
        return maxStructureNbtBytes.get();
    }
}
