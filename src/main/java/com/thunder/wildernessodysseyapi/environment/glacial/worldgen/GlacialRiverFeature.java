package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Carves one short terrain-following glacial stream segment within the owning chunk. */
public final class GlacialRiverFeature extends Feature<NoneFeatureConfiguration> {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();

    public GlacialRiverFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()
                || !GlacialConfig.ENABLE_GLACIAL_RIVERS.get()) {
            return false;
        }
        try {
            return carve(context.level(), context.origin(), context.random().nextLong());
        } catch (RuntimeException exception) {
            GlacialFeatureSupport.logFailure("river", context.origin(), exception);
            return false;
        }
    }

    private static boolean carve(WorldGenLevel level, BlockPos origin, long randomSalt) {
        int minimumX = origin.getX() & ~15;
        int minimumZ = origin.getZ() & ~15;
        GlacialFeatureSupport.StructureGuard structures = GlacialFeatureSupport.structureGuard(level);
        int startX = clamp(origin.getX(), minimumX + 3, minimumX + 12);
        int startZ = clamp(origin.getZ(), minimumZ + 3, minimumZ + 12);
        int[][] targets = {
                {startX, minimumZ},
                {startX, minimumZ + 15},
                {minimumX, startZ},
                {minimumX + 15, startZ}
        };
        int targetX = targets[0][0];
        int targetZ = targets[0][1];
        int targetHeight = Integer.MAX_VALUE;
        for (int[] target : targets) {
            int height = GlacialFeatureSupport.surfaceY(level, target[0], target[1]);
            if (height < targetHeight) {
                targetHeight = height;
                targetX = target[0];
                targetZ = target[1];
            }
        }

        GlacialBiomeManager.Family family = GlacialFeatureSupport.family(
                level,
                new BlockPos(startX, GlacialFeatureSupport.surfaceY(level, startX, startZ), startZ)
        );
        if (family != GlacialBiomeManager.Family.MELTWATER_VALLEY
                && family != GlacialBiomeManager.Family.GLACIAL_BASIN
                && family != GlacialBiomeManager.Family.GLACIAL_HIGHLANDS) {
            return false;
        }

        int changes = 0;
        int steps = 18;
        for (int step = 0; step <= steps; step++) {
            double amount = step / (double) steps;
            double bend = Math.sin(amount * Math.PI) * (((randomSalt >>> 8) & 7L) - 3L) * 0.45;
            int x = clamp(
                    (int) Math.round(startX + (targetX - startX) * amount + (targetZ == startZ ? 0.0 : bend)),
                    minimumX + 1,
                    minimumX + 14
            );
            int z = clamp(
                    (int) Math.round(startZ + (targetZ - startZ) * amount + (targetX == startX ? 0.0 : bend)),
                    minimumZ + 1,
                    minimumZ + 14
            );
            int width = 1 + (int) Math.floorMod(GlacialFeatureSupport.mix(randomSalt + step), 3L);
            int surfaceY = GlacialFeatureSupport.surfaceY(level, x, z);
            for (int offset = -width; offset <= width; offset++) {
                int channelX = targetX == startX ? x + offset : x;
                int channelZ = targetZ == startZ ? z + offset : z;
                BlockPos top = new BlockPos(channelX, surfaceY, channelZ);
                if (structures.contains(top)
                        || !GlacialFeatureSupport.naturalTerrain(level.getBlockState(top))) {
                    continue;
                }
                int channelY = surfaceY - 1;
                GlacialFeatureSupport.set(level, top, AIR);
                GlacialFeatureSupport.set(level, new BlockPos(channelX, channelY - 1, channelZ), GRAVEL);
                GlacialFeatureSupport.set(level, new BlockPos(channelX, channelY, channelZ), WATER);
                if (step % 7 == 4 && offset == 0) {
                    GlacialFeatureSupport.set(level, top, PACKED_ICE);
                }
                changes++;
            }
        }
        return changes > 0;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
