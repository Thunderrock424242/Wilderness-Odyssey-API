package com.thunder.wildernessodysseyapi.SkyBeam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEffects {
    private static final int BLAST_RADIUS = 6;
    private static final int FIRE_RADIUS = 8;
    private static final int MAGMA_SPAWN_CHANCE = 30;

    public static void destroyBlocks(ServerLevel world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-BLAST_RADIUS, -1, -BLAST_RADIUS),
                pos.offset(BLAST_RADIUS, 2, BLAST_RADIUS))) {
            BlockState state = world.getBlockState(blockPos);

            if (state.is(Blocks.TNT) || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL)) {
                world.destroyBlock(blockPos, true);
            } else if (state.getBlock() != Blocks.BEDROCK && state.getBlock() != Blocks.OBSIDIAN
                    && world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    public static void igniteArea(ServerLevel world, BlockPos pos) {
        RandomSource random = world.random;
        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-FIRE_RADIUS, -1, -FIRE_RADIUS),
                pos.offset(FIRE_RADIUS, 2, FIRE_RADIUS))) {
            if (random.nextInt(100) < MAGMA_SPAWN_CHANCE) {
                if (world.getBlockState(blockPos).isAir()) {
                    world.setBlock(blockPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                }
            } else {
                world.setBlock(blockPos, Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }
}