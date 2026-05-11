package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SafeTeleportHelper {
    private SafeTeleportHelper() {
    }

    public static BlockPos findSafePosition(Level level, int x, int startY, int z) {
        int minY = level.getMinBuildHeight() + 2;
        int maxY = level.getMaxBuildHeight() - 2;
        int yStart = Math.max(minY, Math.min(startY, maxY));

        for (int y = yStart; y <= maxY; y++) {
            if (isSafe(level, x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }
        for (int y = yStart - 1; y >= minY; y--) {
            if (isSafe(level, x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    public static BlockPos findSafePositionNearby(Level level, int x, int y, int z, int radius) {
        BlockPos direct = findSafePosition(level, x, y, z);
        if (direct != null) {
            return direct;
        }

        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r) {
                        BlockPos candidate = findSafePosition(level, x + dx, y, z + dz);
                        if (candidate != null) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(Level level, int x, int y, int z) {
        BlockPos groundPos = new BlockPos(x, y - 1, z);
        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = new BlockPos(x, y + 1, z);
        BlockState ground = level.getBlockState(groundPos);
        BlockState feet = level.getBlockState(feetPos);
        BlockState head = level.getBlockState(headPos);
        return !ground.isAir() && ground.isSolid() && isPassable(feet) && isPassable(head);
    }

    private static boolean isPassable(BlockState state) {
        return state.isAir() || !state.isSolid();
    }
}
