package com.thunder.wildernessodysseyapi.WorldGen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared terrain replacement helpers used by structure placement and template processors.
 */
public final class TerrainReplacerEngine {
    private TerrainReplacerEngine() {
    }

    /**
     * Samples the surface block at the given world position, walking down through replaceable blocks
     * until a real surface is found. Fluids are now respected so lakes/rivers remain intact instead
     * of being replaced with dirt when blending structures into the terrain.
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

            // Prefer the fluid itself when we encounter water/lava so we keep natural bodies of
            // water instead of backfilling them with dirt around the starter structure.
            if (!state.getFluidState().isEmpty()) {
                return new SurfaceSample(y, state.getFluidState().createLegacyBlock());
            }

            if (isSolidSurface(state)) {
                return new SurfaceSample(y, state);
            }
        }

        return new SurfaceSample(surfaceY, Blocks.DIRT.defaultBlockState());
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
            sampled.add(sampleSurfaceBlock(level, worldPos));
        }

        return new TerrainReplacementPlan(true, Collections.unmodifiableList(sampled));
    }

    private static boolean isSolidSurface(BlockState state) {
        return !state.isAir();
    }

    /**
     * Immutable description of the blocks that should replace terrain markers.
     */
    public record TerrainReplacementPlan(boolean enabled, List<BlockState> samples) {
        public static TerrainReplacementPlan disabled() {
            return new TerrainReplacementPlan(false, List.of());
        }
    }

    /** Position/state pair returned by {@link #sampleSurface(LevelReader, BlockPos)}. */
    public record SurfaceSample(int y, BlockState state) { }
}
