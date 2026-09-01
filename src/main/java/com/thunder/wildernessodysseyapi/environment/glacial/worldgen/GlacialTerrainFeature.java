package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Builds distinct glacial silhouettes, wind-shaped snow, compressed ice, and coastal shelves. */
public final class GlacialTerrainFeature extends Feature<NoneFeatureConfiguration> {

    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState SNOW_LAYER = Blocks.SNOW.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();

    public GlacialTerrainFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()) {
            return false;
        }
        try {
            return placeTerrain(context.level(), context.origin());
        } catch (RuntimeException exception) {
            GlacialFeatureSupport.logFailure("terrain", context.origin(), exception);
            return false;
        }
    }

    private static boolean placeTerrain(WorldGenLevel level, BlockPos origin) {
        int minimumX = origin.getX() & ~15;
        int minimumZ = origin.getZ() & ~15;
        boolean placed = false;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int x = minimumX + localX;
                int z = minimumZ + localZ;
                int surfaceY = GlacialFeatureSupport.surfaceY(level, x, z);
                BlockPos surface = new BlockPos(x, surfaceY, z);
                GlacialBiomeManager.Family family = GlacialFeatureSupport.family(level, surface);
                if (family == null) {
                    continue;
                }
                if (level.getFluidState(surface).is(FluidTags.WATER)) {
                    placed |= resurfaceCoastFloor(level, family, x, z);
                    placed |= placeShelfIce(level, family, x, z);
                    continue;
                }
                if (!GlacialFeatureSupport.naturalTerrain(level.getBlockState(surface))) {
                    continue;
                }
                placed |= buildIceColumn(level, family, x, surfaceY, z);
            }
        }
        return placed;
    }

    private static boolean buildIceColumn(
            WorldGenLevel level,
            GlacialBiomeManager.Family family,
            int x,
            int originalSurfaceY,
            int z
    ) {
        double broad = GlacialFeatureSupport.noise(level.getSeed(), x, z, 48, 0x4F1BBCDCBFA54001L);
        double ridge = Math.abs(GlacialFeatureSupport.noise(level.getSeed(), x, z, 19, 0x6A09E667F3BCC909L));
        double signature = switch (family) {
            case GLACIAL_HIGHLANDS -> Math.max(0.0, GlacialFeatureSupport.noise(
                    level.getSeed(), x, z, 92, 0x1F83D9ABFB41BD6BL
            ));
            case GLACIAL_BASIN -> Math.abs(GlacialFeatureSupport.noise(
                    level.getSeed(), x + z, z - x, 96, 0x5BE0CD19137E2179L
            ));
            case POLAR_ICE_SHEET, MELTWATER_VALLEY, ICEBERG_COAST -> 0.0;
        };
        int baseDepth = switch (family) {
            case POLAR_ICE_SHEET -> 10;
            case GLACIAL_HIGHLANDS -> 8;
            case GLACIAL_BASIN -> 9;
            case MELTWATER_VALLEY -> 6;
            case ICEBERG_COAST -> 4;
        };
        int rise = GlacialTerrainProfile.iceRise(family, broad, ridge, signature);
        int depth = baseDepth + (int) Math.round((ridge + 1.0) * 2.0);
        int topY = Math.min(level.getMaxBuildHeight() - 2, originalSurfaceY + rise);
        int bottomY = Math.max(level.getMinBuildHeight(), originalSurfaceY - depth + 1);

        // Very steep highland faces retain occasional dark rock windows instead
        // of becoming uniform ice mountains.
        int chunkMinimumX = x & ~15;
        int chunkMinimumZ = z & ~15;
        int localRelief = Math.abs(
                GlacialFeatureSupport.surfaceY(level, Math.min(chunkMinimumX + 15, x + 1), z)
                        - GlacialFeatureSupport.surfaceY(level, Math.max(chunkMinimumX, x - 1), z)
        ) + Math.abs(
                GlacialFeatureSupport.surfaceY(level, x, Math.min(chunkMinimumZ + 15, z + 1))
                        - GlacialFeatureSupport.surfaceY(level, x, Math.max(chunkMinimumZ, z - 1))
        );
        boolean rockButtress = family == GlacialBiomeManager.Family.GLACIAL_HIGHLANDS
                && localRelief >= 8
                && Math.floorMod(GlacialFeatureSupport.mix(
                        level.getSeed() ^ BlockPos.asLong(x, topY, z)
                ), 6L) == 0L;
        int rockFaceDepth = Math.min(8, 2 + localRelief / 3);

        for (int y = bottomY; y <= topY; y++) {
            BlockPos position = new BlockPos(x, y, z);
            BlockState existing = level.getBlockState(position);
            if (y <= originalSurfaceY && !GlacialFeatureSupport.naturalTerrain(existing)) {
                continue;
            }
            if (y > originalSurfaceY && !existing.isAir() && !existing.canBeReplaced()) {
                continue;
            }
            int belowTop = topY - y;
            BlockState replacement;
            if (rockButtress && belowTop <= rockFaceDepth) {
                long rockPattern = GlacialFeatureSupport.mix(
                        level.getSeed() ^ BlockPos.asLong(x, y, z) ^ 0xCBBB9D5DC1059ED8L
                );
                replacement = (rockPattern & 3L) == 0L
                        ? Blocks.TUFF.defaultBlockState()
                        : Blocks.STONE.defaultBlockState();
            } else if (belowTop == 0) {
                replacement = SNOW_BLOCK;
            } else {
                boolean compressedCore = belowTop >= Math.max(5, depth / 2)
                        && GlacialFeatureSupport.noise(
                                level.getSeed(), x + y * 3, z - y * 2, 11, 0xBB67AE8584CAA73BL
                        ) > 0.18;
                boolean blueStriation = (family == GlacialBiomeManager.Family.GLACIAL_HIGHLANDS
                        || family == GlacialBiomeManager.Family.GLACIAL_BASIN)
                        && belowTop >= 2
                        && GlacialFeatureSupport.noise(
                                level.getSeed(), x + y * 5, z - y * 3, 17, 0x629A292A367CD507L
                        ) > 0.58;
                replacement = compressedCore || blueStriation ? BLUE_ICE : PACKED_ICE;
            }
            GlacialFeatureSupport.set(level, position, replacement);
        }
        BlockPos snowLayer = new BlockPos(x, topY + 1, z);
        double broadDrift = GlacialFeatureSupport.noise(
                level.getSeed(), x + z, z - x, 27, 0x3C6EF372FE94F82BL
        );
        double fineDrift = GlacialFeatureSupport.noise(
                level.getSeed(), x, z, 7, 0x9B05688C2B3E6C1FL
        );
        if (!rockButtress && level.getBlockState(snowLayer).isAir() && broadDrift > -0.72) {
            int layers = GlacialTerrainProfile.snowLayers(family, broadDrift, fineDrift);
            GlacialFeatureSupport.set(
                    level,
                    snowLayer,
                    SNOW_LAYER.setValue(SnowLayerBlock.LAYERS, layers)
            );
        }
        return true;
    }

    private static boolean resurfaceCoastFloor(
            WorldGenLevel level,
            GlacialBiomeManager.Family family,
            int x,
            int z
    ) {
        if (family != GlacialBiomeManager.Family.ICEBERG_COAST) {
            return false;
        }
        int floorY = GlacialFeatureSupport.oceanFloorY(level, x, z);
        for (int depth = 0; depth < 4; depth++) {
            BlockPos position = new BlockPos(x, floorY - depth, z);
            if (GlacialFeatureSupport.naturalTerrain(level.getBlockState(position))) {
                GlacialFeatureSupport.set(level, position, depth >= 2 ? BLUE_ICE : PACKED_ICE);
            }
        }
        return true;
    }

    private static boolean placeShelfIce(
            WorldGenLevel level,
            GlacialBiomeManager.Family family,
            int x,
            int z
    ) {
        if (family != GlacialBiomeManager.Family.ICEBERG_COAST) {
            return false;
        }
        int seaLevel = level.getSeaLevel();
        int floorY = GlacialFeatureSupport.oceanFloorY(level, x, z);
        if (seaLevel - floorY > 7
                || GlacialFeatureSupport.noise(level.getSeed(), x, z, 26, 0xA54FF53A5F1D36F1L) < 0.48) {
            return false;
        }
        BlockPos surface = new BlockPos(x, seaLevel, z);
        if (level.getFluidState(surface).is(FluidTags.WATER)
                || level.getFluidState(surface.below()).is(FluidTags.WATER)) {
            GlacialFeatureSupport.set(level, surface, Blocks.ICE.defaultBlockState());
            return true;
        }
        return false;
    }
}
