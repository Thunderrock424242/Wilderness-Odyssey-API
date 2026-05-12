package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

public final class RiftSpawnHelper {
    private static final Random RNG = new Random();

    private RiftSpawnHelper() {
    }

    public static BlockPos findRiftSpawnPosition(ServerLevel overworld, int radius) {
        BlockPos spawn = overworld.getSharedSpawnPos();
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = radius > 0 ? RNG.nextInt(radius * 2 + 1) - radius : 0;
            int dz = radius > 0 ? RNG.nextInt(radius * 2 + 1) - radius : 0;
            int x = spawn.getX() + dx;
            int z = spawn.getZ() + dz;
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (isValidRiftPosition(overworld, candidate)) {
                return candidate;
            }
        }

        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        return new BlockPos(spawn.getX(), y, spawn.getZ());
    }

    public static BlockPos findHighestMountainRiftPosition(ServerLevel level, BlockPos searchCenter, int requestedRadius, int minimumDistance) {
        int radius = Mth.clamp(requestedRadius, 512, 2048);
        int minDistance = Mth.clamp(minimumDistance, 128, radius - 32);
        int step = 16;
        BlockPos best = new BlockPos(
                searchCenter.getX() + minDistance,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, searchCenter.getX() + minDistance, searchCenter.getZ()),
                searchCenter.getZ()
        );

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                long distanceSquared = (long) dx * dx + (long) dz * dz;
                if (distanceSquared > (long) radius * radius || distanceSquared < (long) minDistance * minDistance) {
                    continue;
                }

                int x = searchCenter.getX() + dx;
                int z = searchCenter.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (candidate.getY() > best.getY() && isValidRiftPosition(level, candidate)) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private static boolean isValidRiftPosition(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos.below()).liquid() && !level.getBlockState(pos).liquid();
    }
}
