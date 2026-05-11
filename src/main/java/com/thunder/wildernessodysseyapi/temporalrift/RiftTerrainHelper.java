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
        int safeRadius = Mth.clamp(radius, 3, 24);
        int safeDepth = Mth.clamp(depth, 3, 32);
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
                double collapse = Math.pow(1.0D - edge, 0.58D);
                int columnDepth = Math.max(1, (int) Math.round(safeDepth * collapse));
                int floorY = Math.max(minY, surfaceY - columnDepth);

                if (edge < 0.94D) {
                    for (int y = floorY + 1; y <= Math.min(surfaceY + 3, maxY); y++) {
                        mutable.set(x, y, z);
                        if (!level.getBlockState(mutable).is(Blocks.BEDROCK)) {
                            level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }

                mutable.set(x, floorY, z);
                level.setBlock(mutable, floorMaterial(edge, random), 3);

                if (edge > 0.72D && edge < 1.04D) {
                    mutable.set(x, surfaceY, z);
                    BlockState rimState = rimMaterial(random);
                    if (!level.getBlockState(mutable).isAir() && !level.getBlockState(mutable).liquid()) {
                        level.setBlock(mutable, rimState, 3);
                    }
                }
            }
        }

        buildRiftPedestal(level, centerSurface, centerFloorY);
        return new BlockPos(centerSurface.getX(), centerFloorY + 1, centerSurface.getZ());
    }

    private static BlockPos surfaceAt(ServerLevel level, BlockPos pos) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static void buildRiftPedestal(ServerLevel level, BlockPos centerSurface, int floorY) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = centerSurface.getX() + dx;
                int z = centerSurface.getZ() + dz;
                mutable.set(x, floorY, z);
                level.setBlock(mutable, Math.abs(dx) + Math.abs(dz) == 0
                        ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.OBSIDIAN.defaultBlockState(), 3);

                for (int y = floorY + 1; y <= floorY + 4; y++) {
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
