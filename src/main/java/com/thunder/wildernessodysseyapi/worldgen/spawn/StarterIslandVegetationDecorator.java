package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Places spaced jungle trees, bamboo clusters, hanging vines, and low undergrowth on the starter island. */
final class StarterIslandVegetationDecorator {
    private static final int MIN_TREE_SPACING = 6;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private StarterIslandVegetationDecorator() {
    }

    static void decorate(ServerLevel level,
                         RandomSource random,
                         StarterIslandJungleDecorator.DecorationArea area,
                         double density) {
        // The minimum-spacing check makes the island dense without turning the bunker clearing into an opaque wall.
        placeJungleTrees(level, random, area, density);
        placeBambooClusters(level, random, area, density);
        scatterUndergrowth(level, random, area, density);
    }

    private static void placeJungleTrees(ServerLevel level,
                                         RandomSource random,
                                         StarterIslandJungleDecorator.DecorationArea area,
                                         double density) {
        int target = StarterIslandJungleDecorator.targetTreeCount(area.flatRadius(), density);
        int usableRadius = area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN;
        List<BlockPos> roots = new ArrayList<>(target);

        for (int attempt = 0; attempt < target * 24 && roots.size() < target; attempt++) {
            int x = StarterIslandJungleDecorator.randomCoordinate(random, area.centerX(), usableRadius);
            int z = StarterIslandJungleDecorator.randomCoordinate(random, area.centerZ(), usableRadius);
            if (!StarterIslandJungleDecorator.insideCircle(x, z, area.centerX(), area.centerZ(), usableRadius)
                    || StarterIslandJungleDecorator.isProtectedPosition(
                            x,
                            z,
                            area.bunkerBounds(),
                            StarterIslandJungleDecorator.TREE_CLEARING,
                            true)
                    || isTooCloseToTree(x, z, roots)) {
                continue;
            }

            int surfaceY = StarterIslandJungleDecorator.surfaceY(level, x, z);
            BlockPos root = new BlockPos(x, surfaceY + 1, z);
            int height = 6 + random.nextInt(5);
            if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(root.below()))
                    || !canPlaceTree(level, root, height)) {
                continue;
            }

            placeTree(level, root, height, random);
            roots.add(root);
        }
    }

    private static void placeTree(ServerLevel level, BlockPos root, int height, RandomSource random) {
        BlockState jungleLog = Blocks.JUNGLE_LOG.defaultBlockState();
        for (int y = 0; y < height; y++) {
            level.setBlock(root.above(y), jungleLog, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
        }

        // Short upper branches prevent every generated tree from reading as the same vertical pole.
        int branchY = height - 3;
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            if (random.nextFloat() >= 0.48F) {
                continue;
            }
            BlockState branch = jungleLog.setValue(RotatedPillarBlock.AXIS, direction.getAxis());
            BlockPos branchPos = root.above(branchY).relative(direction);
            if (StarterIslandJungleDecorator.isVegetationReplaceable(level.getBlockState(branchPos))) {
                level.setBlock(branchPos, branch, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
            }
        }

        BlockState jungleLeaves = Blocks.JUNGLE_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);
        int canopyBaseY = root.getY() + height - 3;
        int[] radii = {2, 3, 2, 1};
        for (int layer = 0; layer < radii.length; layer++) {
            placeCanopyLayer(level, random, root, canopyBaseY + layer, radii[layer], jungleLeaves);
        }
        addHangingVines(level, root, canopyBaseY + 1, random);
    }

    private static void placeCanopyLayer(ServerLevel level,
                                         RandomSource random,
                                         BlockPos root,
                                         int y,
                                         int radius,
                                         BlockState jungleLeaves) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radius * radius + 1
                        || (distanceSquared >= radius * radius && random.nextFloat() < 0.35F)) {
                    continue;
                }
                BlockPos leafPos = new BlockPos(root.getX() + dx, y, root.getZ() + dz);
                BlockState existing = level.getBlockState(leafPos);
                if (StarterIslandJungleDecorator.isVegetationReplaceable(existing)
                        && !existing.is(BlockTags.LOGS)) {
                    level.setBlock(leafPos, jungleLeaves, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
                }
            }
        }
    }

    private static void addHangingVines(ServerLevel level,
                                        BlockPos root,
                                        int canopyY,
                                        RandomSource random) {
        for (Direction outward : HORIZONTAL_DIRECTIONS) {
            if (random.nextFloat() >= 0.72F) {
                continue;
            }
            BlockPos support = new BlockPos(
                    root.getX() + outward.getStepX() * 3,
                    canopyY,
                    root.getZ() + outward.getStepZ() * 3);
            if (!level.getBlockState(support).is(BlockTags.LEAVES)) {
                continue;
            }

            BlockState vine = vineFacingSupport(outward);
            BlockPos.MutableBlockPos cursor = support.relative(outward).mutable();
            int length = 1 + random.nextInt(4);
            for (int segment = 0; segment < length && level.getBlockState(cursor).isAir(); segment++) {
                level.setBlock(cursor, vine, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
                cursor.move(Direction.DOWN);
            }
        }
    }

    private static BlockState vineFacingSupport(Direction outward) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        return switch (outward) {
            case NORTH -> vine.setValue(VineBlock.SOUTH, true);
            case SOUTH -> vine.setValue(VineBlock.NORTH, true);
            case WEST -> vine.setValue(VineBlock.EAST, true);
            case EAST -> vine.setValue(VineBlock.WEST, true);
            default -> vine;
        };
    }

    private static void placeBambooClusters(ServerLevel level,
                                            RandomSource random,
                                            StarterIslandJungleDecorator.DecorationArea area,
                                            double density) {
        int clusters = Math.max(2, (int) Math.round(area.flatRadius() * density / 11.0D));
        for (int attempt = 0, placed = 0; attempt < clusters * 16 && placed < clusters; attempt++) {
            int clusterX = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerX(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            int clusterZ = StarterIslandJungleDecorator.randomCoordinate(
                    random, area.centerZ(), area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN);
            if (!StarterIslandJungleDecorator.insideCircle(
                    clusterX,
                    clusterZ,
                    area.centerX(),
                    area.centerZ(),
                    area.flatRadius() - StarterIslandJungleDecorator.TREE_EDGE_MARGIN)
                    || StarterIslandJungleDecorator.isProtectedPosition(
                            clusterX,
                            clusterZ,
                            area.bunkerBounds(),
                            StarterIslandJungleDecorator.TREE_CLEARING,
                            true)) {
                continue;
            }

            if (placeBambooCluster(level, random, clusterX, clusterZ)) {
                placed++;
            }
        }
    }

    private static boolean placeBambooCluster(ServerLevel level,
                                              RandomSource random,
                                              int clusterX,
                                              int clusterZ) {
        int stems = 2 + random.nextInt(4);
        boolean placedStem = false;
        for (int stem = 0; stem < stems; stem++) {
            int x = clusterX + random.nextInt(5) - 2;
            int z = clusterZ + random.nextInt(5) - 2;
            int surfaceY = StarterIslandJungleDecorator.surfaceY(level, x, z);
            BlockPos base = new BlockPos(x, surfaceY + 1, z);
            int height = 3 + random.nextInt(6);
            if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(base.below()))
                    || !hasReplaceableColumn(level, base, height)) {
                continue;
            }
            for (int y = 0; y < height; y++) {
                level.setBlock(
                        base.above(y),
                        Blocks.BAMBOO.defaultBlockState(),
                        StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
            }
            placedStem = true;
        }
        return placedStem;
    }

    private static void scatterUndergrowth(ServerLevel level,
                                           RandomSource random,
                                           StarterIslandJungleDecorator.DecorationArea area,
                                           double density) {
        int attempts = (int) Math.round(area.flatRadius() * 7.0D * density);
        int usableRadius = area.flatRadius() - 3;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = StarterIslandJungleDecorator.randomCoordinate(random, area.centerX(), usableRadius);
            int z = StarterIslandJungleDecorator.randomCoordinate(random, area.centerZ(), usableRadius);
            if (!StarterIslandJungleDecorator.insideCircle(x, z, area.centerX(), area.centerZ(), usableRadius)
                    || StarterIslandJungleDecorator.isProtectedPosition(
                            x,
                            z,
                            area.bunkerBounds(),
                            StarterIslandJungleDecorator.GROUND_CLEARING,
                            true)) {
                continue;
            }

            placeUndergrowth(level, random, x, z);
        }
    }

    private static void placeUndergrowth(ServerLevel level, RandomSource random, int x, int z) {
        int surfaceY = StarterIslandJungleDecorator.surfaceY(level, x, z);
        BlockPos plantPos = new BlockPos(x, surfaceY + 1, z);
        if (!StarterIslandJungleDecorator.isPlantableGround(level.getBlockState(plantPos.below()))
                || !StarterIslandJungleDecorator.isVegetationReplaceable(level.getBlockState(plantPos))) {
            return;
        }

        int roll = random.nextInt(100);
        BlockState plant = roll < 42
                ? Blocks.SHORT_GRASS.defaultBlockState()
                : roll < 69
                ? Blocks.FERN.defaultBlockState()
                : roll < 82
                ? Blocks.MOSS_CARPET.defaultBlockState()
                : roll < 91
                ? Blocks.AZALEA.defaultBlockState()
                : roll < 97
                ? Blocks.FLOWERING_AZALEA.defaultBlockState()
                : Blocks.MELON.defaultBlockState();
        if (plant.canSurvive(level, plantPos)) {
            level.setBlock(plantPos, plant, StarterIslandJungleDecorator.BLOCK_UPDATE_FLAGS);
        }
    }

    private static boolean canPlaceTree(ServerLevel level, BlockPos root, int height) {
        return root.getY() + height + 2 < level.getMaxBuildHeight()
                && hasReplaceableColumn(level, root, height + 2);
    }

    private static boolean hasReplaceableColumn(ServerLevel level, BlockPos base, int height) {
        for (int y = 0; y < height; y++) {
            if (!StarterIslandJungleDecorator.isVegetationReplaceable(level.getBlockState(base.above(y)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTooCloseToTree(int x, int z, List<BlockPos> roots) {
        int minimumDistanceSquared = MIN_TREE_SPACING * MIN_TREE_SPACING;
        for (BlockPos root : roots) {
            int dx = x - root.getX();
            int dz = z - root.getZ();
            if (dx * dx + dz * dz < minimumDistanceSquared) {
                return true;
            }
        }
        return false;
    }
}
