package com.thunder.wildernessodysseyapi.temporalrift.item;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class TimeCapsuleBlockItem extends BlockItem {
    public TimeCapsuleBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!context.getLevel().dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)) {
            if (!context.getLevel().isClientSide && context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.literal("Time Capsules can only be placed in The Before."));
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
