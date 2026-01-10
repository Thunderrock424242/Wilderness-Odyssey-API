package com.thunder.wildernessodysseyapi.WorldGen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared terrain replacement helpers used by structure placement and template processors.
 */
public final class TerrainReplacerEngine {
    private static final int MIN_DIRT_COLUMN = 3;

    private TerrainReplacerEngine() {
    }

    /**
     * Samples the surface block at the given world position, walking down through replaceable blocks
     * until a real surface is found. Excluded blocks (such as water) will be skipped so they do not
     * overwrite terrain markers (e.g., grass blocks) during bunker placement.
     */
    public static BlockState sampleSurfaceBlock(LevelReader level, BlockPos target) {
        return sampleSurface(level, target).state();
    }

    /**
     * Samples both the surface Y coordinate and the block used at that location.
     */
    public static SurfaceSample sampleSurface(LevelReader level, BlockPos target) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ()) - 1;
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(target.getX(), surfaceY, target.getZ());

        for (int y = surfaceY; y >= minY; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);

            if (state.isAir()) {
                continue;
            }

            if (!state.getFluidState().isEmpty()) {
                BlockState fluidState = state.getFluidState().createLegacyBlock();
                if (isExcludedReplacement(fluidState)) {
                    continue;
                }
                return new SurfaceSample(y, fluidState);
            }

            if (isSolidSurface(state) && !isExcludedReplacement(state)) {
                return new SurfaceSample(y, state);
            }
        }

        return new SurfaceSample(surfaceY, Blocks.DIRT.defaultBlockState());
    }

    /**
     * Samples the surface material pair (top + filler) used for blending.
     */
    public static SurfaceMaterial sampleSurfaceMaterial(LevelReader level, BlockPos target) {
        SurfaceSample surface = sampleSurface(level, target);
        int fillerY = surface.y() - 1;
        if (fillerY < level.getMinBuildHeight()) {
            return new SurfaceMaterial(surface.y(), surface.state(), Blocks.DIRT.defaultBlockState());
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(target.getX(), fillerY, target.getZ());
        BlockState filler = level.getBlockState(cursor);
        if (filler.isAir() || isExcludedReplacement(filler)) {
            filler = Blocks.DIRT.defaultBlockState();
        }

        return new SurfaceMaterial(surface.y(), surface.state(), filler);
    }

    /**
     * Chooses the appropriate replacement block based on the target Y coordinate.
     */
    public static BlockState chooseReplacement(SurfaceMaterial material, int targetY) {
        if (targetY >= material.surfaceY()) {
            return material.surfaceState();
        }
        return material.fillerState();
    }

    /**
     * Collects replacement blocks for each offset if terrain replacement is enabled.
     */
    public static TerrainReplacementPlan planReplacement(ServerLevel level, BlockPos origin, List<BlockPos> offsets, boolean enabled) {
        if (!enabled || offsets.isEmpty()) {
            return TerrainReplacementPlan.disabled();
        }

        List<BlockState> sampled = new ArrayList<>(offsets.size());
        for (BlockPos offset : offsets) {
            BlockPos worldPos = origin.offset(offset);
            SurfaceMaterial material = sampleSurfaceMaterial(level, worldPos);
            sampled.add(chooseReplacement(material, worldPos.getY()));
        }

        return new TerrainReplacementPlan(true, Collections.unmodifiableList(sampled));
    }

    /**
     * Fills gaps between the terrain surface and the lowest structure block in each column of the bounds.
     */
    public static int applyAutoBlend(ServerLevel level, BoundingBox bounds, int maxFillDepth, int radius) {
        return applyAutoBlend(level, bounds, maxFillDepth, radius, AutoBlendMask.allowAll());
    }

    /**
     * Fills gaps between the terrain surface and the lowest structure block in each column of the bounds,
     * skipping columns that are not marked as supported by the supplied mask.
     */
    public static int applyAutoBlend(ServerLevel level,
                                     BoundingBox bounds,
                                     int maxFillDepth,
                                     int radius,
                                     AutoBlendMask mask) {
        int applied = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BoundingBox outerBounds = expandBounds(bounds, radius);
        for (int x = outerBounds.minX(); x <= outerBounds.maxX(); x++) {
            for (int z = outerBounds.minZ(); z <= outerBounds.maxZ(); z++) {
                if (!mask.allows(x, z)) {
                    continue;
                }
                int baseY = resolveBaseY(level, bounds, x, z);
                if (baseY == Integer.MIN_VALUE) {
                    continue;
                }

                SurfaceMaterial material = sampleSurfaceMaterial(level, new BlockPos(x, baseY, z));
                if (!hasContiguousDirtColumn(level, x, z, material.surfaceY())) {
                    continue;
                }
                int surfaceY = material.surfaceY();
                if (baseY <= surfaceY + 1) {
                    continue;
                }

                int fillTop = Math.min(baseY - 1, surfaceY + maxFillDepth);
                for (int y = surfaceY + 1; y <= fillTop; y++) {
                    cursor.set(x, y, z);
                    BlockState existing = level.getBlockState(cursor);
                    if (!existing.isAir()) {
                        continue;
                    }
                    BlockState replacement = chooseReplacement(material, y);
                    level.setBlock(cursor, replacement, 2);
                    applied++;
                }
            }
        }

        return applied;
    }

    private static boolean hasContiguousDirtColumn(LevelReader level, int x, int z, int surfaceY) {
        int minY = level.getMinBuildHeight();
        int consecutive = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, surfaceY, z);
        for (int y = surfaceY; y >= minY; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (state.is(BlockTags.DIRT)) {
                consecutive++;
                if (consecutive >= MIN_DIRT_COLUMN) {
                    return true;
                }
                continue;
            }
            if (state.is(Blocks.STONE) || state.is(Blocks.BEDROCK)) {
                return false;
            }
            if (!state.isAir()) {
                return false;
            }
        }
        return false;
    }

    private static int findLowestStructureBlock(ServerLevel level, BoundingBox bounds, int x, int z) {
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int resolveBaseY(ServerLevel level, BoundingBox bounds, int x, int z) {
        if (bounds.isInside(new BlockPos(x, bounds.minY(), z))) {
            return findLowestStructureBlock(level, bounds, x, z);
        }

        int clampedX = clamp(x, bounds.minX(), bounds.maxX());
        int clampedZ = clamp(z, bounds.minZ(), bounds.maxZ());
        return findLowestStructureBlock(level, bounds, clampedX, clampedZ);
    }

    private static BoundingBox expandBounds(BoundingBox bounds, int radius) {
        if (radius <= 0) {
            return bounds;
        }
        return new BoundingBox(
                bounds.minX() - radius,
                bounds.minY(),
                bounds.minZ() - radius,
                bounds.maxX() + radius,
                bounds.maxY(),
                bounds.maxZ() + radius
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isSolidSurface(BlockState state) {
        return !state.isAir();
    }

    private static boolean isExcludedReplacement(BlockState state) {
        return state.is(Blocks.WATER)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.BUBBLE_COLUMN)
                || state.getFluidState().is(Fluids.WATER)
                || state.getFluidState().is(Fluids.LAVA);
    }

    /**
     * Immutable description of the blocks that should replace terrain markers.
     */
    public record TerrainReplacementPlan(boolean enabled, List<BlockState> samples) {
        public static TerrainReplacementPlan disabled() {
            return new TerrainReplacementPlan(false, List.of());
        }
    }

    /**
     * Column mask used to decide which columns can be filled during auto-blend.
     */
    public record AutoBlendMask(int originX, int originZ, int sizeX, int sizeZ, boolean[] supportedColumns) {
        public static AutoBlendMask allowAll() {
            return new AutoBlendMask(0, 0, 0, 0, null);
        }

        public boolean allows(int worldX, int worldZ) {
            if (supportedColumns == null) {
                return true;
            }
            if (sizeX <= 0 || sizeZ <= 0) {
                return true;
            }
            int localX = clamp(worldX - originX, 0, sizeX - 1);
            int localZ = clamp(worldZ - originZ, 0, sizeZ - 1);
            int index = localX + (localZ * sizeX);
            return supportedColumns[index];
        }
    }

    /** Position/state pair returned by {@link #sampleSurface(LevelReader, BlockPos)}. */
    public record SurfaceSample(int y, BlockState state) { }

    /** Surface material pair used to blend between topsoil and filler. */
    public record SurfaceMaterial(int surfaceY, BlockState surfaceState, BlockState fillerState) { }
}
