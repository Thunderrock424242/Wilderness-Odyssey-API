package com.thunder.wildernessodysseyapi.structureblock;

import com.thunder.wildernessodysseyapi.structureblock.config.StructureBlockConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared configuration for structure block enhancements.
 */
public final class StructureBlockSettings {

    private static final int DEFAULT_MAX_STRUCTURE_SIZE = 512;
    private static final int DEFAULT_MAX_STRUCTURE_OFFSET = 512;
    private static final int DEFAULT_DETECTION_RADIUS = 64;
    private static final int DEFAULT_CORNER_SEARCH_RADIUS = 512;
    private static final int DEFAULT_NBT_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_LOADED_CHUNK_SCAN_BUDGET = 256;
    private static final int DEFAULT_COMPRESSION_LEVEL = 6;
    private static final long DEFAULT_MAX_OPERATION_VOLUME = 4_194_304L;
    private static final int DEFAULT_MAX_SYNCHRONOUS_SCAN_BLOCKS = 1_048_576;
    private static final long DEFAULT_MAX_STRUCTURE_NBT_BYTES = 16_777_216L;

    private static int maxStructureSize = DEFAULT_MAX_STRUCTURE_SIZE;
    private static int maxStructureOffset = DEFAULT_MAX_STRUCTURE_OFFSET;
    private static int defaultDetectionRadius = DEFAULT_DETECTION_RADIUS;
    private static int cornerSearchRadius = DEFAULT_CORNER_SEARCH_RADIUS;
    private static int nbtParseTimeoutMillis = DEFAULT_NBT_TIMEOUT_MS;
    private static int loadedChunkScanBudget = DEFAULT_LOADED_CHUNK_SCAN_BUDGET;
    private static int structureCompressionLevel = DEFAULT_COMPRESSION_LEVEL;
    private static long maxOperationVolume = DEFAULT_MAX_OPERATION_VOLUME;
    private static int maxSynchronousScanBlocks = DEFAULT_MAX_SYNCHRONOUS_SCAN_BLOCKS;
    private static long maxStructureNbtBytes = DEFAULT_MAX_STRUCTURE_NBT_BYTES;

    private StructureBlockSettings() {
    }

    /**
     * Reloads cached settings from the server config, clamping values to safe ranges.
     */
    public static synchronized void reloadFromConfig() {
        if (StructureBlockConfig.CONFIG == null) {
            return;
        }

        maxStructureSize = Math.max(1, StructureBlockConfig.CONFIG.maxStructureSize());
        maxStructureOffset = Math.max(1, StructureBlockConfig.CONFIG.maxStructureOffset());

        defaultDetectionRadius = Mth.clamp(Math.max(0, StructureBlockConfig.CONFIG.defaultDetectionRadius()), 0,
                maxStructureOffset);
        cornerSearchRadius = Math.min(Math.max(0, StructureBlockConfig.CONFIG.cornerSearchRadius()), maxStructureOffset);

        nbtParseTimeoutMillis = Mth.clamp(StructureBlockConfig.CONFIG.nbtParseTimeoutMs(), 1_000, 120_000);
        loadedChunkScanBudget = Mth.clamp(StructureBlockConfig.CONFIG.maxLoadedChunksPerOperation(), 1, 1024);
        structureCompressionLevel = Mth.clamp(StructureBlockConfig.CONFIG.structureCompressionLevel(), 0, 9);
        maxOperationVolume = Mth.clamp(StructureBlockConfig.CONFIG.maxOperationVolume(), 1L, 16_777_216L);
        maxSynchronousScanBlocks = Mth.clamp(StructureBlockConfig.CONFIG.maxSynchronousScanBlocks(), 1, 4_194_304);
        maxStructureNbtBytes = Mth.clamp(StructureBlockConfig.CONFIG.maxStructureNbtBytes(), 1_048_576L,
                67_108_864L);
    }

    /**
     * @return Maximum allowed structure size per axis, sanitized from the configuration file.
     */
    public static int getMaxStructureSize() {
        return maxStructureSize;
    }

    /**
     * @return Maximum offset allowed between the structure block and the captured structure.
     */
    public static int getMaxStructureOffset() {
        return maxStructureOffset;
    }

    /**
     * @return Default detection radius, clamped so it never exceeds the offset limit.
     */
    public static int getDefaultDetectionRadius() {
        return defaultDetectionRadius;
    }

    /**
     * @return Maximum search range for corner structure blocks, limited to the offset limit to avoid chunk thrashing.
     */
    public static int getCornerSearchRadius() {
        return cornerSearchRadius;
    }

    /**
     * @return Timeout in milliseconds used when parsing large NBT files.
     */
    public static int getNbtParseTimeoutMillis() {
        return nbtParseTimeoutMillis;
    }

    /**
     * @return Maximum number of already-loaded chunks a structure operation may inspect.
     */
    public static int getLoadedChunkScanBudget() {
        return loadedChunkScanBudget;
    }

    /**
     * @return Desired compression level (0-9) used when writing saved structure .nbt files.
     */
    public static int getStructureCompressionLevel() {
        return structureCompressionLevel;
    }

    /**
     * @return Hard volume budget for a single save, load, or detection request.
     */
    public static long getMaxOperationVolume() {
        return maxOperationVolume;
    }

    /**
     * @return Maximum number of block states a synchronous helper scan may inspect.
     */
    public static int getMaxSynchronousScanBlocks() {
        return maxSynchronousScanBlocks;
    }

    /**
     * @return Maximum compressed structure file size and decoded NBT accounting quota.
     */
    public static long getMaxStructureNbtBytes() {
        return maxStructureNbtBytes;
    }

    /**
     * Determines whether the scanned block should count towards the saved bounding box. Air, structure helpers and
     * void markers are excluded so the automatic fitting logic ignores scaffolding and empty padding.
     */
    public static boolean isStructureContent(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID)) {
            return false;
        }
        // Ignore the various helper blocks that only exist to control jigsaw assembly.
        if (state.is(Blocks.JIGSAW) || state.is(Blocks.BARRIER)) {
            return false;
        }
        // Liquids should keep the bounding box even though the block is technically air when flowing, but every other
        // remaining block should contribute to the capture volume.
        return true;
    }
}
