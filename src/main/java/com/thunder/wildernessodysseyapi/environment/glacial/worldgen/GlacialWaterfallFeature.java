package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Places narrow frozen cliff curtains; ordinary ice is the exact reversible seasonal surface. */
public final class GlacialWaterfallFeature extends Feature<NoneFeatureConfiguration> {

    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();

    public GlacialWaterfallFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()
                || !GlacialConfig.ENABLE_GLACIAL_WATERFALLS.get()) {
            return false;
        }
        try {
            return placeCurtain(context.level(), context.origin(), context.random());
        } catch (RuntimeException exception) {
            GlacialFeatureSupport.logFailure("waterfall", context.origin(), exception);
            return false;
        }
    }

    private static boolean placeCurtain(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int minimumX = (origin.getX() & ~15) + 2;
        int minimumZ = (origin.getZ() & ~15) + 2;
        int maximumX = minimumX + 11;
        int maximumZ = minimumZ + 11;
        GlacialFeatureSupport.StructureGuard structures = GlacialFeatureSupport.structureGuard(level);
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = minimumX + random.nextInt(maximumX - minimumX + 1);
            int z = minimumZ + random.nextInt(maximumZ - minimumZ + 1);
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int lowX = x + direction.getStepX() * 4;
            int lowZ = z + direction.getStepZ() * 4;
            if (!GlacialFeatureSupport.canWrite(
                    level,
                    new BlockPos(lowX, level.getSeaLevel(), lowZ)
            )) {
                continue;
            }
            int highY = GlacialFeatureSupport.surfaceY(level, x, z);
            int lowY = GlacialFeatureSupport.surfaceY(level, lowX, lowZ);
            GlacialBiomeManager.Family family = GlacialFeatureSupport.family(level, new BlockPos(x, highY, z));
            if ((family != GlacialBiomeManager.Family.GLACIAL_HIGHLANDS
                    && family != GlacialBiomeManager.Family.GLACIAL_BASIN)
                    || highY - lowY < 7) {
                continue;
            }
            int width = 2 + random.nextInt(3);
            int drop = Math.min(28, highY - lowY);
            int changes = 0;
            Direction side = direction.getClockWise();
            for (int depth = 1; depth < drop; depth++) {
                double progress = depth / (double) drop;
                int localWidth = Math.max(1, (int) Math.round(width * (1.0 - progress * 0.45)));
                int outward = 2 + (int) Math.round(Math.sin(progress * Math.PI));
                int curtainCenterX = x + direction.getStepX() * outward;
                int curtainCenterZ = z + direction.getStepZ() * outward;
                int y = highY - depth;
                for (int lateral = -localWidth; lateral <= localWidth; lateral++) {
                    int curtainX = curtainCenterX + side.getStepX() * lateral;
                    int curtainZ = curtainCenterZ + side.getStepZ() * lateral;
                    BlockPos position = new BlockPos(curtainX, y, curtainZ);
                    if (structures.contains(position)) {
                        continue;
                    }
                    BlockState existing = level.getBlockState(position);
                    if (!existing.isAir() && !existing.canBeReplaced()) {
                        continue;
                    }
                    GlacialFeatureSupport.set(
                            level,
                            position,
                            lateral == 0 && depth > 2 ? BLUE_ICE : ICE
                    );
                    changes++;
                }
            }
            changes += placeFrozenApron(level, structures, lowX, lowY, lowZ, width);
            return changes > 0;
        }
        return false;
    }

    private static int placeFrozenApron(
            WorldGenLevel level,
            GlacialFeatureSupport.StructureGuard structures,
            int centerX,
            int surfaceY,
            int centerZ,
            int radius
    ) {
        int changes = 0;
        int apronRadius = radius + 2;
        for (int x = -apronRadius; x <= apronRadius; x++) {
            for (int z = -apronRadius; z <= apronRadius; z++) {
                double distance = ((double) x * x + (double) z * z)
                        / (apronRadius * (double) apronRadius);
                if (distance > 1.0) {
                    continue;
                }
                BlockPos position = new BlockPos(centerX + x, surfaceY + 1, centerZ + z);
                if (structures.contains(position)) {
                    continue;
                }
                BlockState existing = level.getBlockState(position);
                if (!existing.isAir() && !existing.canBeReplaced()) {
                    continue;
                }
                if (level.getBlockState(position.below()).isAir()) {
                    continue;
                }
                GlacialFeatureSupport.set(
                        level,
                        position,
                        distance < 0.16 ? BLUE_ICE : ICE
                );
                changes++;
            }
        }
        return changes;
    }
}
