package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Builds the jungle island's ground patches, entrance trail, and moss-covered boulders. */
final class StarterIslandGroundDecorator {
    private StarterIslandGroundDecorator() {
    }

    static void decorate(ServerLevel level,
                         RandomSource random,
                         StarterIslandJungleDecorator.DecorationArea area,
                         double density) {
        raiseTerrainHummocks(level, random, area, density);
        paintGroundPatches(level, random, area, density);
        buildEntrancePath(level, random, area);
        placeMossyBoulders(level, random, area, density);
        placeFallenLogs(level, random, area, density);
    }

    private static void raiseTerrainHummocks(ServerLevel level,
                                             RandomSource random,
                                             StarterIslandJungleDecorator.DecorationArea area,
                                             double density) {
        int hummockCount = Math.max(10, (int) Math.round(area.flatRadius() * density / 5.5D));
        int usableRadius = area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN - 2;
        int islandBaseY = level.getSeaLevel() + 1;

        // Broad one- and two-block rises create readable terrain contours without endangering the buried facility.
        for (int attempt = 0, placed = 0; attempt < hummockCount * 12 && placed < hummockCount; attempt++) {
            int centerX = StarterIslandJungleDecorator.randomCoordinate(random, area.centerX(), usableRadius);
            int centerZ = StarterIslandJungleDecorator.randomCoordinate(random, area.centerZ(), usableRadius);
            if (!StarterIslandJungleDecorator.insideCircle(
                    centerX, centerZ, area.centerX(), area.centerZ(), usableRadius)
                    || StarterIslandJungleDecorator.isProtectedPosition(
                            centerX,
                            centerZ,
                            area.bunkerBounds(),
                            StarterIslandJungleDecorator.TREE_CLEARING + 4,
                            true)) {
                continue;
            }

            int radius = 7 + random.nextInt(10);
            int peakHeight = random.nextFloat() < 0.38F ? 2 : 1;
            buildHummock(level, area, centerX, centerZ, radius, peakHeight, islandBaseY, random.nextLong());
            placed++;
        }
    }

    private static void buildHummock(ServerLevel level,
                                     StarterIslandJungleDecorator.DecorationArea area,
                                     int centerX,
                                     int centerZ,
                                     int radius,
                                     int peakHeight,
                                     int islandBaseY,
                                     long shapeSalt) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                double distortedDistance = Math.sqrt(dx * dx + dz * dz)
                        + coordinateNoise(x, z, shapeSalt) * 2.25D;
                double influence = 1.0D - distortedDistance / radius;
                if (influence <= 0.14D
                        || !StarterIslandJungleDecorator.insideCircle(
                                x,
                                z,
                                area.centerX(),
                                area.centerZ(),
                                area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN)
                        || StarterIslandJungleDecorator.isProtectedPosition(
                                x,
                                z,
                                area.bunkerBounds(),
                                StarterIslandJungleDecorator.GROUND_CLEARING + 2,
                                true)) {
                    continue;
                }

                int rise = peakHeight == 2 && influence > 0.58D ? 2 : 1;
                raiseGroundColumn(level, x, z, islandBaseY + rise);
            }
        }
    }

    private static void raiseGroundColumn(ServerLevel level, int x, int z, int targetTopY) {
        int currentTopY = StarterIslandJungleDecorator.surfaceY(level, x, z);
        if (currentTopY >= targetTopY) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, currentTopY, z);
        if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(cursor))) {
            return;
        }
        for (int y = currentTopY + 1; y <= targetTopY; y++) {
            cursor.setY(y);
            if (!StarterIslandJungleDecorator.isVegetationReplaceable(level.getBlockState(cursor))) {
                return;
            }
        }

        cursor.setY(currentTopY);
        level.setBlock(cursor, Blocks.DIRT.defaultBlockState(), StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
        for (int y = currentTopY + 1; y < targetTopY; y++) {
            cursor.setY(y);
            level.setBlock(cursor, Blocks.DIRT.defaultBlockState(), StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
        }
        cursor.setY(targetTopY);
        level.setBlock(cursor, Blocks.GRASS_BLOCK.defaultBlockState(), StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
    }

    private static double coordinateNoise(int x, int z, long salt) {
        long mixed = salt ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return ((mixed >>> 11) & 2047L) / 2047.0D - 0.5D;
    }

    private static void paintGroundPatches(ServerLevel level,
                                           RandomSource random,
                                           StarterIslandJungleDecorator.DecorationArea area,
                                           double density) {
        int patchCount = Math.max(8, (int) Math.round(area.flatRadius() * density / 4.5D));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int patch = 0; patch < patchCount; patch++) {
            int patchX = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerX(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            int patchZ = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerZ(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            int patchRadius = 3 + random.nextInt(4);

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
        int target = Math.max(3, (int) Math.round(area.flatRadius() * density / 10.0D));
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

    private static void placeFallenLogs(ServerLevel level,
                                        RandomSource random,
                                        StarterIslandJungleDecorator.DecorationArea area,
                                        double density) {
        int target = Math.max(2, (int) Math.round(area.flatRadius() * density / 22.0D));
        int usableRadius = area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN;
        for (int attempt = 0, placed = 0; attempt < target * 18 && placed < target; attempt++) {
            int x = StarterIslandJungleDecorator.randomCoordinate(random, area.centerX(), usableRadius);
            int z = StarterIslandJungleDecorator.randomCoordinate(random, area.centerZ(), usableRadius);
            if (!StarterIslandJungleDecorator.insideCircle(x, z, area.centerX(), area.centerZ(), usableRadius)
                    || StarterIslandJungleDecorator.isProtectedPosition(
                            x,
                            z,
                            area.bunkerBounds(),
                            StarterIslandJungleDecorator.TREE_CLEARING,
                            true)) {
                continue;
            }

            Direction direction = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
            int length = 3 + random.nextInt(4);
            List<BlockPos> positions = collectFallenLogPositions(level, x, z, direction, length);
            if (positions.isEmpty()) {
                continue;
            }

            BlockState log = Blocks.JUNGLE_LOG.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, direction.getAxis());
            for (BlockPos position : positions) {
                level.setBlock(position, log, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
                BlockPos mossPos = position.above();
                BlockState moss = Blocks.MOSS_CARPET.defaultBlockState();
                if (random.nextFloat() < 0.45F
                        && level.getBlockState(mossPos).isAir()
                        && moss.canSurvive(level, mossPos)) {
                    level.setBlock(mossPos, moss, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
                }
            }
            placed++;
        }
    }

    private static List<BlockPos> collectFallenLogPositions(ServerLevel level,
                                                            int startX,
                                                            int startZ,
                                                            Direction direction,
                                                            int length) {
        List<BlockPos> positions = new ArrayList<>(length);
        int expectedSurfaceY = Integer.MIN_VALUE;
        for (int segment = 0; segment < length; segment++) {
            int x = startX + direction.getStepX() * segment;
            int z = startZ + direction.getStepZ() * segment;
            int surfaceY = StarterIslandJungleDecorator.surfaceY(level, x, z);
            if (expectedSurfaceY == Integer.MIN_VALUE) {
                expectedSurfaceY = surfaceY;
            }
            if (surfaceY != expectedSurfaceY) {
                return List.of();
            }
            BlockPos position = new BlockPos(x, surfaceY + 1, z);
            if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(position.below()))
                    || !StarterIslandJungleDecorator.isVegetationReplaceable(level.getBlockState(position))) {
                return List.of();
            }
            positions.add(position);
        }
        return positions;
    }
}
