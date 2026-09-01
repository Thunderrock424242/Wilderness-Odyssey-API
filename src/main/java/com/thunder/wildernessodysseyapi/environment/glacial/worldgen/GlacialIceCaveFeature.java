package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Creates bounded curved ice tunnels and occasional compressed blue-ice chambers. */
public final class GlacialIceCaveFeature extends Feature<NoneFeatureConfiguration> {

    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    public GlacialIceCaveFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()
                || !GlacialConfig.ENABLE_GLACIER_ICE_CAVES.get()) {
            return false;
        }
        try {
            return carve(context.level(), context.origin(), context.random());
        } catch (RuntimeException exception) {
            GlacialFeatureSupport.logFailure("ice cave", context.origin(), exception);
            return false;
        }
    }

    private static boolean carve(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int surfaceY = GlacialFeatureSupport.surfaceY(level, origin.getX(), origin.getZ());
        GlacialBiomeManager.Family family = GlacialFeatureSupport.family(
                level,
                new BlockPos(origin.getX(), surfaceY, origin.getZ())
        );
        if (family == null || family == GlacialBiomeManager.Family.ICEBERG_COAST) {
            return false;
        }
        int minimumX = (origin.getX() & ~15) + 1;
        int minimumZ = (origin.getZ() & ~15) + 1;
        int maximumX = minimumX + 13;
        int maximumZ = minimumZ + 13;
        GlacialFeatureSupport.StructureGuard structures = GlacialFeatureSupport.structureGuard(level);
        int centerX = Mth.clamp(origin.getX(), minimumX + 3, maximumX - 3);
        int centerZ = Mth.clamp(origin.getZ(), minimumZ + 3, maximumZ - 3);
        int centerY = Math.max(level.getMinBuildHeight() + 9, surfaceY - 14 - random.nextInt(26));
        int segments = 3 + random.nextInt(4);
        int changes = 0;
        for (int segment = 0; segment < segments; segment++) {
            int radiusX = 3 + random.nextInt(segment == segments - 1 && random.nextInt(10) == 0 ? 6 : 3);
            int radiusY = 2 + random.nextInt(3);
            int radiusZ = 3 + random.nextInt(segment == segments - 1 && random.nextInt(10) == 0 ? 6 : 3);
            changes += carveEllipsoid(
                    level,
                    structures,
                    new BlockPos(centerX, centerY, centerZ),
                    radiusX,
                    radiusY,
                    radiusZ,
                    random,
                    minimumX,
                    maximumX,
                    minimumZ,
                    maximumZ
            );
            centerX = Mth.clamp(centerX + random.nextInt(7) - 3, minimumX + 3, maximumX - 3);
            centerZ = Mth.clamp(centerZ + random.nextInt(7) - 3, minimumZ + 3, maximumZ - 3);
            centerY = Mth.clamp(centerY + random.nextInt(5) - 2, level.getMinBuildHeight() + 7, surfaceY - 6);
        }
        return changes > 0;
    }

    private static int carveEllipsoid(
            WorldGenLevel level,
            GlacialFeatureSupport.StructureGuard structures,
            BlockPos center,
            int radiusX,
            int radiusY,
            int radiusZ,
            RandomSource random,
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ
    ) {
        int changes = 0;
        for (int x = -radiusX; x <= radiusX; x++) {
            int worldX = center.getX() + x;
            if (worldX < minimumX || worldX > maximumX) {
                continue;
            }
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    int worldZ = center.getZ() + z;
                    if (worldZ < minimumZ || worldZ > maximumZ) {
                        continue;
                    }
                    double distance = square(x / (double) radiusX)
                            + square(y / (double) radiusY)
                            + square(z / (double) radiusZ);
                    if (distance > 1.0) {
                        continue;
                    }
                    BlockPos position = new BlockPos(worldX, center.getY() + y, worldZ);
                    if (structures.contains(position)) {
                        continue;
                    }
                    BlockState existing = level.getBlockState(position);
                    if (!GlacialFeatureSupport.carvable(existing)) {
                        continue;
                    }
                    if (distance >= 0.68) {
                        boolean compressionBand = Math.floorMod(position.getY(), 5) == 0
                                || GlacialFeatureSupport.noise(
                                        level.getSeed(), worldX, worldZ, 9,
                                        0xD807AA98A3030242L
                                ) > 0.62;
                        GlacialFeatureSupport.set(
                                level,
                                position,
                                compressionBand ? BLUE_ICE : PACKED_ICE
                        );
                    } else if (y == -radiusY + 1
                            && GlacialConfig.ENABLE_GLACIAL_RIVERS.get()
                            && random.nextInt(18) == 0) {
                        GlacialFeatureSupport.set(level, position, WATER);
                    } else {
                        GlacialFeatureSupport.set(level, position, AIR);
                    }
                    changes++;
                }
            }
        }
        changes += placeIceColumns(
                level,
                structures,
                center,
                radiusX,
                radiusY,
                radiusZ,
                random,
                minimumX,
                maximumX,
                minimumZ,
                maximumZ
        );
        return changes;
    }

    private static int placeIceColumns(
            WorldGenLevel level,
            GlacialFeatureSupport.StructureGuard structures,
            BlockPos center,
            int radiusX,
            int radiusY,
            int radiusZ,
            RandomSource random,
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ
    ) {
        int changes = 0;
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = Mth.clamp(
                    center.getX() + random.nextInt(Math.max(1, radiusX * 2 - 1)) - radiusX + 1,
                    minimumX + 1,
                    maximumX - 1
            );
            int z = Mth.clamp(
                    center.getZ() + random.nextInt(Math.max(1, radiusZ * 2 - 1)) - radiusZ + 1,
                    minimumZ + 1,
                    maximumZ - 1
            );
            int length = 1 + random.nextInt(Math.max(1, Math.min(3, radiusY)));
            BlockPos ceiling = new BlockPos(x, center.getY() + radiusY - 1, z);
            if (level.getBlockState(ceiling).isAir()
                    && !level.getBlockState(ceiling.above()).isAir()) {
                for (int offset = 0; offset < length; offset++) {
                    BlockPos position = ceiling.below(offset);
                    if (!level.getBlockState(position).isAir()
                            || structures.contains(position)) {
                        break;
                    }
                    GlacialFeatureSupport.set(level, position, BLUE_ICE);
                    changes++;
                }
            }
            BlockPos floor = new BlockPos(x, center.getY() - radiusY + 1, z);
            if (level.getBlockState(floor).isAir()
                    && !level.getBlockState(floor.below()).isAir()) {
                for (int offset = 0; offset < length; offset++) {
                    BlockPos position = floor.above(offset);
                    if (!level.getBlockState(position).isAir()
                            || structures.contains(position)) {
                        break;
                    }
                    GlacialFeatureSupport.set(level, position, offset == 0 ? PACKED_ICE : BLUE_ICE);
                    changes++;
                }
            }
        }
        return changes;
    }

    private static double square(double value) {
        return value * value;
    }
}
