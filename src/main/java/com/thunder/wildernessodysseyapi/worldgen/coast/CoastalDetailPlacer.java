package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import com.thunder.wildernessodysseyapi.worldgen.coast.config.CoastalWorldgenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, deterministic coastal content using the terrain feature's shore snapshot.
 * Thirty-two irregular small-detail candidates and twenty palm attempts are the
 * hard limits; successful content is capped separately to keep the wet beach open.
 */
final class CoastalDetailPlacer {
    private CoastalDetailPlacer() { }

    static boolean place(WorldGenLevel level, CoastalGenerationArea area) {
        boolean placed = placePalms(level, area);
        long seed = level.getSeed();
        int details = 0;
        int logs = 0;
        int formations = 0;
        for (int attempt = 0; attempt < 32 && details < 12; attempt++) {
            int x = area.minimumX + (int) (roll(seed, area.minimumX, area.minimumZ, attempt, 11) * 16);
            int z = area.minimumZ + (int) (roll(seed, area.minimumX, area.minimumZ, attempt, 23) * 16);
            BlockPos top = area.surface(x, z);
            var type = area.shoreType(x, z);
            var water = area.contact(x, z);
            if (type == null || water == null || area.column(x, z).water()
                    || !CoastalGenerationArea.natural(level.getBlockState(top))) continue;
            double patch = CoastalTerrainProfile.noise(seed, x, z, 13, 0xB8B34B2F33C9E0A7L);
            double density = CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY.get();
            // Back-beach vegetation groups follow coherent patches; the strand
            // retains a much lower occupancy even at the authored maximum.
            density *= water.distance() > 6 ? 0.75 + (patch + 1.0) * 0.55 : 0.65;
            var detail = CoastalTerrainProfile.detail(type, CoastalTerrainProfile.zone(type,
                    water.distance(), patch * 0.5), roll(seed, x, z, attempt, 37),
                    roll(seed, x, z, attempt, 41), density);
            boolean success = switch (detail) {
                case NONE -> false;
                case BEACH_GRASS, PALM -> CoastalWorldgenConfig.ENABLE_COASTAL_VEGETATION.get()
                        && placePlants(level, area, top, type, seed);
                case DRIFTWOOD -> CoastalWorldgenConfig.ENABLE_DRIFTWOOD.get() && logs < 2
                        && roll(seed, x, z, attempt, 53) < 0.7 * CoastalWorldgenConfig.DRIFTWOOD_DENSITY.get()
                        && placeDriftwood(level, area, top, type, water, seed);
                case SHELL_PATCH -> CoastalWorldgenConfig.ENABLE_ROCK_OUTCROPS.get()
                        && placeShellPatch(level, area, top, type, seed);
                case ROCK_CLUSTER, SEA_STACK -> CoastalWorldgenConfig.ENABLE_ROCK_OUTCROPS.get()
                        && formations < 3
                        && roll(seed, x, z, attempt, 59) < 0.7 * CoastalWorldgenConfig.ROCK_CLUSTER_DENSITY.get()
                        && (detail == CoastalTerrainProfile.Detail.SEA_STACK
                        ? placeSeaStack(level, area, water, seed)
                        : placeMound(level, area, top, type, seed, false));
                case TIDE_POOL -> CoastalWorldgenConfig.ENABLE_TIDE_POOLS.get()
                        && water.distance() <= 4 && placeTidePool(level, area, top);
                case ICE_FRAGMENT -> CoastalWorldgenConfig.ENABLE_ICE_FRAGMENTS.get()
                        && formations < (type == CoastalWaveProfile.ShoreType.COLD ? 1 : 3)
                        && placeMound(level, area, top, type, seed, true);
            };
            if (success) {
                placed = true;
                details++;
                if (detail == CoastalTerrainProfile.Detail.DRIFTWOOD) logs++;
                if (detail == CoastalTerrainProfile.Detail.ROCK_CLUSTER
                        || detail == CoastalTerrainProfile.Detail.SEA_STACK
                        || detail == CoastalTerrainProfile.Detail.ICE_FRAGMENT) formations++;
            }
        }
        return placed;
    }

    private static boolean placePalms(WorldGenLevel level, CoastalGenerationArea area) {
        double density = CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY.get()
                * CoastalWorldgenConfig.PALM_DENSITY.get();
        if (!CoastalWorldgenConfig.ENABLE_COASTAL_VEGETATION.get() || density <= 0
                || roll(level.getSeed(), area.minimumX, area.minimumZ, 0, 71) >= Math.min(1, density * 1.7)) {
            return false;
        }
        long seed = level.getSeed();
        int target = roll(seed, area.minimumX, area.minimumZ, 0, 73) < Math.min(0.7, density) ? 2 : 1;
        List<BlockPos> palms = new ArrayList<>(2);
        for (int attempt = 0; attempt < 20 && palms.size() < target; attempt++) {
            // Sample roots where a complete crown CAN fit, instead of spending
            // almost every chance on anchors destined to fail the chunk guard.
            int x = area.minimumX + 4 + (int) (roll(seed, area.minimumX, area.minimumZ, attempt, 79) * 8);
            int z = area.minimumZ + 4 + (int) (roll(seed, area.minimumX, area.minimumZ, attempt, 83) * 8);
            var water = area.contact(x, z);
            if (area.shoreType(x, z) != CoastalWaveProfile.ShoreType.TROPICAL || water == null
                    || water.distance() < 7 || water.distance() > 20
                    || area.column(x, z).water()) continue;
            BlockPos top = area.surface(x, z);
            if (palms.stream().anyMatch(other -> other.distSqr(top) < 36)) continue;
            int height = 5 + (int) (roll(seed, x, z, attempt, 89) * 4);
            int direction = (int) (roll(seed, x, z, attempt, 97) * 4);
            int leanX = direction < 2 ? (direction == 0 ? 1 : -1) : 0;
            int leanZ = direction >= 2 ? (direction == 2 ? 1 : -1) : 0;
            if (TropicalPalmPlacer.place(level, top, area.minimumX, area.minimumZ, height, leanX, leanZ)) {
                palms.add(top);
            }
        }
        return !palms.isEmpty();
    }

    private static boolean placePlants(WorldGenLevel level, CoastalGenerationArea area, BlockPos top,
                                       CoastalWaveProfile.ShoreType type, long seed) {
        boolean placed = false;
        for (int index = 0; index < 4; index++) {
            int x = top.getX() + index % 2;
            int z = top.getZ() + index / 2;
            if (!area.inside(x, z)) continue;
            BlockPos support = area.surface(x, z);
            var water = area.contact(x, z);
            if (area.shoreType(x, z) != type || water == null || water.distance() < 5
                    || Math.abs(support.getY() - top.getY()) > 1) continue;
            BlockPos pos = support.above();
            if (!area.canWrite(pos) || !level.getBlockState(pos).isAir()) continue;
            BlockState ground = level.getBlockState(support);
            BlockState plant = (type == CoastalWaveProfile.ShoreType.TROPICAL
                    || type == CoastalWaveProfile.ShoreType.COLD) ? Blocks.FERN.defaultBlockState()
                    : Blocks.SHORT_GRASS.defaultBlockState();
            if (ground.is(BlockTags.SAND)) {
                if (type == CoastalWaveProfile.ShoreType.DUNE) {
                    plant = Blocks.DEAD_BUSH.defaultBlockState();
                } else if (water.distance() >= 7 && CoastalGenerationArea.natural(ground)
                        && CoastalGenerationArea.natural(level.getBlockState(support.below()))) {
                    // Native grass needs soil; bounded meadow islands provide a
                    // supported sand-to-green transition rather than invalid grass on sand.
                    level.setBlock(support, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                } else continue;
            }
            if (plant.canSurvive(level, pos)) {
                level.setBlock(pos, plant, 2);
                placed = true;
            }
        }
        return placed;
    }

    static boolean placeDriftwood(WorldGenLevel level, CoastalGenerationArea area, BlockPos top,
                                  CoastalWaveProfile.ShoreType type, CoastalGenerationArea.Contact water, long seed) {
        int tangentX = Math.abs(water.directionZ()) >= Math.abs(water.directionX()) ? 1 : 0;
        int tangentZ = tangentX == 0 ? 1 : 0;
        BlockState log = (type == CoastalWaveProfile.ShoreType.TROPICAL ? Blocks.JUNGLE_LOG
                : type == CoastalWaveProfile.ShoreType.COLD || type == CoastalWaveProfile.ShoreType.GLACIAL
                ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                tangentX == 1 ? Direction.Axis.X : Direction.Axis.Z);
        int wanted = 2 + (int) (roll(seed, top.getX(), top.getZ(), 0, 101) * 3);
        for (int length = wanted; length >= 2; length--) {
            for (int sign : new int[]{1, -1}) {
                List<BlockPos> positions = new ArrayList<>(4);
                for (int offset = 0; offset < length; offset++) {
                    int x = top.getX() + tangentX * offset * sign;
                    int z = top.getZ() + tangentZ * offset * sign;
                    if (!area.inside(x, z)) break;
                    BlockPos support = area.surface(x, z);
                    BlockPos pos = support.above();
                    if (support.getY() != top.getY() || !area.canWrite(pos)
                            || !CoastalGenerationArea.natural(level.getBlockState(support))
                            || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                            || !CoastalGenerationArea.clear(level.getBlockState(pos))) break;
                    positions.add(pos);
                }
                if (positions.size() == length) {
                    for (BlockPos pos : positions) level.setBlock(pos, log, 2);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean placeShellPatch(WorldGenLevel level, CoastalGenerationArea area, BlockPos top,
                                          CoastalWaveProfile.ShoreType type, long seed) {
        boolean placed = false;
        int count = 1 + (int) (roll(seed, top.getX(), top.getZ(), 0, 103) * 3);
        for (int index = 0; index < count; index++) {
            int x = top.getX() + index % 2;
            int z = top.getZ() + index / 2;
            if (!area.inside(x, z)) continue;
            BlockPos pos = area.surface(x, z);
            if (area.canWrite(pos) && level.getBlockState(pos).is(BlockTags.SAND)
                    && CoastalGenerationArea.clear(level.getBlockState(pos.above()))) {
                level.setBlock(pos, (type == CoastalWaveProfile.ShoreType.TROPICAL && index == 0
                        ? Blocks.DEAD_BRAIN_CORAL_BLOCK : Blocks.CALCITE).defaultBlockState(), 2);
                placed = true;
            }
        }
        return placed;
    }

    private static boolean placeMound(WorldGenLevel level, CoastalGenerationArea area, BlockPos top,
                                     CoastalWaveProfile.ShoreType type, long seed, boolean ice) {
        int height = ice || type == CoastalWaveProfile.ShoreType.ROCKY
                ? 1 + (int) (roll(seed, top.getX(), top.getZ(), 0, 107) * 2) : 1;
        BlockState material = (ice ? Blocks.PACKED_ICE
                : roll(seed, top.getX(), top.getZ(), 0, 109) > 0.6 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
        List<BlockPos> positions = new ArrayList<>(12);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                int x = top.getX() + dx;
                int z = top.getZ() + dz;
                if (!area.inside(x, z)) return false;
                BlockPos support = area.surface(x, z);
                if (Math.abs(support.getY() - top.getY()) > 1
                        || !CoastalGenerationArea.natural(level.getBlockState(support))
                        || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) return false;
                int columnHeight = dx == 0 && dz == 0 ? height : 1;
                for (int y = 1; y <= columnHeight; y++) {
                    BlockPos pos = support.above(y);
                    if (!area.canWrite(pos) || !CoastalGenerationArea.clear(level.getBlockState(pos))) return false;
                    positions.add(pos);
                }
            }
        }
        for (BlockPos pos : positions) {
            boolean blue = ice && type == CoastalWaveProfile.ShoreType.GLACIAL
                    && pos.getX() == top.getX() && pos.getZ() == top.getZ();
            level.setBlock(pos, blue ? Blocks.BLUE_ICE.defaultBlockState() : material, 2);
        }
        return true;
    }

    static boolean placeTidePool(WorldGenLevel level, CoastalGenerationArea area, BlockPos top) {
        int y = top.getY();
        // Verify a solid floor and an intact natural rim before excavating. No
        // fluids escape into caves, down a beach slope, or over the chunk edge.
        for (int dz = -1; dz <= 2; dz++) {
            for (int dx = -1; dx <= 2; dx++) {
                BlockPos pos = top.offset(dx, 0, dz);
                if (!area.canWrite(pos) || area.surface(pos.getX(), pos.getZ()).getY() < y
                        || !CoastalGenerationArea.natural(level.getBlockState(pos))
                        || !CoastalGenerationArea.natural(level.getBlockState(pos.below()))
                        || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
                if (dx >= 0 && dx < 2 && dz >= 0 && dz < 2
                        && !CoastalGenerationArea.clear(level.getBlockState(pos.above()))) return false;
            }
        }
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                BlockPos pos = top.offset(dx, 0, dz);
                if (!level.getBlockState(pos.above()).isAir()) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            }
        }
        return true;
    }

    static boolean placeSeaStack(WorldGenLevel level, CoastalGenerationArea area,
                                         CoastalGenerationArea.Contact water, long seed) {
        int x = water.x() + Integer.signum(water.directionX()) * 2;
        int z = water.z() + Integer.signum(water.directionZ()) * 2;
        int top = level.getSeaLevel() + 2 + (int) (roll(seed, x, z, 0, 113) * 3);
        List<BlockPos> positions = new ArrayList<>(96);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                if (!area.inside(x + dx, z + dz)) return false;
                int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + dx, z + dz) - 1;
                int surface = area.column(x + dx, z + dz).y();
                if (!area.column(x + dx, z + dz).water() || surface - floor < 1 || surface - floor > 10
                        || !CoastalGenerationArea.natural(level.getBlockState(new BlockPos(x + dx, floor, z + dz)))) return false;
                int columnTop = top - (dx == 0 && dz == 0 ? 0 : 2);
                for (int y = floor + 1; y <= columnTop; y++) {
                    BlockPos pos = new BlockPos(x + dx, y, z + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!area.canWrite(pos) || !(state.isAir() || state.getFluidState().is(FluidTags.WATER))) return false;
                    positions.add(pos);
                }
            }
        }
        for (BlockPos pos : positions) level.setBlock(pos, (pos.getY() < top - 1
                ? Blocks.STONE : Blocks.ANDESITE).defaultBlockState(), 2);
        return true;
    }

    private static double roll(long seed, int x, int z, int attempt, int salt) {
        return CoastalTerrainProfile.hashUnit(seed, x, z, (long) salt * 0xD1B54A32D192ED03L
                ^ (long) attempt * 0xABC98388FB8FAC03L);
    }
}
