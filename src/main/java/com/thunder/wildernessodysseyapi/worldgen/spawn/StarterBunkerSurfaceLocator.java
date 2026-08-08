package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Finds the visible starter-bunker shell without treating its very large underground template as a clearing.
 *
 * <p>The bunker NBT spans the underground facility, but landscaping only needs to avoid blocks exposed at the
 * island surface. This bounded scan runs once after placement and keeps trees close to the entrance without
 * allowing trunks to intersect its walls or roof.</p>
 */
final class StarterBunkerSurfaceLocator {
    private static final int SURFACE_SCAN_HEIGHT = 24;
    private static final int FALLBACK_HALF_SPAN = 14;

    private StarterBunkerSurfaceLocator() {
    }

    static AABB find(ServerLevel level, AABB templateBounds, BlockPos surfaceAnchor) {
        int templateMinX = (int) Math.floor(templateBounds.minX);
        int templateMaxX = (int) Math.ceil(templateBounds.maxX) - 1;
        int templateMinZ = (int) Math.floor(templateBounds.minZ);
        int templateMaxZ = (int) Math.ceil(templateBounds.maxZ) - 1;
        int minY = Math.max(surfaceAnchor.getY(), (int) Math.floor(templateBounds.minY));
        int maxY = Math.min(
                surfaceAnchor.getY() + SURFACE_SCAN_HEIGHT,
                (int) Math.ceil(templateBounds.maxY) - 1);

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int foundMinY = Integer.MAX_VALUE;
        int foundMaxY = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = templateMinX; x <= templateMaxX; x++) {
            for (int z = templateMinZ; z <= templateMaxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!isSurfaceStructureBlock(level.getBlockState(cursor))) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                    foundMinY = Math.min(foundMinY, y);
                    foundMaxY = Math.max(foundMaxY, y);
                }
            }
        }

        if (minX == Integer.MAX_VALUE) {
            return fallbackBounds(surfaceAnchor);
        }
        return new AABB(minX, foundMinY, minZ, maxX + 1, foundMaxY + 1, maxZ + 1);
    }

    static boolean isSurfaceStructureBlock(BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.DIRT)
                && !state.is(Blocks.GRASS_BLOCK)
                && !state.is(Blocks.DIRT_PATH)
                && !state.is(Blocks.SAND)
                && !state.is(Blocks.RED_SAND)
                && !state.is(Blocks.GRAVEL)
                && !state.is(Blocks.MOSS_BLOCK)
                && !state.is(Blocks.MOSS_CARPET)
                && !state.is(Blocks.SHORT_GRASS)
                && !state.is(Blocks.FERN)
                && !state.is(Blocks.TALL_GRASS)
                && !state.is(Blocks.LARGE_FERN)
                && !state.is(Blocks.BLUE_WOOL)
                && !state.is(Blocks.STRUCTURE_VOID);
    }

    static AABB fallbackBounds(BlockPos surfaceAnchor) {
        return new AABB(
                surfaceAnchor.getX() - FALLBACK_HALF_SPAN,
                surfaceAnchor.getY(),
                surfaceAnchor.getZ() - FALLBACK_HALF_SPAN,
                surfaceAnchor.getX() + FALLBACK_HALF_SPAN + 1,
                surfaceAnchor.getY() + 1,
                surfaceAnchor.getZ() + FALLBACK_HALF_SPAN + 1);
    }
}
