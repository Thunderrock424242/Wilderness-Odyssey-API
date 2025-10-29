package com.thunder.wildernessodysseyapi.util;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared configuration for structure block enhancements.
 */
public final class StructureBlockSettings {
    /**
     * Expanded limit for structure block dimensions. Vanilla uses 48 blocks per axis which is too restrictive for
     * the large set pieces shipped with the modpack, so we greatly increase the limit.
     */
    public static final int MAX_STRUCTURE_SIZE = 512;

    /**
     * Expanded limit for the offset from the structure block when capturing structures. Matching the size limit keeps
     * the UI experience consistent and allows placing the controller block outside of large builds.
     */
    public static final int MAX_STRUCTURE_OFFSET = 512;

    /**
     * Default radius used when the detect button runs without an existing bounding box. Scanning a generous
     * 64-block radius keeps the operation responsive while still covering most medium-sized builds. Players can
     * expand the configured size manually before detecting if their structure exceeds this area.
     */
    public static final int DEFAULT_DETECTION_RADIUS = 64;

    /**
     * Maximum diagonal search distance when looking for a corner block that defines the opposite corner of the
     * structure. Matching the expanded offset limit allows lone corner markers to seed the bounding box anywhere inside
     * the 512-block cube around the save block.
     */
    public static final int CORNER_SEARCH_RADIUS = 512;

    private StructureBlockSettings() {
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
