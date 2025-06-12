package com.thunder.wildernessodysseyapi.SkyBeam.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BlockPalette {
    public static void charTrees(ServerLevel w, BlockPos c, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(
                c.offset(-radius, -1, -radius),
                c.offset(radius, 2, radius))) {
            BlockState bs = w.getBlockState(p);
            if (bs.is(BlockTags.LOGS)) {
                w.setBlock(p, Blocks.COAL_BLOCK.defaultBlockState(), 3);
            } else if (bs.getBlock() instanceof LeavesBlock) {
                w.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
