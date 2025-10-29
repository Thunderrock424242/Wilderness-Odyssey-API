package com.thunder.wildernessodysseyapi.util;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared configuration for structure block enhancements.
 */
public final class StructureBlockSettings {

    /** Maximum allowed capture size per axis when working with structure blocks. */
    private static final int MAX_STRUCTURE_SIZE = 512;
    /** Maximum offset permitted between the structure block and its captured area. */
    private static final int MAX_STRUCTURE_OFFSET = 512;
    /** Default radius scanned when pressing the Detect button without existing bounds. */
    private static final int DEFAULT_DETECTION_RADIUS = 64;
    /** Maximum range used when searching for matching corner structure blocks. */
    private static final int CORNER_SEARCH_RADIUS = 512;
    private StructureBlockSettings() {
    }

    /**
     * @return Maximum allowed structure size per axis, sanitized from the configuration file.
     */
    public static int getMaxStructureSize() {
        return MAX_STRUCTURE_SIZE;
    }

    /**
     * @return Maximum offset allowed between the structure block and the captured structure.
     */
    public static int getMaxStructureOffset() {
        return MAX_STRUCTURE_OFFSET;
    }

    /**
     * @return Default detection radius, clamped so it never exceeds the offset limit.
     */
    public static int getDefaultDetectionRadius() {
        int radius = Math.max(0, DEFAULT_DETECTION_RADIUS);
        return Mth.clamp(radius, 0, getMaxStructureOffset());
    }

    /**
     * @return Maximum search range for corner structure blocks, limited to the offset limit to avoid chunk thrashing.
     */
    public static int getCornerSearchRadius() {
        int radius = Math.max(0, CORNER_SEARCH_RADIUS);
        return Math.min(radius, getMaxStructureOffset());
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
