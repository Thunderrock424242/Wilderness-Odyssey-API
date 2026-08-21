package com.thunder.wildernessodysseyapi.worldgen.structure;

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
 * massive prefabs. The helpers in this class validate the placement footprint without loading
 * chunks and expose chunk-aware bounds for diagnostics and downstream bookkeeping.
 */
public final class LargeStructurePlacementOptimizer {
    private static final int CHUNK_SIZE = 16;
    /** Maximum decoded template volume accepted by the runtime loader. */
    public static final long MAX_TEMPLATE_VOLUME = 8_000_000L;
    /** Maximum number of serialized block entries accepted from one template. */
    public static final int MAX_TEMPLATE_BLOCKS = 2_000_000;
    /** Maximum number of serialized entities accepted from one template. */
    public static final int MAX_TEMPLATE_ENTITIES = 1_024;
    /** Maximum number of already-loaded chunks one synchronous placement may touch. */
    public static final int MAX_PLACEMENT_CHUNKS = 256;

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
     * Checks whether any template axis exceeds the supported structure span.
     *
     * <p>The structure-block limit describes the maximum size of one axis, not
     * the total bounding-box volume. Large but valid prefabs such as the starter
     * bunker should therefore not be reported as invalid merely because their
     * three-dimensional volume is greater than the per-axis limit.</p>
     *
     * @param size the template size
     * @return {@code true} when the placement should be considered heavy
     */
    public static boolean exceedsStructureBlockLimit(Vec3i size) {
        return Math.max(size.getX(), Math.max(size.getY(), size.getZ()))
                > StructureUtils.STRUCTURE_BLOCK_LIMIT;
    }

    /**
     * Validates template dimensions before any world access or block mutation occurs.
     *
     * @param size decoded template dimensions
     * @return whether every axis and the total volume fit the runtime placement budget
     */
    public static boolean isWithinTemplateBudget(Vec3i size) {
        if (size == null || size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0
                || exceedsStructureBlockLimit(size)) {
            return false;
        }
        long volume = (long) size.getX() * size.getY() * size.getZ();
        return volume <= MAX_TEMPLATE_VOLUME;
    }

    /**
     * Validates serialized content counts before a decoded template can reach world mutation.
     *
     * @param blockCount serialized block entries in the structure NBT
     * @param entityCount serialized entity entries in the structure NBT
     * @return whether both counts fit the synchronous placement policy
     */
    public static boolean isWithinContentBudget(int blockCount, int entityCount) {
        return blockCount >= 0 && blockCount <= MAX_TEMPLATE_BLOCKS
                && entityCount >= 0 && entityCount <= MAX_TEMPLATE_ENTITIES;
    }

    /**
     * Returns the number of horizontal chunks touched by the requested placement.
     */
    public static long countPlacementChunks(BlockPos origin, Vec3i size) {
        if (origin == null || size == null || size.getX() <= 0 || size.getZ() <= 0) {
            return 0L;
        }
        BlockPos max = origin.offset(size.getX() - 1, 0, size.getZ() - 1);
        long chunkXCount = (long) Math.floorDiv(max.getX(), CHUNK_SIZE)
                - Math.floorDiv(origin.getX(), CHUNK_SIZE) + 1L;
        long chunkZCount = (long) Math.floorDiv(max.getZ(), CHUNK_SIZE)
                - Math.floorDiv(origin.getZ(), CHUNK_SIZE) + 1L;
        return Math.max(0L, chunkXCount) * Math.max(0L, chunkZCount);
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
     * Confirms that all chunks touched by the structure are already fully loaded.
     *
     * <p>This method never requests a chunk. Placement is refused when a caller has not arranged
     * the required tickets, preventing a template from synchronously cascading into neighboring
     * chunk generation.</p>
     *
     * @return {@code true} when the placement is within budget and every touched chunk is loaded
     */
    public static boolean preparePlacement(ServerLevel level, BlockPos origin, Vec3i size) {
        long chunkCount = countPlacementChunks(origin, size);
        if (level == null || chunkCount <= 0L || chunkCount > MAX_PLACEMENT_CHUNKS) {
            return false;
        }
        BlockPos max = origin.offset(Math.max(0, size.getX() - 1), 0, Math.max(0, size.getZ() - 1));
        int minChunkX = Math.floorDiv(origin.getX(), CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(max.getX(), CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(max.getZ(), CHUNK_SIZE);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Splits the bounding box of the structure into chunk-aligned slices for diagnostics and
     * placement-result bookkeeping. The current template placement is still one synchronous
     * operation; this method intentionally does not claim to make it incremental.
     *
     * @param origin the structure origin
     * @param size   the template size
     * @return chunk-aligned AABBs ordered from lowest chunk to highest
     */
    public static List<AABB> computeChunkSlices(BlockPos origin, Vec3i size) {
        long chunkCount = countPlacementChunks(origin, size);
        if (!isWithinTemplateBudget(size) || chunkCount <= 0L || chunkCount > MAX_PLACEMENT_CHUNKS) {
            return Collections.emptyList();
        }

        BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        int minChunkX = Math.floorDiv(origin.getX(), CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(max.getX(), CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(max.getZ(), CHUNK_SIZE);
        int chunkXCount = maxChunkX - minChunkX + 1;
        int chunkZCount = maxChunkZ - minChunkZ + 1;
        long estimatedSlices = (long) chunkXCount * chunkZCount;
        int initialCapacity = (int) Math.min(MAX_PLACEMENT_CHUNKS, Math.max(0L, estimatedSlices));
        List<AABB> slices = new ArrayList<>(initialCapacity);

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
