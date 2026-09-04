package com.thunder.wildernessodysseyapi.worldgen.coast;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small native-block palms for the tropical back-beach, with all-or-nothing bounds checks. */
final class TropicalPalmPlacer {

    private TropicalPalmPlacer() {
    }

    /** Places one preflighted tree; the coastal detail owner enforces the one-per-chunk cap. */
    static boolean place(
            WorldGenLevel level, BlockPos ground, int minimumX, int minimumZ,
            int height, int leanX, int leanZ
    ) {
        List<Part> parts = shape(height, leanX, leanZ);
        for (Part part : parts) {
            BlockPos position = ground.offset(part.x(), part.y(), part.z());
            if (position.getX() < minimumX || position.getX() >= minimumX + 16
                    || position.getZ() < minimumZ || position.getZ() >= minimumZ + 16
                    || position.getY() >= level.getMaxBuildHeight()
                    || !level.ensureCanWrite(position)
                    || !level.getBlockState(position).isAir()) {
                return false;
            }
        }
        for (Part part : parts) {
            BlockState state = part.trunk() ? Blocks.JUNGLE_LOG.defaultBlockState()
                    : Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(
                    LeavesBlock.DISTANCE, leafDistance(part, parts));
            level.setBlock(ground.offset(part.x(), part.y(), part.z()), state, 2);
        }
        return true;
    }

    /** Pure, bounded shape shared with regression tests; all branches remain face-connected. */
    static List<Part> shape(int requestedHeight, int directionX, int directionZ) {
        int height = Math.max(5, Math.min(7, requestedHeight));
        int leanX = Integer.signum(directionX);
        int leanZ = leanX == 0 ? Integer.signum(directionZ) : 0;
        if (leanX == 0 && leanZ == 0) {
            leanX = 1;
        }
        Map<Offset, Boolean> blocks = new LinkedHashMap<>();
        for (int y = 1; y <= height; y++) {
            boolean bent = y >= height - 1;
            blocks.put(new Offset(bent ? leanX : 0, y, bent ? leanZ : 0), true);
        }
        blocks.put(new Offset(leanX, height - 2, leanZ), true);
        blocks.put(new Offset(leanX, height + 1, leanZ), false);
        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int[] direction : directions) {
            boolean diagonal = direction[0] != 0 && direction[1] != 0;
            int length = diagonal ? 2 : 3;
            for (int step = 1; step <= length; step++) {
                int x = leanX + direction[0] * step;
                int z = leanZ + direction[1] * step;
                int y = height - (step == 3 ? 1 : 0);
                blocks.putIfAbsent(new Offset(x, y, z), false);
                if (diagonal) {
                    // Bridge diagonals so leaves do not become isolated decaying islands.
                    blocks.putIfAbsent(new Offset(x - direction[0], y, z), false);
                } else if (step == 2) {
                    blocks.putIfAbsent(new Offset(x, y - 1, z), false);
                }
            }
        }
        return blocks.entrySet().stream().map(entry -> new Part(
                entry.getKey().x(), entry.getKey().y(), entry.getKey().z(), entry.getValue()
        )).toList();
    }

    private static int leafDistance(Part leaf, List<Part> parts) {
        int distance = 7;
        for (Part part : parts) {
            if (part.trunk()) {
                distance = Math.min(distance, Math.abs(leaf.x() - part.x())
                        + Math.abs(leaf.y() - part.y()) + Math.abs(leaf.z() - part.z()));
            }
        }
        return Math.max(1, distance);
    }

    record Part(int x, int y, int z, boolean trunk) {
    }

    private record Offset(int x, int y, int z) {
    }
}
