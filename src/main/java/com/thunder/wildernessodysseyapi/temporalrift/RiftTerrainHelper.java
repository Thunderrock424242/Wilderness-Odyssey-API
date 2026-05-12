package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class RiftTerrainHelper {
    private RiftTerrainHelper() {
    }

    public static BlockPos createSinkhole(ServerLevel level, BlockPos aroundPos, int radius, int depth) {
        int safeRadius = Mth.clamp(radius, 6, 96);
        int safeDepth = Mth.clamp(depth, 6, 64);
        BlockPos centerSurface = surfaceAt(level, aroundPos);
        int minY = level.getMinBuildHeight() + 4;
        int maxY = level.getMaxBuildHeight() - 2;
        int centerFloorY = Mth.clamp(centerSurface.getY() - safeDepth, minY, maxY);
        RandomSource random = RandomSource.create(level.getSeed() ^ centerSurface.asLong());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -safeRadius; dx <= safeRadius; dx++) {
            for (int dz = -safeRadius; dz <= safeRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > safeRadius + 0.65D) {
                    continue;
                }

                int x = centerSurface.getX() + dx;
                int z = centerSurface.getZ() + dz;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (surfaceY <= minY) {
                    continue;
                }

                double edge = Mth.clamp(distance / safeRadius, 0.0D, 1.0D);
                double collapse = Math.pow(1.0D - edge, 0.40D);
                int columnDepth = Math.max(2, (int) Math.round(safeDepth * collapse));
                int floorY = Math.max(minY, surfaceY - columnDepth);

                if (edge < 0.98D) {
                    for (int y = floorY + 1; y <= Math.min(surfaceY + 3, maxY); y++) {
                        mutable.set(x, y, z);
                        if (!level.getBlockState(mutable).is(Blocks.BEDROCK)) {
                            level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }

                mutable.set(x, floorY, z);
                level.setBlock(mutable, floorMaterial(edge, random), 3);

                if (edge > 0.64D && edge < 1.08D) {
                    mutable.set(x, surfaceY, z);
                    BlockState rimState = rimMaterial(random);
                    if (!level.getBlockState(mutable).isAir() && !level.getBlockState(mutable).liquid()) {
                        level.setBlock(mutable, rimState, 3);
                    }
                }
            }
        }

        buildRiftBasin(level, centerSurface, centerFloorY, Mth.clamp(safeRadius / 5, 5, 12));
        return new BlockPos(centerSurface.getX(), centerFloorY + 1, centerSurface.getZ());
    }

    public static BlockPos createReturnScar(ServerLevel level, BlockPos aroundPos) {
        int radius = 4;
        int depth = 3;
        BlockPos centerSurface = surfaceAt(level, aroundPos);
        int minY = level.getMinBuildHeight() + 4;
        int maxY = level.getMaxBuildHeight() - 2;
        int centerFloorY = Mth.clamp(centerSurface.getY() - depth, minY, maxY);
        RandomSource random = RandomSource.create(level.getSeed() ^ centerSurface.asLong() ^ 0x5A17D3L);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius + 0.35D) {
                    continue;
                }

                int x = centerSurface.getX() + dx;
                int z = centerSurface.getZ() + dz;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                double edge = Mth.clamp(distance / radius, 0.0D, 1.0D);
                int floorY = Math.max(minY, surfaceY - Math.max(1, (int) Math.round(depth * Math.pow(1.0D - edge, 0.65D))));

                for (int y = floorY + 1; y <= Math.min(surfaceY + 2, maxY); y++) {
                    mutable.set(x, y, z);
                    if (!level.getBlockState(mutable).is(Blocks.BEDROCK)) {
                        level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                mutable.set(x, floorY, z);
                level.setBlock(mutable, edge < 0.45D
                        ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : rimMaterial(random), 3);
            }
        }

        return new BlockPos(centerSurface.getX(), centerFloorY + 1, centerSurface.getZ());
    }

    private static BlockPos surfaceAt(ServerLevel level, BlockPos pos) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static void buildRiftBasin(ServerLevel level, BlockPos centerSurface, int floorY, int basinRadius) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -basinRadius; dx <= basinRadius; dx++) {
            for (int dz = -basinRadius; dz <= basinRadius; dz++) {
                int x = centerSurface.getX() + dx;
                int z = centerSurface.getZ() + dz;
                mutable.set(x, floorY, z);
                int manhattan = Math.abs(dx) + Math.abs(dz);
                BlockState basinState = manhattan <= Math.max(3, basinRadius / 2)
                        ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : manhattan <= Math.max(5, basinRadius)
                        ? Blocks.SCULK.defaultBlockState()
                        : Blocks.DEEPSLATE.defaultBlockState();
                level.setBlock(mutable, basinState, 3);

                for (int y = floorY + 1; y <= floorY + Math.max(6, basinRadius / 2 + 3); y++) {
                    mutable.set(x, y, z);
                    level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static BlockState floorMaterial(double edge, RandomSource random) {
        if (edge < 0.28D) {
            return switch (random.nextInt(4)) {
                case 0 -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
                case 1 -> Blocks.SCULK.defaultBlockState();
                case 2 -> Blocks.AMETHYST_BLOCK.defaultBlockState();
                default -> Blocks.OBSIDIAN.defaultBlockState();
            };
        }
        if (edge < 0.68D) {
            return switch (random.nextInt(4)) {
                case 0 -> Blocks.DEEPSLATE.defaultBlockState();
                case 1 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                case 2 -> Blocks.BLACKSTONE.defaultBlockState();
                default -> Blocks.TUFF.defaultBlockState();
            };
        }
        return rimMaterial(random);
    }

    private static BlockState rimMaterial(RandomSource random) {
        return switch (random.nextInt(5)) {
            case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
            case 1 -> Blocks.GRAVEL.defaultBlockState();
            case 2 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case 3 -> Blocks.TUFF.defaultBlockState();
            default -> Blocks.DEEPSLATE.defaultBlockState();
        };
    }
}
