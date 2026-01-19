package com.thunder.wildernessodysseyapi.WorldGen.structure;

/**
 * Miscellaneous helpers and constants related to structure handling.
 */
public final class StructureUtils {
    /**
     * Maximum number of blocks that a structure placement operation may affect.
     * <p>
     * The vanilla limit is 48 blocks, but Wilderness Odyssey expands this to 256
     * to better accommodate larger modded structures.
     */
    public static final int STRUCTURE_BLOCK_LIMIT = 256;

    private StructureUtils() {
    }
}
