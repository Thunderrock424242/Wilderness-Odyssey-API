package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
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
            footprint = new Footprint(origin, 32, 16, 32);
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

        ModConstants.LOGGER.debug("[Starter Structure compat] Blended bunker edges with {} terrain blocks (margin {}).", blended, margin);
    }

    private static boolean isInsideFootprint(int x, int z, BlockPos min, BlockPos max) {
        return x >= min.getX() && x <= max.getX() && z >= min.getZ() && z <= max.getZ();
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
