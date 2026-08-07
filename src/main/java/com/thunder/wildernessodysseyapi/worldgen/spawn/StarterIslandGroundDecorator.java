package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Builds the jungle island's ground patches, entrance trail, and moss-covered boulders. */
final class StarterIslandGroundDecorator {
    private StarterIslandGroundDecorator() {
    }

    static void decorate(ServerLevel level,
                         RandomSource random,
                         StarterIslandJungleDecorator.DecorationArea area,
                         double density) {
        paintGroundPatches(level, random, area, density);
        buildEntrancePath(level, random, area);
        placeMossyBoulders(level, random, area, density);
    }

    private static void paintGroundPatches(ServerLevel level,
                                           RandomSource random,
                                           StarterIslandJungleDecorator.DecorationArea area,
                                           double density) {
        int patchCount = Math.max(4, (int) Math.round(area.flatRadius() * density / 7.0D));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int patch = 0; patch < patchCount; patch++) {
            int patchX = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerX(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            int patchZ = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerZ(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            int patchRadius = 2 + random.nextInt(3);

            for (int dx = -patchRadius; dx <= patchRadius; dx++) {
                for (int dz = -patchRadius; dz <= patchRadius; dz++) {
                    int x = patchX + dx;
                    int z = patchZ + dz;
                    if (dx * dx + dz * dz > patchRadius * patchRadius
                            || !StarterIslandJungleDecorator.insideCircle(
                                    x, z, area.centerX(), area.centerZ(), area.flatRadius() - 2)
                            || StarterIslandJungleDecorator.isProtectedPosition(
                                    x,
                                    z,
                                    area.bunkerBounds(),
                                    StarterIslandJungleDecorator.GROUND_CLEARING,
                                    true)) {
                        continue;
                    }

                    int surfaceY = StarterIslandJungleDecorator.surfaceY(level, x, z);
                    cursor.set(x, surfaceY, z);
                    if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(cursor))) {
                        continue;
                    }

                    int roll = random.nextInt(10);
                    BlockState replacement = roll < 5
                            ? Blocks.PODZOL.defaultBlockState()
                            : roll < 8
                            ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : Blocks.COARSE_DIRT.defaultBlockState();
                    level.setBlock(cursor, replacement, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
                }
            }
        }
    }

    private static void buildEntrancePath(ServerLevel level,
                                          RandomSource random,
                                          StarterIslandJungleDecorator.DecorationArea area) {
        int entranceX = (int) Math.floor((area.bunkerBounds().minX + area.bunkerBounds().maxX) * 0.5D);
        int frontZ = (int) Math.floor(area.bunkerBounds().minZ) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // The unrotated bunker door faces negative Z. A wandering path extends that established orientation.
        for (int step = 0; step < StarterIslandJungleDecorator.ENTRANCE_PATH_LENGTH; step++) {
            int z = frontZ - step;
            int pathCenterX = entranceX + (int) Math.round(Math.sin(step * 0.42D) * 1.25D);
            int halfWidth = step < 5 ? 2 : 1 + random.nextInt(2);
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                if (Math.abs(dx) == halfWidth && random.nextFloat() < 0.22F) {
                    continue;
                }
                int x = pathCenterX + dx;
                int surfaceY = StarterIslandJungleDecorator.surfaceY(level, x, z);
                cursor.set(x, surfaceY, z);
                if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(cursor))) {
                    continue;
                }

                int roll = random.nextInt(100);
                BlockState pathState = roll < 52
                        ? Blocks.DIRT_PATH.defaultBlockState()
                        : roll < 73
                        ? Blocks.COARSE_DIRT.defaultBlockState()
                        : roll < 88
                        ? Blocks.ROOTED_DIRT.defaultBlockState()
                        : roll < 96
                        ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                level.setBlock(cursor, pathState, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
            }
        }
    }

    private static void placeMossyBoulders(ServerLevel level,
                                           RandomSource random,
                                           StarterIslandJungleDecorator.DecorationArea area,
                                           double density) {
        int target = Math.max(2, (int) Math.round(area.flatRadius() * density / 15.0D));
        for (int attempt = 0, placed = 0; attempt < target * 12 && placed < target; attempt++) {
            int x = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerX(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            int z = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerZ(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            if (!StarterIslandJungleDecorator.insideCircle(
                    x,
                    z,
                    area.centerX(),
                    area.centerZ(),
                    area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN)
                    || StarterIslandJungleDecorator.isProtectedPosition(
                            x,
                            z,
                            area.bunkerBounds(),
                            StarterIslandJungleDecorator.TREE_CLEARING,
                            true)) {
                continue;
            }

            int y = StarterIslandJungleDecorator.surfaceY(level, x, z) + 1;
            BlockPos base = new BlockPos(x, y, z);
            if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(base.below()))
                    || !StarterIslandJungleDecorator.isVegetationReplaceable(level.getBlockState(base))) {
                continue;
            }

            placeBoulder(level, random, base);
            placed++;
        }
    }

    private static void placeBoulder(ServerLevel level, RandomSource random, BlockPos base) {
        int radius = 1 + random.nextInt(2);
        for (int dy = 0; dy <= 1; dy++) {
            int layerRadius = Math.max(0, radius - dy);
            for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                    if (dx * dx + dz * dz > layerRadius * layerRadius + random.nextInt(2)) {
                        continue;
                    }
                    BlockPos targetPos = base.offset(dx, dy, dz);
                    if (!StarterIslandJungleDecorator.isVegetationReplaceable(level.getBlockState(targetPos))) {
                        continue;
                    }
                    BlockState rock = random.nextInt(5) == 0
                            ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : random.nextBoolean()
                            ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : Blocks.COBBLESTONE.defaultBlockState();
                    level.setBlock(targetPos, rock, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
                }
            }
        }
    }
}
