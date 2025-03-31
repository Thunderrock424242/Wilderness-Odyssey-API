package com.thunder.wildernessodysseyapi.worldgen.features;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

public class MeteorCraterFeature extends Feature<NoneFeatureConfiguration> {
    private static boolean spawned = false;

    public MeteorCraterFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(@NotNull FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (spawned) return false;
        spawned = true;

        BlockPos center = context.origin();
        LevelAccessor level = context.level();
        RandomSource random = context.random();

        int radius = 8;
        int depth = 4;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= radius) {
                    for (int y = -depth; y <= 0; y++) {
                        BlockPos pos = center.offset(x, y, z);
                        if (dist + (y / 1.5) < radius) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            if (random.nextFloat() < 0.15f && y == -1) {
                                level.setBlock(pos, Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
                            } else if (random.nextFloat() < 0.1f) {
                                level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                            } else if (y == -1) {
                                level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
