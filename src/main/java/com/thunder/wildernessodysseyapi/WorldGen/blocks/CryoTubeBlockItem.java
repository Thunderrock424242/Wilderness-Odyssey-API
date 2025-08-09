package com.thunder.wildernessodysseyapi.WorldGen.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;

/**
 * Block item for the cryo tube that places the tube one block higher
 * than the targeted position to account for the model's vertical offset.
 */
public class CryoTubeBlockItem extends BlockItem {
    public CryoTubeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace()).above();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), Direction.UP, pos, false);
        BlockPlaceContext offset = new BlockPlaceContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), hit);
        return super.place(offset);
    }
}
