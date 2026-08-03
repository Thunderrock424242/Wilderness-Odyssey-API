package com.thunder.wildernessodysseyapi.worldgen.structure;

/**
 * Miscellaneous helpers and constants related to structure handling.
 */
public final class StructureUtils {
    /**
     * Maximum supported template span along any one axis.
     *
     * <p>Wilderness Odyssey allows a larger span than the vanilla structure
     * block so imported modpack prefabs can be placed by the custom loader.</p>
     */
    public static final int STRUCTURE_BLOCK_LIMIT = 256;

    private StructureUtils() {
    }
}
