package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import com.thunder.wildernessodysseyapi.worldgen.coast.config.CoastalWorldgenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Supplies explicit surface ownership for Wilderness beach biome identifiers.
 *
 * <p>Vanilla's overworld surface rule checks exact vanilla beach keys, so a
 * custom biome cannot rely on that rule to receive sand or gravel. This feature
 * scans only its 16 by 16 origin chunk, reads only available worldgen-region
 * neighbors, and writes bounded natural surface columns.</p>
 */
public final class CoastalTerrainFeature extends Feature<NoneFeatureConfiguration> {

    private static final int WATER_SEARCH_DISTANCE = 14;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    public CoastalTerrainFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        boolean terrainEnabled = CoastalWorldgenConfig.ENABLE_COASTAL_TERRAIN_BANDS.get();
        boolean detailsEnabled = CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS.get()
                && CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY.get() > 0.0;
        if (!CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY.get()
                || (!terrainEnabled && !detailsEnabled)) {
            return false;
        }
        try {
            return placeChunk(context.level(), context.origin());
        } catch (RuntimeException exception) {
            if (LOGGED_FAILURE.compareAndSet(false, true)) {
                ModConstants.LOGGER.error(
                        "Coastal terrain generation failed at {}; subsequent failures use normal worldgen diagnostics",
                        context.origin(),
                        exception
                );
            }
            return false;
        }
    }

    private static boolean placeChunk(WorldGenLevel level, BlockPos origin) {
        int minimumX = origin.getX() & ~15;
        int minimumZ = origin.getZ() & ~15;
        boolean placed = false;
        if (CoastalWorldgenConfig.ENABLE_COASTAL_TERRAIN_BANDS.get()) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int x = minimumX + localX;
                    int z = minimumZ + localZ;
                    int topY = surfaceY(level, x, z);
                    BlockPos top = new BlockPos(x, topY, z);
                    CoastalWaveProfile.ShoreType shoreType = ModBiomes
                            .coastalShoreType(level.getBiome(top))
                            .orElse(null);
                    if (shoreType == null || level.getFluidState(top).is(FluidTags.WATER)) {
                        continue;
                    }
                    BlockState existing = level.getBlockState(top);
                    if (!isNaturalSurface(existing)) {
                        continue;
                    }

                    int distanceToWater = nearestWaterDistance(level, x, topY, z);
                    if (distanceToWater > WATER_SEARCH_DISTANCE) {
                        // Replacement beaches remain transitions even at a chunk
                        // edge where the worldgen region cannot expose the ocean.
                        distanceToWater = Math.min(
                                WATER_SEARCH_DISTANCE,
                                7 + Math.max(0, topY - level.getSeaLevel()) * 2
                        );
                    }
                    CoastalTerrainProfile.Zone zone = CoastalTerrainProfile.zone(
                            shoreType, distanceToWater);
                    double broadNoise = noise(
                            level.getSeed(), x, z, 19, 0xA0761D6478BD642FL);
                    placed |= resurface(level, top, shoreType, zone, broadNoise);
                }
            }
        }
        if (CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS.get()
                && CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY.get() > 0.0) {
            placed |= CoastalDetailPlacer.place(level, minimumX, minimumZ);
        }
        return placed;
    }

    private static boolean resurface(
            WorldGenLevel level,
            BlockPos top,
            CoastalWaveProfile.ShoreType shoreType,
            CoastalTerrainProfile.Zone zone,
            double broadNoise
    ) {
        BlockState surface = surfaceState(shoreType, zone, broadNoise);
        BlockState foundation = foundationState(surface, shoreType);
        boolean changed = setNatural(level, top, surface);
        for (int depth = 1; depth <= 3; depth++) {
            changed |= setNatural(level, top.below(depth), foundation);
        }

        int rise = CoastalTerrainProfile.duneRise(
                shoreType,
                zone,
                broadNoise,
                CoastalWorldgenConfig.MAX_DUNE_RISE_BLOCKS.get()
        );
        for (int offset = 1; offset <= rise; offset++) {
            BlockPos raised = top.above(offset);
            if (!canWrite(level, raised) || !level.getBlockState(raised).isAir()) {
                break;
            }
            level.setBlock(raised, Blocks.SAND.defaultBlockState(), 2);
            changed = true;
        }

        BlockPos decoration = top.above(rise + 1);
        if ((shoreType == CoastalWaveProfile.ShoreType.COLD
                || shoreType == CoastalWaveProfile.ShoreType.GLACIAL)
                && canWrite(level, decoration)
                && level.getBlockState(decoration).isAir()
                && broadNoise > -0.32) {
            int layers = 1 + (int) Math.floor(Math.min(0.999, (broadNoise + 1.0) * 0.5) * 3.0);
            level.setBlock(
                    decoration,
                    Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers),
                    2
            );
            changed = true;
        }
        if (shoreType == CoastalWaveProfile.ShoreType.GLACIAL
                && (zone == CoastalTerrainProfile.Zone.ICE_STRAND
                || zone == CoastalTerrainProfile.Zone.GLACIAL_BEACH)
                && broadNoise > 0.76
                && canWrite(level, decoration)
                && level.getBlockState(decoration).isAir()) {
            level.setBlock(decoration, Blocks.PACKED_ICE.defaultBlockState(), 2);
            changed = true;
        }
        return changed;
    }

    private static BlockState surfaceState(
            CoastalWaveProfile.ShoreType shoreType,
            CoastalTerrainProfile.Zone zone,
            double noise
    ) {
        return switch (zone) {
            case STRANDLINE, OPEN_BEACH, DUNE -> shoreType == CoastalWaveProfile.ShoreType.DUNE
                    && noise > 0.72
                    ? Blocks.RED_SAND.defaultBlockState()
                    : Blocks.SAND.defaultBlockState();
            case COASTAL_MEADOW -> Blocks.GRASS_BLOCK.defaultBlockState();
            case ROCKY_STRAND -> noise > 0.30
                    ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.STONE.defaultBlockState();
            case ROCKY_SLOPE -> noise > 0.48
                    ? Blocks.ANDESITE.defaultBlockState()
                    : Blocks.STONE.defaultBlockState();
            case GRAVEL_STRAND -> noise > 0.20
                    ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.SAND.defaultBlockState();
            case COLD_BEACH -> noise > 0.42
                    ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.COARSE_DIRT.defaultBlockState();
            case SNOWY_MEADOW -> Blocks.GRASS_BLOCK.defaultBlockState();
            case ICE_STRAND -> noise > 0.18
                    ? Blocks.PACKED_ICE.defaultBlockState()
                    : Blocks.GRAVEL.defaultBlockState();
            case GLACIAL_BEACH -> noise > 0.35
                    ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.PACKED_ICE.defaultBlockState();
            case SNOWFIELD -> Blocks.SNOW_BLOCK.defaultBlockState();
        };
    }

    private static BlockState foundationState(
            BlockState surface,
            CoastalWaveProfile.ShoreType shoreType
    ) {
        if (surface.is(BlockTags.SAND)) {
            return surface.is(Blocks.RED_SAND)
                    ? Blocks.RED_SANDSTONE.defaultBlockState()
                    : Blocks.SANDSTONE.defaultBlockState();
        }
        if (surface.is(Blocks.GRASS_BLOCK)
                || surface.is(Blocks.COARSE_DIRT)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (surface.is(Blocks.SNOW_BLOCK)) {
            return shoreType == CoastalWaveProfile.ShoreType.GLACIAL
                    ? Blocks.PACKED_ICE.defaultBlockState()
                    : Blocks.DIRT.defaultBlockState();
        }
        if (shoreType == CoastalWaveProfile.ShoreType.GLACIAL
                && surface.is(Blocks.PACKED_ICE)) {
            return Blocks.BLUE_ICE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private static int nearestWaterDistance(
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
                if (Math.abs(surfaceY - originY) <= 8
                        && level.getFluidState(new BlockPos(x, surfaceY, z)).is(FluidTags.WATER)) {
                    return distance;
                }
            }
        }
        return WATER_SEARCH_DISTANCE + 1;
    }

    private static int surfaceY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
    }

    private static boolean setNatural(
            WorldGenLevel level,
            BlockPos position,
            BlockState replacement
    ) {
        if (!canWrite(level, position) || !isNaturalSurface(level.getBlockState(position))) {
            return false;
        }
        level.setBlock(position, replacement, 2);
        return true;
    }

    private static boolean canWrite(WorldGenLevel level, BlockPos position) {
        return position.getY() >= level.getMinBuildHeight()
                && position.getY() < level.getMaxBuildHeight()
                && level.ensureCanWrite(position);
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

    private static double noise(long seed, int x, int z, int scale, long salt) {
        int safeScale = Math.max(2, scale);
        int cellX = Math.floorDiv(x, safeScale);
        int cellZ = Math.floorDiv(z, safeScale);
        double fractionX = Math.floorMod(x, safeScale) / (double) safeScale;
        double fractionZ = Math.floorMod(z, safeScale) / (double) safeScale;
        double smoothX = smoothStep(fractionX);
        double smoothZ = smoothStep(fractionZ);
        double northWest = lattice(seed, cellX, cellZ, salt);
        double northEast = lattice(seed, cellX + 1, cellZ, salt);
        double southWest = lattice(seed, cellX, cellZ + 1, salt);
        double southEast = lattice(seed, cellX + 1, cellZ + 1, salt);
        return lerp(lerp(northWest, northEast, smoothX),
                lerp(southWest, southEast, smoothX), smoothZ);
    }

    private static double lattice(long seed, int cellX, int cellZ, long salt) {
        long value = seed ^ salt
                ^ (long) cellX * 0x9E3779B97F4A7C15L
                ^ (long) cellZ * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

}
}
