package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

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

/** Carves variable-width, variable-depth glacier fissures after structure placement. */
public final class GlacialCrevasseFeature extends Feature<NoneFeatureConfiguration> {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    public GlacialCrevasseFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()
                || !GlacialConfig.ENABLE_GLACIER_CREVASSES.get()) {
            return false;
        }
        try {
            return carve(context.level(), context.origin(), context.random());
        } catch (RuntimeException exception) {
            GlacialFeatureSupport.logFailure("crevasse", context.origin(), exception);
            return false;
        }
    }

    private static boolean carve(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int minimumX = origin.getX() & ~15;
        int minimumZ = origin.getZ() & ~15;
        GlacialFeatureSupport.StructureGuard structures = GlacialFeatureSupport.structureGuard(level);
        double angle = random.nextDouble() * Math.PI;
        double forwardX = Math.cos(angle);
        double forwardZ = Math.sin(angle);
        double sideX = -forwardZ;
        double sideZ = forwardX;
        int length = 10 + random.nextInt(17);
        int width = 1 + random.nextInt(4);
        int baseDepth = 4 + random.nextInt(20);
        double curvePhase = random.nextDouble() * Math.PI * 2.0;
        if (random.nextInt(14) == 0) {
            baseDepth += 24 + random.nextInt(25);
        }

        int changes = 0;
        for (int step = -length / 2; step <= length / 2; step++) {
            double curve = Math.sin(step * 0.34 + curvePhase) * (0.65 + width * 0.38);
            int centerX = Mth.clamp(
                    (int) Math.round(origin.getX() + forwardX * step + sideX * curve),
                    minimumX + 1,
                    minimumX + 14
            );
            int centerZ = Mth.clamp(
                    (int) Math.round(origin.getZ() + forwardZ * step + sideZ * curve),
                    minimumZ + 1,
                    minimumZ + 14
            );
            double taper = 1.0 - Math.abs(step) / (length * 0.58);
            double pulse = 0.82 + Math.sin(step * 0.71 + curvePhase) * 0.18;
            int localWidth = Math.max(1, (int) Math.round(width * Math.max(0.35, taper) * pulse));
            int localDepth = Math.max(3, (int) Math.round(
                    baseDepth * Math.max(0.45, taper) * (0.88 + pulse * 0.12)
            ));
            for (int side = -localWidth; side <= localWidth; side++) {
                int x = Mth.clamp(
                        (int) Math.round(centerX + sideX * side),
                        minimumX + 1,
                        minimumX + 14
                );
                int z = Mth.clamp(
                        (int) Math.round(centerZ + sideZ * side),
                        minimumZ + 1,
                        minimumZ + 14
                );
                int surfaceY = GlacialFeatureSupport.surfaceY(level, x, z);
                BlockPos surface = new BlockPos(x, surfaceY, z);
                if (structures.contains(surface)) {
                    continue;
                }
                int bottomY = Math.max(level.getMinBuildHeight() + 5, surfaceY - localDepth);
                for (int y = surfaceY; y >= bottomY; y--) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (structures.contains(position)) {
                        break;
                    }
                    BlockState existing = level.getBlockState(position);
                    if (GlacialFeatureSupport.carvable(existing)) {
                        GlacialFeatureSupport.set(level, position, AIR);
                        changes++;
                    }
                    if (Math.abs(side) == localWidth) {
                        accentWall(level, position.offset(
                                (int) Math.signum(side) * (int) Math.round(sideX),
                                0,
                                (int) Math.signum(side) * (int) Math.round(sideZ)
                        ), surfaceY - y);
                    }
                }
                if (side == 0 && GlacialConfig.ENABLE_GLACIAL_RIVERS.get()
                        && random.nextInt(6) == 0) {
                    GlacialFeatureSupport.set(level, new BlockPos(x, bottomY, z), WATER);
                }
            }
        }
        return changes > 0;
    }

    private static void accentWall(WorldGenLevel level, BlockPos position, int depth) {
        BlockState existing = level.getBlockState(position);
        if (!GlacialFeatureSupport.carvable(existing)) {
            return;
        }
        GlacialFeatureSupport.set(level, position, depth >= 6 ? BLUE_ICE : PACKED_ICE);
    }
}
