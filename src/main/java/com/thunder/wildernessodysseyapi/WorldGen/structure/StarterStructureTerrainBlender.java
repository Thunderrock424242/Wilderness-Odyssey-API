package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lightweight terrain blending for the Starter Structure bunker. Instead of using terrain replacer
 * markers (which can eat custom machines), we feather the bunker into surrounding terrain by
 * backfilling the footprint edges with sampled surface blocks while leaving the bunker interior
 * untouched.
 */
public final class StarterStructureTerrainBlender {
    private StarterStructureTerrainBlender() {
    }

    /**
     * Applies a simple blending pass around the placed bunker.
     *
     * @param level     world
     * @param origin    structure origin (min corner as provided by Starter Structure)
     * @param footprint optional footprint details from the parsed schematic; may be {@code null}
     */
    public static void blendPlacedStructure(ServerLevel level, BlockPos origin, Footprint footprint) {
        if (level == null || origin == null) {
            return;
        }

        if (footprint == null || footprint.width() <= 0 || footprint.length() <= 0) {
            // Fall back to a conservative footprint if parsing failed.
            footprint = new Footprint(32, 16, 32);
        }

        int margin = Math.max(3, Math.min(8, Math.max(footprint.width(), footprint.length()) / 8));
        BlockPos min = footprint.minCorner(origin);
        BlockPos max = footprint.maxCorner(origin);

        int minX = min.getX() - margin;
        int maxX = max.getX() + margin;
        int minZ = min.getZ() - margin;
        int maxZ = max.getZ() + margin;
        int baseY = Math.max(level.getMinBuildHeight(), min.getY() - 2);

        int blended = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (isInsideFootprint(x, z, min, max)) {
                    continue; // Never touch the bunker itself.
                }

                TerrainReplacerEngine.SurfaceSample sample = TerrainReplacerEngine.sampleSurface(level, new BlockPos(x, origin.getY(), z));
                BlockState topState = sample.state();
                int topY = sample.y();

                // Build a column up to the surface so cliffs and pits around the bunker get patched.
                for (int y = baseY; y <= topY; y++) {
                    cursor.set(x, y, z);
                    BlockState existing = level.getBlockState(cursor);
                    if (!existing.isAir() && existing.getFluidState().isEmpty()) {
                        continue;
                    }
                    level.setBlock(cursor, topState, 2);
                    blended++;
                }
            }
        }

        int coverDepth = Math.max(0, StructureConfig.STARTER_STRUCTURE_EXTRA_COVER_DEPTH.get());
        int buried = coverDepth > 0
                ? buryStructure(level, origin, footprint, coverDepth)
                : 0;

        ModConstants.LOGGER.debug(
                "[Starter Structure compat] Blended bunker edges with {} terrain blocks (margin {}), added {} cover blocks (extra depth {}).",
                blended, margin, buried, coverDepth);
    }

    private static boolean isInsideFootprint(int x, int z, BlockPos min, BlockPos max) {
        return x >= min.getX() && x <= max.getX() && z >= min.getZ() && z <= max.getZ();
    }

    private static int buryStructure(ServerLevel level, BlockPos origin, Footprint footprint, int coverDepth) {
        BlockPos min = footprint.minCorner(origin);
        BlockPos max = footprint.maxCorner(origin);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int buried = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                int topSolidY = findHighestSolidY(level, x, min.getY(), max.getY(), z);
                if (topSolidY < min.getY()) {
                    continue;
                }

                TerrainReplacerEngine.SurfaceSample sample = TerrainReplacerEngine.sampleSurface(level, new BlockPos(x, origin.getY(), z));
                BlockState coverState = sample.state();
                int targetY = Math.max(sample.y(), topSolidY + coverDepth);

                for (int y = topSolidY + 1; y <= targetY; y++) {
                    cursor.set(x, y, z);
                    BlockState existing = level.getBlockState(cursor);
                    if (!existing.isAir() && existing.getFluidState().isEmpty()) {
                        continue;
                    }
                    level.setBlock(cursor, coverState, 2);
                    buried++;
                }
            }
        }

        return buried;
    }

    private static int findHighestSolidY(ServerLevel level, int x, int minY, int maxY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, maxY, z);
        for (int y = maxY; y >= minY; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return y;
            }
        }

        return minY - 1;
    }

    /**
     * Minimal structure footprint description captured from the parsed schematic.
     */
    public record Footprint(int width, int height, int length) {
        BlockPos minCorner(BlockPos origin) {
            return origin;
        }

        BlockPos maxCorner(BlockPos origin) {
            return origin.offset(width - 1, height - 1, length - 1);
        }
    }
}
