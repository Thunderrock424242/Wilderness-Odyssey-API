package com.thunder.wildernessodysseyapi.WorldGen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility helpers focused on placing exceptionally large structures as safely as possible.
 * <p>
 * Vanilla assumes that structures are relatively small, which can lead to cascading chunk loads
 * or thousands of block updates happening in the same tick when Wilderness Odyssey deploys its
 * massive prefabs. The helpers in this class prepare the terrain ahead of time and expose
 * chunk-aware slices that higher level callers can process incrementally.
 */
public final class LargeStructurePlacementOptimizer {
    private static final int CHUNK_SIZE = 16;

    private LargeStructurePlacementOptimizer() {
    }

    /**
     * Estimates the number of blocks that will be touched by a structure placement operation.
     *
     * @param size the template size
     * @return the estimated number of modified blocks (clamped to {@link Integer#MAX_VALUE})
     */
    public static int estimateAffectedBlocks(Vec3i size) {
        long volume = (long) Math.max(0, size.getX()) * Math.max(0, size.getY()) * Math.max(0, size.getZ());
        return volume >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) volume;
    }

    /**
     * Checks whether the given template size exceeds the mod's soft limit for structure placements.
     * This can be used to log warnings before starting expensive operations.
     *
     * @param size the template size
     * @return {@code true} when the placement should be considered heavy
     */
    public static boolean exceedsStructureBlockLimit(Vec3i size) {
        return estimateAffectedBlocks(size) > StructureUtils.STRUCTURE_BLOCK_LIMIT;
    }

    /**
     * Computes the axis-aligned bounding box occupied by a structure that starts at {@code origin}.
     * The resulting bounds are inclusive of all blocks touched by the template and expand by one
     * block on each axis to match vanilla's block placement checks.
     */
    public static AABB createBounds(BlockPos origin, Vec3i size) {
        BlockPos max = origin.offset(Math.max(0, size.getX() - 1), Math.max(0, size.getY() - 1), Math.max(0, size.getZ() - 1));
        return new AABB(
                origin.getX(), origin.getY(), origin.getZ(),
                max.getX() + 1, max.getY() + 1, max.getZ() + 1
        );
    }

    /**
     * Computes the levelgen {@link BoundingBox} occupied by a structure that starts at {@code origin}.
     * The resulting bounds are inclusive of all blocks touched by the template and mirror the extents
     * used by {@link #createBounds(BlockPos, Vec3i)}.
     */
    public static BoundingBox createPlacementBox(BlockPos origin, Vec3i size) {
        BlockPos max = origin.offset(Math.max(0, size.getX() - 1), Math.max(0, size.getY() - 1), Math.max(0, size.getZ() - 1));
        return new BoundingBox(
                origin.getX(), origin.getY(), origin.getZ(),
                max.getX(), max.getY(), max.getZ()
        );
    }

    /**
     * Ensures that all chunks touched by the structure are fully loaded before the template is placed.
     * This avoids a cascade of synchronous chunk loads at placement time.
     */
    public static void preparePlacement(ServerLevel level, BlockPos origin, Vec3i size) {
        if (level == null) {
            return;
        }
        BlockPos max = origin.offset(Math.max(0, size.getX() - 1), 0, Math.max(0, size.getZ() - 1));
        int minChunkX = Math.floorDiv(origin.getX(), CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(max.getX(), CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(max.getZ(), CHUNK_SIZE);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    /**
     * Splits the bounding box of the structure into chunk-aligned slices. Callers can iterate over
     * the returned list to process the structure in smaller batches, reducing the risk of hitting
     * the server's tick budget with a single placement.
     *
     * @param origin the structure origin
     * @param size   the template size
     * @return chunk-aligned AABBs ordered from lowest chunk to highest
     */
    public static List<AABB> computeChunkSlices(BlockPos origin, Vec3i size) {
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return Collections.emptyList();
        }

        List<AABB> slices = new ArrayList<>();
        BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        int minChunkX = Math.floorDiv(origin.getX(), CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(max.getX(), CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(max.getZ(), CHUNK_SIZE);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int chunkMinX = chunkX * CHUNK_SIZE;
            int chunkMaxX = chunkMinX + CHUNK_SIZE;
            double minX = Math.max(origin.getX(), chunkMinX);
            double maxX = Math.min(max.getX() + 1, chunkMaxX);
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int chunkMinZ = chunkZ * CHUNK_SIZE;
                int chunkMaxZ = chunkMinZ + CHUNK_SIZE;
                double minZ = Math.max(origin.getZ(), chunkMinZ);
                double maxZ = Math.min(max.getZ() + 1, chunkMaxZ);
                slices.add(new AABB(minX, origin.getY(), minZ, maxX, max.getY() + 1, maxZ));
            }
        }

        return slices;
    }
}
