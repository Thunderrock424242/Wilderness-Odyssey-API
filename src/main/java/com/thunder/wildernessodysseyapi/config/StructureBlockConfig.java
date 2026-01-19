package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server-side configuration for expanded structure block behavior.
 */
public class StructureBlockConfig {

    public static final StructureBlockConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<StructureBlockConfig, ModConfigSpec> pair = new ModConfigSpec.Builder()
                .configure(StructureBlockConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private final ModConfigSpec.IntValue maxStructureSize;
    private final ModConfigSpec.IntValue maxStructureOffset;
    private final ModConfigSpec.IntValue defaultDetectionRadius;
    private final ModConfigSpec.IntValue cornerSearchRadius;
    private final ModConfigSpec.IntValue nbtParseTimeoutMs;
    private final ModConfigSpec.IntValue chunkWarmupBudget;
    private final ModConfigSpec.IntValue structureCompressionLevel;

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
                        "How many chunks to proactively load around the structure block before scanning for content."
                                + " Set to 0 to disable warmup.")
                .translation("wildernessodysseyapi.structure_blocks.chunk_warmup_budget")
                .defineInRange("chunkWarmupBudget", 256, 0, 4096);

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

    public int structureCompressionLevel() {
        return structureCompressionLevel.get();
    }
}
