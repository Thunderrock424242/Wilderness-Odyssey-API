package com.thunder.wildernessodysseyapi.item.cloak;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class InhalerItem extends Item {
    public InhalerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        CloakState.reduceBreathPenalty(player);
        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.inhaler_used"), true);
        return InteractionResultHolder.success(stack);
    }
}
