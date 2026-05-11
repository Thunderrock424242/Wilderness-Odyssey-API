package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

    private static boolean isValidRiftPosition(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos.below()).liquid() && !level.getBlockState(pos).liquid();
    }
}
