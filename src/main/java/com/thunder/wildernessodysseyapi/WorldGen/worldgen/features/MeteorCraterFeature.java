package com.thunder.wildernessodysseyapi.WorldGen.worldgen.features;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/****
 * MeteorCraterFeature for the Wilderness Odyssey API mod.
 */
public class MeteorCraterFeature extends Feature<NoneFeatureConfiguration> {
    private static boolean spawned = false;

    public MeteorCraterFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Ensure it only ever spawns once:
        if (spawned) return false;
        spawned = true;

        BlockPos center = context.origin();
        LevelAccessor level = context.level();
        RandomSource random = context.random();

        int radius = 8;
        int depth  = 4;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= radius) {
                    for (int dy = -depth; dy <= 0; dy++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (dist + (dy / 1.5) < radius) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);

                            if (random.nextFloat() < 0.15f && dy == -1) {
                                level.setBlock(pos, Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
                            } else if (random.nextFloat() < 0.10f) {
                                level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                            } else if (dy == -1) {
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
