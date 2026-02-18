package com.thunder.wildernessodysseyapi.worldgen.blocks;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Custom block item that normalises the cryo tube placement so it behaves like
 * a regular block despite the model using an offset origin.
 */
public class CryoTubeBlockItem extends BlockItem {
    public CryoTubeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        BlockHitResult hit = new BlockHitResult(
                context.getClickLocation(),
                context.getClickedFace(),
                context.getClickedPos().relative(context.getClickedFace()),
                false
        );
        BlockPlaceContext adjustedContext = new BlockPlaceContext(
                context.getLevel(),
                context.getPlayer(),
                context.getHand(),
                context.getItemInHand(),
                hit
        );
        return super.place(adjustedContext);
    }
}
