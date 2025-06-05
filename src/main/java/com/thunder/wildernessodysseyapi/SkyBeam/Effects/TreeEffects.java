package com.thunder.wildernessodysseyapi.SkyBeam.Effects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public class TreeEffects {
    private static final int CHAR_TREE_RADIUS = 10;

    public static void charTrees(ServerLevel world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-CHAR_TREE_RADIUS, -1, -CHAR_TREE_RADIUS),
                pos.offset(CHAR_TREE_RADIUS, 2, CHAR_TREE_RADIUS))) {

            BlockState state = world.getBlockState(blockPos);
            Block block = state.getBlock();

            if (state.is(BlockTags.LOGS)) {
                // Replace log with "charred" block - you can replace with a custom charred log if desired
                world.setBlock(blockPos, Blocks.POLISHED_BASALT.defaultBlockState(), 3);
            } else if (block instanceof LeavesBlock) {
                world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
