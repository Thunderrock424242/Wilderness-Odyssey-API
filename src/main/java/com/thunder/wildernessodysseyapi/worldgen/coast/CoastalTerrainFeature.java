package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
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
 * Owns the six custom beaches' natural surface bands and bounded details.
 * Vanilla's exact-key beach surface rules do not handle these biome identifiers.
 * A placement owns only its origin chunk and never loads a neighboring chunk.
 */
public final class CoastalTerrainFeature extends Feature<NoneFeatureConfiguration> {
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    public CoastalTerrainFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        boolean terrain = CoastalWorldgenConfig.ENABLE_COASTAL_TERRAIN_BANDS.get();
        boolean details = CoastalWorldgenConfig.ENABLE_COASTAL_DETAILS.get()
                && CoastalWorldgenConfig.COASTAL_DETAIL_DENSITY.get() > 0.0;
        if (!CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY.get() || (!terrain && !details)) return false;
        try {
            return placeChunk(context.level(), context.origin(), terrain, details);
        } catch (RuntimeException exception) {
            if (LOGGED_FAILURE.compareAndSet(false, true)) {
                ModConstants.LOGGER.error("Coastal terrain generation failed at {}; subsequent failures use normal worldgen diagnostics",
                        context.origin(), exception);
            }
            return false;
        }
    }

    private static boolean placeChunk(WorldGenLevel level, BlockPos origin, boolean terrain, boolean details) {
        int minimumX = origin.getX() & ~15;
        int minimumZ = origin.getZ() & ~15;
        var chunk = level.getChunk(minimumX >> 4, minimumZ >> 4);
        // The same guard covers earthwork AND decorations. Structure foundations
        // may use natural materials, and references may name an unavailable start.
        if (!chunk.getAllStarts().isEmpty()
                || chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty())) {
            return false;
        }
        CoastalGenerationArea area = new CoastalGenerationArea(level, minimumX, minimumZ);
        boolean placed = false;
        if (terrain) {
            for (int z = minimumZ; z < minimumZ + 16; z++) {
                for (int x = minimumX; x < minimumX + 16; x++) {
                    var type = area.shoreType(x, z);
                    var water = area.contact(x, z);
                    BlockPos top = area.surface(x, z);
                    if (type == null || water == null) continue;
                    if (area.column(x, z).water()) {
                        if (type == CoastalWaveProfile.ShoreType.TROPICAL) {
                            placed |= resurfaceTropicalSeabed(level, area, x, z);
                        }
                        continue;
                    }
                    if (!CoastalGenerationArea.natural(level.getBlockState(top))) continue;
                    double weight = area.biomeWeight(x, z);
                    double broad = CoastalTerrainProfile.noise(level.getSeed(), x, z, 29, 0xA0761D6478BD642FL);
                    double fine = CoastalTerrainProfile.noise(level.getSeed(), x, z, 9, 0xE7037ED1A0B428DBL);
                    if (weight == 0.0 || (weight < 1.0 && broad * 0.8 + fine * 0.2 > weight * 2.0 - 1.0)) continue;
                    var zone = CoastalTerrainProfile.zone(type, water.distance(), broad);
                    if (type == CoastalWaveProfile.ShoreType.TROPICAL) {
                        top = gradeTropicalBank(level, area, top, water.distance(), weight);
                        area.surface(top);
                    }
                    int rise = (int) Math.floor(weight * CoastalTerrainProfile.duneHeight(type,
                            water.distance(), broad, fine, CoastalWorldgenConfig.MAX_DUNE_RISE_BLOCKS.get()));
                    placed |= resurface(level, area, top, type, zone, broad * 0.8 + fine * 0.2, rise);
                }
            }
        }
        if (details) placed |= CoastalDetailPlacer.place(level, area);
        return placed;
    }

    /** Continues beach sand below shallow water without changing its depth or fluid blocks. */
    private static boolean resurfaceTropicalSeabed(WorldGenLevel level, CoastalGenerationArea area, int x, int z) {
        int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        int waterDepth = area.column(x, z).y() - floorY;
        BlockPos floor = new BlockPos(x, floorY, z);
        if (waterDepth < 1 || waterDepth > 4
                || !level.getFluidState(floor.above()).is(FluidTags.WATER)) {
            return false;
        }
        // The shared preflight requires four solid natural cells. It rejects
        // caves and construction before writing, and never fills the water above.
        return resurface(level, area, floor, CoastalWaveProfile.ShoreType.TROPICAL,
                CoastalTerrainProfile.Zone.OPEN_BEACH, 0.0, 0);
    }

    private static BlockPos gradeTropicalBank(WorldGenLevel level, CoastalGenerationArea area,
                                             BlockPos top, double distance, double weight) {
        int target = CoastalTerrainProfile.tropicalSurfaceHeight(top.getY(), level.getSeaLevel(),
                (int) Math.round(distance), CoastalWorldgenConfig.MAX_TROPICAL_BANK_CUT_BLOCKS.get());
        target = top.getY() - (int) Math.floor((top.getY() - target) * weight);
        if (target == top.getY() || !level.getBlockState(top.above()).isAir()) return top;
        for (int y = target - 3; y <= top.getY(); y++) {
            BlockPos pos = new BlockPos(top.getX(), y, top.getZ());
            BlockState state = level.getBlockState(pos);
            if (!area.canWrite(pos) || !CoastalGenerationArea.natural(state)
                    || !state.getFluidState().isEmpty()) return top;
        }
        for (int y = top.getY(); y > target; y--) {
            level.setBlock(new BlockPos(top.getX(), y, top.getZ()), Blocks.AIR.defaultBlockState(), 2);
        }
        return new BlockPos(top.getX(), target, top.getZ());
    }

    private static boolean resurface(WorldGenLevel level, CoastalGenerationArea area, BlockPos top,
                                     CoastalWaveProfile.ShoreType type, CoastalTerrainProfile.Zone zone,
                                     double noise, int rise) {
        // Preflight the whole foundation and raised column: never leave floating
        // sand above a cave or cap an existing tree, plant, or construction.
        for (int depth = 0; depth <= 3; depth++) {
            BlockPos pos = top.below(depth);
            if (!area.canWrite(pos) || !CoastalGenerationArea.natural(level.getBlockState(pos))) return false;
        }
        for (int offset = 1; offset <= rise; offset++) {
            BlockPos pos = top.above(offset);
            if (!area.canWrite(pos) || !level.getBlockState(pos).isAir()) return false;
        }
        BlockState surface = surfaceState(type, zone, noise);
        BlockState foundation = foundationState(surface, type);
        level.setBlock(top, surface, 2);
        for (int depth = 1; depth <= 3; depth++) level.setBlock(top.below(depth), foundation, 2);
        for (int offset = 1; offset <= rise; offset++) {
            level.setBlock(top.above(offset), Blocks.SAND.defaultBlockState(), 2);
        }
        BlockPos finalTop = top.above(rise);
        area.surface(finalTop);
        BlockPos snow = finalTop.above();
        boolean cold = type == CoastalWaveProfile.ShoreType.COLD;
        boolean glacial = type == CoastalWaveProfile.ShoreType.GLACIAL;
        double snowThreshold = cold ? 0.05 : -0.65;
        if ((cold || glacial) && noise > snowThreshold && area.canWrite(snow)
                && level.getBlockState(snow).isAir()) {
            level.setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,
                    1 + (int) Math.min(3, Math.max(0, (noise + 1.0) * (glacial ? 2.0 : 1.0)))), 2);
        }
        return true;
    }

    private static BlockState surfaceState(CoastalWaveProfile.ShoreType type,
                                           CoastalTerrainProfile.Zone zone, double noise) {
        return switch (zone) {
            case STRANDLINE, OPEN_BEACH, DUNE -> type == CoastalWaveProfile.ShoreType.DUNE && noise > 0.72
                    ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
            case COASTAL_MEADOW, SNOWY_MEADOW -> Blocks.GRASS_BLOCK.defaultBlockState();
            case ROCKY_STRAND -> (noise > 0.12 ? Blocks.GRAVEL : Blocks.STONE).defaultBlockState();
            case ROCKY_SLOPE -> (noise > 0.36 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
            case GRAVEL_STRAND -> (noise > -0.55 ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState();
            case COLD_BEACH -> (noise > 0.25 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState();
            case ICE_STRAND -> (noise > 0.16 ? Blocks.PACKED_ICE : Blocks.GRAVEL).defaultBlockState();
            case GLACIAL_BEACH -> (noise > 0.32 ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE).defaultBlockState();
            case SNOWFIELD -> (noise < -0.55 ? Blocks.STONE : Blocks.SNOW_BLOCK).defaultBlockState();
        };
    }

    private static BlockState foundationState(BlockState surface, CoastalWaveProfile.ShoreType type) {
        if (surface.is(BlockTags.SAND)) return (surface.is(Blocks.RED_SAND)
                ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE).defaultBlockState();
        if (surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.COARSE_DIRT)) return Blocks.DIRT.defaultBlockState();
        if (surface.is(Blocks.SNOW_BLOCK) || (type == CoastalWaveProfile.ShoreType.GLACIAL
                && surface.is(Blocks.PACKED_ICE))) return Blocks.PACKED_ICE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }
}
