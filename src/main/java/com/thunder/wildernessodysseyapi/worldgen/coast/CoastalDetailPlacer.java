package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
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

/**
 * Places sparse persistent content after a coastal chunk's surface bands are complete.
 *
 * <p>Only sixteen deterministic four-by-four anchors are considered. Every
 * write is constrained to the feature origin chunk and natural terrain, while
 * neighbor reads stop at chunks already exposed by the worldgen region.</p>
 */
final class CoastalDetailPlacer {

    private static final int WATER_SEARCH_DISTANCE = 14;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private CoastalDetailPlacer() {
    }

    /** Places configured details for one feature origin chunk. */
    static boolean place(WorldGenLevel level, int minimumX, int minimumZ) {
        boolean placed = false;
        long seed = level.getSeed();
        for (int cellZ = 0; cellZ < 16; cellZ += 4) {
            for (int cellX = 0; cellX < 16; cellX += 4) {
                int anchorX = minimumX + cellX + (int) Math.floor(
                        hashUnit(seed, minimumX + cellX, minimumZ + cellZ,
                                0xD1B54A32D192ED03L) * 4.0);
                int anchorZ = minimumZ + cellZ + (int) Math.floor(
                        hashUnit(seed, minimumX + cellX, minimumZ + cellZ,
                                0xABC98388FB8FAC03L) * 4.0);
                int topY = surfaceY(level, anchorX, anchorZ);
                BlockPos top = new BlockPos(anchorX, topY, anchorZ);
                if (level.getFluidState(top).is(FluidTags.WATER)
                        || !isNaturalSurface(level.getBlockState(top))) {
                    continue;
                }
                CoastalWaveProfile.ShoreType shoreType = ModBiomes
                        .coastalShoreType(level.getBiome(top))
                        .orElse(null);
                if (shoreType == null) {
                    continue;
                }
                WaterContact water = nearestWaterContact(level, anchorX, topY, anchorZ);
                if (water == null) {
                    continue;
                }
                CoastalTerrainProfile.Detail detail = CoastalTerrainProfile.detail(
                        shoreType,
                        CoastalTerrainProfile.zone(shoreType, water.distance()),
                        hashUnit(seed, anchorX, anchorZ, 0x8CB92BA72F3D8DD7L),
                        hashUnit(seed, anchorX, anchorZ, 0xDB4F0B9175AE2165L),
                        CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY.get()
                );
                if (enabled(detail)) {
                    placed |= placeDetail(
                            level, minimumX, minimumZ, top, shoreType, detail, water, seed);
                }
            }
        }
        return placed;
    }

    private static boolean enabled(CoastalTerrainProfile.Detail detail) {
        return switch (detail) {
            case NONE -> false;
            case BEACH_GRASS -> CoastalWorldgenConfig.ENABLE_COASTAL_VEGETATION.get();
            case DRIFTWOOD -> CoastalWorldgenConfig.ENABLE_DRIFTWOOD.get();
            case TIDE_POOL -> CoastalWorldgenConfig.ENABLE_TIDE_POOLS.get();
            case ICE_FRAGMENT -> CoastalWorldgenConfig.ENABLE_ICE_FRAGMENTS.get();
            case SHELL_PATCH, ROCK_CLUSTER, SEA_STACK ->
                    CoastalWorldgenConfig.ENABLE_ROCK_OUTCROPS.get();
        };
    }

    private static boolean placeDetail(
            WorldGenLevel level,
            int minimumX,
            int minimumZ,
            BlockPos top,
            CoastalWaveProfile.ShoreType shoreType,
            CoastalTerrainProfile.Detail detail,
            WaterContact water,
            long seed
    ) {
        return switch (detail) {
            case NONE -> false;
            case BEACH_GRASS -> placePlant(level, top, shoreType, seed);
            case DRIFTWOOD -> placeDriftwood(
                    level, minimumX, minimumZ, top, shoreType, water, seed);
            case SHELL_PATCH -> placeShellPatch(
                    level, minimumX, minimumZ, top, shoreType, seed);
            case ROCK_CLUSTER -> placeRockCluster(
                    level, minimumX, minimumZ, top, shoreType, seed);
            case TIDE_POOL -> placeTidePool(level, minimumX, minimumZ, top);
            case ICE_FRAGMENT -> placeIceFragment(
                    level, minimumX, minimumZ, top, seed);
            case SEA_STACK -> placeSeaStack(
                    level, minimumX, minimumZ, top, water, seed);
        };
    }

    private static boolean placePlant(
            WorldGenLevel level,
            BlockPos top,
            CoastalWaveProfile.ShoreType shoreType,
            long seed
    ) {
        BlockPos position = top.above();
        if (!canWrite(level, position) || !level.getBlockState(position).isAir()) {
            return false;
        }
        BlockState ground = level.getBlockState(top);
        BlockState plant;
        if (ground.is(BlockTags.SAND)) {
            plant = Blocks.DEAD_BUSH.defaultBlockState();
        } else if (shoreType == CoastalWaveProfile.ShoreType.TROPICAL
                && hashUnit(seed, top.getX(), top.getZ(), 0xF1357AEA2E62A9C5L) > 0.58) {
            plant = Blocks.FERN.defaultBlockState();
        } else {
            plant = Blocks.SHORT_GRASS.defaultBlockState();
        }
        if (!plant.canSurvive(level, position)) {
            return false;
        }
        level.setBlock(position, plant, 2);
        return true;
    }

    private static boolean placeDriftwood(
            WorldGenLevel level,
            int minimumX,
            int minimumZ,
            BlockPos top,
            CoastalWaveProfile.ShoreType shoreType,
            WaterContact water,
            long seed
    ) {
        int tangentX = water.directionZ() == 0 ? 0 : Integer.signum(-water.directionZ());
        int tangentZ = water.directionX() == 0 ? 0 : Integer.signum(water.directionX());
        if (tangentX == 0 && tangentZ == 0) {
            tangentX = 1;
        }
        Direction.Axis axis = Math.abs(tangentX) >= Math.abs(tangentZ)
                ? Direction.Axis.X : Direction.Axis.Z;
        if (axis == Direction.Axis.X) {
            tangentX = tangentX == 0 ? 1 : Integer.signum(tangentX);
            tangentZ = 0;
        } else {
            tangentX = 0;
            tangentZ = tangentZ == 0 ? 1 : Integer.signum(tangentZ);
        }
        BlockState log = switch (shoreType) {
            case TROPICAL -> Blocks.JUNGLE_LOG.defaultBlockState();
            case COLD, GLACIAL -> Blocks.SPRUCE_LOG.defaultBlockState();
            default -> Blocks.OAK_LOG.defaultBlockState();
        };
        log = log.setValue(RotatedPillarBlock.AXIS, axis);
        int length = 2 + (int) Math.floor(hashUnit(
                seed, top.getX(), top.getZ(), 0x94D049BB133111EBL) * 3.0);
        for (int offset = 0; offset < length; offset++) {
            int x = top.getX() + tangentX * offset;
            int z = top.getZ() + tangentZ * offset;
            if (!insideOriginChunk(x, z, minimumX, minimumZ)) {
                return false;
            }
            int y = surfaceY(level, x, z);
            BlockPos support = new BlockPos(x, y, z);
            BlockPos position = support.above();
            if (Math.abs(y - top.getY()) > 1
                    || !isNaturalSurface(level.getBlockState(support))
                    || !canWrite(level, position)
                    || !level.getBlockState(position).isAir()) {
                return false;
            }
        }
        for (int offset = 0; offset < length; offset++) {
            int x = top.getX() + tangentX * offset;
            int z = top.getZ() + tangentZ * offset;
            int y = surfaceY(level, x, z);
            level.setBlock(new BlockPos(x, y + 1, z), log, 2);
        }
        return true;
    }

    private static boolean placeShellPatch(
            WorldGenLevel level,
            int minimumX,
            int minimumZ,
            BlockPos top,
            CoastalWaveProfile.ShoreType shoreType,
            long seed
    ) {
        boolean placed = false;
        int count = 1 + (int) Math.floor(hashUnit(
                seed, top.getX(), top.getZ(), 0xBF58476D1CE4E5B9L) * 3.0);
        for (int index = 0; index < count; index++) {
            int x = top.getX() + (index & 1);
            int z = top.getZ() + (index >> 1);
            if (!insideOriginChunk(x, z, minimumX, minimumZ)) {
                continue;
            }
            BlockPos position = new BlockPos(x, surfaceY(level, x, z), z);
            if (!level.getBlockState(position).is(BlockTags.SAND)
                    || !canWrite(level, position)) {
                continue;
            }
            level.setBlock(position,
                    shoreType == CoastalWaveProfile.ShoreType.TROPICAL && index == 0
                            ? Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState()
                            : Blocks.CALCITE.defaultBlockState(),
                    2);
            placed = true;
        }
        return placed;
    }

    private static boolean placeRockCluster(
            WorldGenLevel level,
            int minimumX,
            int minimumZ,
            BlockPos top,
            CoastalWaveProfile.ShoreType shoreType,
            long seed
    ) {
        boolean placed = false;
        int count = 1 + (int) Math.floor(hashUnit(
                seed, top.getX(), top.getZ(), 0x369DEA0F31A53F85L) * 3.0);
        for (int index = 0; index < count; index++) {
            int x = top.getX() + (index & 1);
            int z = top.getZ() + (index >> 1);
            if (!insideOriginChunk(x, z, minimumX, minimumZ)) {
                continue;
            }
            int y = surfaceY(level, x, z);
            BlockPos support = new BlockPos(x, y, z);
            BlockPos position = support.above();
            if (!isNaturalSurface(level.getBlockState(support))
                    || !canWrite(level, position)
                    || !level.getBlockState(position).isAir()) {
                continue;
            }
            BlockState rock = shoreType == CoastalWaveProfile.ShoreType.GLACIAL
                    ? Blocks.BLUE_ICE.defaultBlockState()
                    : index % 2 == 0
                    ? Blocks.ANDESITE.defaultBlockState()
                    : Blocks.COBBLESTONE.defaultBlockState();
            level.setBlock(position, rock, 2);
            placed = true;
        }
        return placed;
    }

    private static boolean placeTidePool(
            WorldGenLevel level,
            int minimumX,
            int minimumZ,
            BlockPos top
    ) {
        int poolY = top.getY();
        for (int offsetZ = 0; offsetZ < 2; offsetZ++) {
            for (int offsetX = 0; offsetX < 2; offsetX++) {
                int x = top.getX() + offsetX;
                int z = top.getZ() + offsetZ;
                BlockPos position = new BlockPos(x, poolY, z);
                if (!insideOriginChunk(x, z, minimumX, minimumZ)
                        || surfaceY(level, x, z) != poolY
                        || !canWrite(level, position)
                        || !isNaturalSurface(level.getBlockState(position))
                        || !level.getBlockState(position.above()).isAir()) {
                    return false;
                }
            }
        }
        for (int offsetZ = 0; offsetZ < 2; offsetZ++) {
            for (int offsetX = 0; offsetX < 2; offsetX++) {
                level.setBlock(
                        new BlockPos(top.getX() + offsetX, poolY, top.getZ() + offsetZ),
                        Blocks.WATER.defaultBlockState(),
                        2
                );
            }
        }
        return true;
    }

    private static boolean placeIceFragment(
            WorldGenLevel level,
            int minimumX,
            int minimumZ,
            BlockPos top,
            long seed
    ) {
        int count = 1 + (int) Math.floor(hashUnit(
                seed, top.getX(), top.getZ(), 0x9E3779B97F4A7C15L) * 3.0);
        boolean placed = false;
        for (int index = 0; index < count; index++) {
            int x = top.getX() + (index & 1);
            int z = top.getZ() + (index >> 1);
            if (!insideOriginChunk(x, z, minimumX, minimumZ)) {
                continue;
            }
            BlockPos position = new BlockPos(x, surfaceY(level, x, z) + 1, z);
            if (!canWrite(level, position) || !level.getBlockState(position).isAir()) {
                continue;
            }
            level.setBlock(position, index == 0
                    ? Blocks.BLUE_ICE.defaultBlockState()
                    : Blocks.PACKED_ICE.defaultBlockState(), 2);
            placed = true;
        }
        return placed;
    }

    private static boolean placeSeaStack(
            WorldGenLevel level,
            int minimumX,
            int minimumZ,
            BlockPos top,
            WaterContact water,
            long seed
    ) {
        int seawardX = water.x() + water.directionX() * 2;
        int seawardZ = water.z() + water.directionZ() * 2;
        if (!insideOriginChunk(seawardX, seawardZ, minimumX, minimumZ)) {
            return false;
        }
        int waterSurfaceY = surfaceY(level, seawardX, seawardZ);
        if (!level.getFluidState(new BlockPos(seawardX, waterSurfaceY, seawardZ))
                .is(FluidTags.WATER)) {
            return false;
        }
        int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, seawardX, seawardZ) - 1;
        int depth = waterSurfaceY - floorY;
        if (depth < 1 || depth > 10) {
            return false;
        }
        int stackTopY = waterSurfaceY + 3 + (int) Math.floor(hashUnit(
                seed, top.getX(), top.getZ(), 0xC2B2AE3D27D4EB4FL) * 5.0);
        boolean placed = false;
        int[][] footprint = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int index = 0; index < footprint.length; index++) {
            int x = seawardX + footprint[index][0];
            int z = seawardZ + footprint[index][1];
            if (!insideOriginChunk(x, z, minimumX, minimumZ)) {
                continue;
            }
            int columnTop = stackTopY - (index == 0 ? 0 : 2 + index % 2);
            for (int y = floorY + 1; y <= columnTop; y++) {
                BlockPos position = new BlockPos(x, y, z);
                BlockState existing = level.getBlockState(position);
                if (!canWrite(level, position)
                        || !(existing.isAir()
                        || level.getFluidState(position).is(FluidTags.WATER)
                        || isNaturalSurface(existing))) {
                    continue;
                }
                level.setBlock(position, (y + index) % 4 == 0
                        ? Blocks.ANDESITE.defaultBlockState()
                        : Blocks.STONE.defaultBlockState(), 2);
                placed = true;
            }
        }
        return placed;
    }

    private static WaterContact nearestWaterContact(
            WorldGenLevel level,
            int originX,
            int originY,
            int originZ
    ) {
        for (int distance = 1; distance <= WATER_SEARCH_DISTANCE; distance++) {
            for (int[] direction : DIRECTIONS) {
                int x = originX + direction[0] * distance;
                int z = originZ + direction[1] * distance;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int surfaceY = surfaceY(level, x, z);
                BlockPos position = new BlockPos(x, surfaceY, z);
                if (Math.abs(surfaceY - originY) <= 8
                        && level.getFluidState(position).is(FluidTags.WATER)) {
                    return new WaterContact(
                            distance, direction[0], direction[1], x, z);
                }
            }
        }
        return null;
    }

    private static int surfaceY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
    }

    private static boolean canWrite(WorldGenLevel level, BlockPos position) {
        return position.getY() >= level.getMinBuildHeight()
                && position.getY() < level.getMaxBuildHeight()
                && level.ensureCanWrite(position);
    }

    private static boolean insideOriginChunk(
            int x,
            int z,
            int minimumX,
            int minimumZ
    ) {
        return x >= minimumX && x < minimumX + 16
                && z >= minimumZ && z < minimumZ + 16;
    }

    private static boolean isNaturalSurface(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE);
    }

    private static double hashUnit(long seed, int x, int z, long salt) {
        long value = seed ^ salt
                ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private record WaterContact(
            int distance,
            int directionX,
            int directionZ,
            int x,
            int z
    ) {
    }
}
