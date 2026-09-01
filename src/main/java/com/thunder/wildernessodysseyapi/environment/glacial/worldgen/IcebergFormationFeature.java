package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Builds significantly varied icebergs with underwater mass, compressed cores, cracks, and rare caves. */
public final class IcebergFormationFeature extends Feature<NoneFeatureConfiguration> {

    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState SNOW_LAYER = Blocks.SNOW.defaultBlockState();

    public IcebergFormationFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()
                || !GlacialConfig.ENABLE_ICEBERG_COAST.get()) {
            return false;
        }
        try {
            return build(context.level(), context.origin(), context.random());
        } catch (RuntimeException exception) {
            GlacialFeatureSupport.logFailure("iceberg", context.origin(), exception);
            return false;
        }
    }

    private static boolean build(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int seaLevel = level.getSeaLevel();
        int centerX = origin.getX();
        int centerZ = origin.getZ();
        BlockPos waterProbe = new BlockPos(centerX, seaLevel - 1, centerZ);
        if (GlacialFeatureSupport.family(level, waterProbe) != GlacialBiomeManager.Family.ICEBERG_COAST
                || !level.getFluidState(waterProbe).is(FluidTags.WATER)) {
            return false;
        }
        GlacialFeatureSupport.StructureGuard structures = GlacialFeatureSupport.structureGuard(level);

        boolean colossal = random.nextInt(9) == 0;
        boolean tabular = colossal || random.nextInt(3) == 0;
        int radiusX;
        if (colossal) {
            radiusX = 12 + random.nextInt(6);
        } else if (tabular) {
            radiusX = 7 + random.nextInt(6);
        } else {
            radiusX = 4 + random.nextInt(7);
        }
        int radiusZ = Math.max(4, radiusX - 3 + random.nextInt(7));
        int height;
        if (colossal) {
            height = 15 + random.nextInt(10);
        } else if (tabular) {
            height = 8 + random.nextInt(7);
        } else {
            height = 7 + random.nextInt(15);
        }
        int underwaterDepth = colossal ? 14 + random.nextInt(11) : 6 + random.nextInt(12);
        int calvedFace = random.nextInt(4);
        int calvedX = calvedFace == 0 ? 1 : calvedFace == 1 ? -1 : 0;
        int calvedZ = calvedFace == 2 ? 1 : calvedFace == 3 ? -1 : 0;
        double leanX = (random.nextDouble() - 0.5) * (tabular ? 0.08 : 0.20);
        double leanZ = (random.nextDouble() - 0.5) * (tabular ? 0.08 : 0.20);
        int changes = 0;
        for (int y = -underwaterDepth; y <= height; y++) {
            double vertical = y >= 0
                    ? y / (double) Math.max(1, height)
                    : -y / (double) Math.max(1, underwaterDepth);
            double horizontalScale;
            if (y < 0) {
                horizontalScale = 0.72 + vertical * 0.42;
            } else if (tabular) {
                horizontalScale = Math.max(0.55, 1.0 - Math.pow(vertical, 5.0) * 0.45);
            } else {
                horizontalScale = Math.max(0.18, 1.0 - vertical * 0.78);
            }
            int localRadiusX = Math.max(1, (int) Math.ceil(radiusX * horizontalScale));
            int localRadiusZ = Math.max(1, (int) Math.ceil(radiusZ * horizontalScale));
            int layerCenterX = centerX + (int) Math.round(leanX * y);
            int layerCenterZ = centerZ + (int) Math.round(leanZ * y);
            for (int x = -localRadiusX; x <= localRadiusX; x++) {
                for (int z = -localRadiusZ; z <= localRadiusZ; z++) {
                    double ellipse = square(x / (double) localRadiusX)
                            + square(z / (double) localRadiusZ);
                    double irregularity = GlacialFeatureSupport.noise(
                            level.getSeed(), layerCenterX + x + y * 2, layerCenterZ + z - y,
                            tabular ? 13 : 9, 0x510E527FADE682D1L
                    ) * (tabular ? 0.09 : 0.16);
                    if (ellipse > 1.0 + irregularity) {
                        continue;
                    }
                    double faceCoordinate = calvedX != 0
                            ? x / (double) localRadiusX * calvedX
                            : z / (double) localRadiusZ * calvedZ;
                    if (tabular && y >= 0 && faceCoordinate > 0.68 + irregularity * 0.45) {
                        continue;
                    }
                    BlockPos position = new BlockPos(layerCenterX + x, seaLevel + y, layerCenterZ + z);
                    if (structures.contains(position)) {
                        continue;
                    }
                    BlockState existing = level.getBlockState(position);
                    if (y < 0 && !existing.isAir() && !level.getFluidState(position).is(FluidTags.WATER)
                            && !existing.is(Blocks.ICE)) {
                        continue;
                    }
                    if (y >= 0 && !existing.isAir() && !existing.canBeReplaced()
                            && !level.getFluidState(position).is(FluidTags.WATER)) {
                        continue;
                    }
                    boolean core = ellipse < 0.34 && (y < height * 0.65 || y < 0);
                    boolean blueBand = y < 0
                            && ellipse > 0.42
                            && Math.floorMod(y, 6) == 0
                            && GlacialFeatureSupport.noise(
                                    level.getSeed(), position.getX(), position.getZ(), 11,
                                    0x7137449123EF65CDL
                            ) > 0.12;
                    GlacialFeatureSupport.set(level, position, core || blueBand ? BLUE_ICE : PACKED_ICE);
                    changes++;
                }
            }
        }

        if (radiusX >= 10 && GlacialConfig.ENABLE_GLACIER_ICE_CAVES.get()) {
            carveCave(
                    level,
                    structures,
                    centerX,
                    seaLevel + Math.max(2, height / 4),
                    centerZ,
                    radiusX,
                    radiusZ
            );
        }
        if (radiusX >= 8 && random.nextBoolean()) {
            carveSurfaceCleft(
                    level,
                    structures,
                    centerX,
                    centerZ,
                    seaLevel,
                    height,
                    radiusX,
                    radiusZ,
                    random
            );
        }
        int skewX = (int) Math.ceil(Math.abs(leanX) * height) + 2;
        int skewZ = (int) Math.ceil(Math.abs(leanZ) * height) + 2;
        capWithSnow(
                level,
                structures,
                centerX,
                centerZ,
                seaLevel,
                height,
                radiusX + skewX,
                radiusZ + skewZ,
                random.nextLong()
        );
        placeCrackedShelf(
                level,
                structures,
                centerX,
                centerZ,
                seaLevel,
                radiusX,
                radiusZ,
                random.nextLong()
        );
        return changes > 0;
    }

    private static void carveCave(
            WorldGenLevel level,
            GlacialFeatureSupport.StructureGuard structures,
            int centerX,
            int centerY,
            int centerZ,
            int radiusX,
            int radiusZ
    ) {
        int tunnel = Math.max(3, Math.min(radiusX, radiusZ) / 2);
        for (int x = -tunnel; x <= tunnel; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (square(y / 2.5) + square(z / 2.5) > 1.0) {
                        continue;
                    }
                    BlockPos position = new BlockPos(centerX + x, centerY + y, centerZ + z);
                    if (!structures.contains(position)) {
                        GlacialFeatureSupport.set(level, position, AIR);
                    }
                }
            }
        }
    }

    private static void placeCrackedShelf(
            WorldGenLevel level,
            GlacialFeatureSupport.StructureGuard structures,
            int centerX,
            int centerZ,
            int seaLevel,
            int radiusX,
            int radiusZ,
            long crackSalt
    ) {
        int shelfRadius = Math.max(radiusX, radiusZ) + 3;
        for (int x = -shelfRadius; x <= shelfRadius; x++) {
            for (int z = -shelfRadius; z <= shelfRadius; z++) {
                double distance = Math.sqrt((double) x * x + (double) z * z);
                if (distance < Math.min(radiusX, radiusZ) * 0.75
                        || distance > shelfRadius) {
                    continue;
                }
                double primaryCrack = Math.abs(GlacialFeatureSupport.noise(
                        level.getSeed(), centerX + x, centerZ + z, 7,
                        crackSalt ^ 0xB5C0FBCFEC4D3B2FL
                ));
                double crossingCrack = Math.abs(GlacialFeatureSupport.noise(
                        level.getSeed(), centerX + x + centerZ + z,
                        centerZ + z - centerX - x,
                        11,
                        crackSalt ^ 0xE9B5DBA58189DBBCL
                ));
                if (primaryCrack < 0.10 || crossingCrack < 0.065) {
                    continue;
                }
                BlockPos position = new BlockPos(centerX + x, seaLevel, centerZ + z);
                if (structures.contains(position)) {
                    continue;
                }
                if (level.getFluidState(position).is(FluidTags.WATER)
                        || level.getFluidState(position.below()).is(FluidTags.WATER)) {
                    GlacialFeatureSupport.set(level, position, ICE);
                }
            }
        }
    }

    private static void carveSurfaceCleft(
            WorldGenLevel level,
            GlacialFeatureSupport.StructureGuard structures,
            int centerX,
            int centerZ,
            int seaLevel,
            int height,
            int radiusX,
            int radiusZ,
            RandomSource random
    ) {
        double angle = random.nextDouble() * Math.PI;
        double forwardX = Math.cos(angle);
        double forwardZ = Math.sin(angle);
        int length = Math.max(4, Math.min(radiusX, radiusZ) - 2);
        int depth = 4 + random.nextInt(Math.max(2, Math.min(8, height / 2)));
        for (int step = -length; step <= length; step++) {
            int x = centerX + (int) Math.round(forwardX * step);
            int z = centerZ + (int) Math.round(forwardZ * step);
            int top = highestIce(level, x, z, seaLevel, seaLevel + height + 2);
            if (top < seaLevel) {
                continue;
            }
            int localDepth = Math.max(2, depth - Math.abs(step) / 2);
            for (int y = top; y >= top - localDepth; y--) {
                BlockPos position = new BlockPos(x, y, z);
                if (!structures.contains(position)
                        && (level.getBlockState(position).is(Blocks.PACKED_ICE)
                        || level.getBlockState(position).is(Blocks.BLUE_ICE))) {
                    GlacialFeatureSupport.set(level, position, AIR);
                }
            }
        }
    }

    private static void capWithSnow(
            WorldGenLevel level,
            GlacialFeatureSupport.StructureGuard structures,
            int centerX,
            int centerZ,
            int seaLevel,
            int height,
            int radiusX,
            int radiusZ,
            long snowSalt
    ) {
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                int worldX = centerX + x;
                int worldZ = centerZ + z;
                int top = highestIce(level, worldX, worldZ, seaLevel, seaLevel + height + 2);
                if (top < seaLevel) {
                    continue;
                }
                BlockPos topPosition = new BlockPos(worldX, top, worldZ);
                if (structures.contains(topPosition)) {
                    continue;
                }
                GlacialFeatureSupport.set(level, topPosition, SNOW_BLOCK);
                BlockPos drift = topPosition.above();
                if (structures.contains(drift)
                        || (!level.getBlockState(drift).isAir()
                        && !level.getBlockState(drift).canBeReplaced())) {
                    continue;
                }
                double driftNoise = GlacialFeatureSupport.noise(
                        level.getSeed(), worldX + worldZ, worldZ - worldX, 9, snowSalt
                );
                int layers = 1 + (int) Math.floor((driftNoise + 1.0) * 1.5);
                layers = Math.max(1, Math.min(4, layers));
                GlacialFeatureSupport.set(
                        level,
                        drift,
                        SNOW_LAYER.setValue(SnowLayerBlock.LAYERS, layers)
                );
            }
        }
    }

    private static int highestIce(
            WorldGenLevel level,
            int x,
            int z,
            int minimumY,
            int maximumY
    ) {
        for (int y = maximumY; y >= minimumY; y--) {
            BlockPos position = new BlockPos(x, y, z);
            if (!GlacialFeatureSupport.canWrite(level, position)) {
                continue;
            }
            BlockState state = level.getBlockState(position);
            if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
                return y;
            }
        }
        return minimumY - 1;
    }

    private static double square(double value) {
        return value * value;
    }
}
