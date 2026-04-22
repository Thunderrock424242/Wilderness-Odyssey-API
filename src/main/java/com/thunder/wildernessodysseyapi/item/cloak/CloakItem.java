package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.ticktoklib.api.TickTokAPI;
import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Handheld cloak focus item. The actual cloaking behavior is now controlled by
 * holding the breath key while this item is held.
 */
public class CloakItem extends Item {
    private static final int CLOAK_TOGGLE_COOLDOWN_TICKS = TickTokAPI.toTicks(1);

    public CloakItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cloak_hold_breath_hint"), true);
        player.getCooldowns().addCooldown(this, CLOAK_TOGGLE_COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
    }

    /**
     * @return {@code true} when the player is currently holding the cloak item.
     */
    public static boolean isHoldingCloak(Player player) {
        return player.getMainHandItem().is(ModItems.CLOAK_ITEM.get())
                || player.getOffhandItem().is(ModItems.CLOAK_ITEM.get());
    }
}
