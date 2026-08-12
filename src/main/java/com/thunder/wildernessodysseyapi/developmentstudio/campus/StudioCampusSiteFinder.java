package com.thunder.wildernessodysseyapi.developmentstudio.campus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Selects a bounded, low-slope natural site near spawn for the campus template. */
final class StudioCampusSiteFinder {
    private static final int[] SEARCH_RADII = {40, 56, 72, 88};
    private static final int MAX_ACCEPTED_SLOPE = 4;
    private static final int SAMPLE_STEP = 4;

    private StudioCampusSiteFinder() {
    }

    static BlockPos find(ServerLevel level, BlockPos spawn, Vec3i size, BlockPos markerOffset) {
        List<Candidate> candidates = new ArrayList<>();
        for (int radius : SEARCH_RADII) {
            evaluate(level, spawn.offset(radius, 0, 0), spawn, size, markerOffset, candidates);
            evaluate(level, spawn.offset(-radius, 0, 0), spawn, size, markerOffset, candidates);
            evaluate(level, spawn.offset(0, 0, radius), spawn, size, markerOffset, candidates);
            evaluate(level, spawn.offset(0, 0, -radius), spawn, size, markerOffset, candidates);
            evaluate(level, spawn.offset(radius, 0, radius), spawn, size, markerOffset, candidates);
            evaluate(level, spawn.offset(radius, 0, -radius), spawn, size, markerOffset, candidates);
            evaluate(level, spawn.offset(-radius, 0, radius), spawn, size, markerOffset, candidates);
            evaluate(level, spawn.offset(-radius, 0, -radius), spawn, size, markerOffset, candidates);
        }

        return candidates.stream()
                .filter(candidate -> candidate.slope() <= MAX_ACCEPTED_SLOPE)
                .min(Comparator.comparingInt(Candidate::score))
                .map(Candidate::anchor)
                .orElse(null);
    }

    private static void evaluate(ServerLevel level,
                                 BlockPos center,
                                 BlockPos spawn,
                                 Vec3i size,
                                 BlockPos markerOffset,
                                 List<Candidate> candidates) {
        int minX = center.getX() - markerOffset.getX();
        int minZ = center.getZ() - markerOffset.getZ();
        int maxX = minX + Math.max(1, size.getX()) - 1;
        int maxZ = minZ + Math.max(1, size.getZ()) - 1;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;

        for (int x : samples(minX, maxX)) {
            for (int z : samples(minZ, maxZ)) {
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos ground = new BlockPos(x, groundY, z);
                BlockState state = level.getBlockState(ground);
                if (!isSafeSurface(level, ground, state)) {
                    return;
                }
                minimumY = Math.min(minimumY, groundY);
                maximumY = Math.max(maximumY, groundY);
            }
        }

        if (minimumY == Integer.MAX_VALUE) {
            return;
        }
        int slope = maximumY - minimumY;
        int distance = (int) Math.sqrt(center.distSqr(spawn));
        BlockPos anchor = new BlockPos(center.getX(), maximumY + 1, center.getZ());
        candidates.add(new Candidate(anchor, slope, slope * 100 + distance));
    }

    private static List<Integer> samples(int minimum, int maximum) {
        List<Integer> values = new ArrayList<>();
        for (int value = minimum; value <= maximum; value += SAMPLE_STEP) {
            values.add(value);
        }
        if (values.isEmpty() || values.getLast() != maximum) {
            values.add(maximum);
        }
        return values;
    }

    private static boolean isSafeSurface(ServerLevel level, BlockPos ground, BlockState state) {
        if (!state.getFluidState().isEmpty() || level.getBlockEntity(ground) != null) {
            return false;
        }
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL);
    }

    private record Candidate(BlockPos anchor, int slope, int score) {
    }
}
