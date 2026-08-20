package com.thunder.wildernessodysseyapi.structureblock;

import net.minecraft.core.Vec3i;

/**
 * Calculates bounded work estimates for expanded structure-block operations.
 *
 * <p>The expanded per-axis limit is intentionally separate from these budgets. Existing structure metadata can keep
 * its full dimensions while new synchronous scans and placements are rejected when their total work is unsafe.</p>
 */
public final class StructureBlockWorkBudget {

    private StructureBlockWorkBudget() {
    }

    /**
     * Calculates the number of blocks in a structure without integer overflow.
     *
     * @param size structure dimensions supplied by the structure block
     * @return zero when any dimension is non-positive, otherwise the full volume
     */
    public static long volume(Vec3i size) {
        return volume(size.getX(), size.getY(), size.getZ());
    }

    /**
     * Calculates the number of blocks in three dimensions without integer overflow.
     */
    public static long volume(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return 0L;
        }
        try {
            return Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Counts the chunks intersected by inclusive horizontal block bounds.
     */
    public static long chunkCount(int minX, int maxX, int minZ, int maxZ) {
        int lowX = Math.min(minX, maxX) >> 4;
        int highX = Math.max(minX, maxX) >> 4;
        int lowZ = Math.min(minZ, maxZ) >> 4;
        int highZ = Math.max(minZ, maxZ) >> 4;
        return ((long) highX - lowX + 1L) * ((long) highZ - lowZ + 1L);
    }
}
