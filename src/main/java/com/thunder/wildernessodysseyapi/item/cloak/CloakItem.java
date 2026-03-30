package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.ticktoklib.api.TickTokAPI;
import com.thunder.wildernessodysseyapi.curios.CuriosIntegration;
import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Handheld toggle item that enables/disables player cloak state when required
 * dependencies are present.
 */
public class CloakItem extends Item {
    private static final int CLOAK_TOGGLE_COOLDOWN_TICKS = TickTokAPI.toTicks(1);

    public CloakItem(Properties properties) {
        super(properties);
    }

    /**
     * Toggles cloaking on the server after validating compass and cloak-chip
     * requirements.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (!hasCompassLink(player)) {
            player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cloak_requires_compass"), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!hasCloakChip(player)) {
            player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cloak_requires_chip"), true);
            return InteractionResultHolder.fail(stack);
        }

        boolean enable = !CloakState.isCloaked(player);
        CloakState.setCloaked(player, enable);

        if (enable) {
            CloakState.applyCloak(player);
            player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cloak_enabled"), true);
        } else {
            CloakState.clearCloak(player);
            player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cloak_disabled"), true);
        }

        player.getCooldowns().addCooldown(this, CLOAK_TOGGLE_COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
    }

    public static boolean hasCompassLink(Player player) {
        return player.getOffhandItem().is(Items.COMPASS) || player.getMainHandItem().is(Items.COMPASS);
    }

    /**
     * @return {@code true} when the player has a cloak chip equipped in Curios.
     */
    public static boolean hasCloakChip(Player player) {
        return CuriosIntegration.isEquipped(player, ModItems.CLOAK_CHIP.get());
    }

    /**
     * @return {@code true} when the player is currently holding the cloak item.
     */
    public static boolean isHoldingCloak(Player player) {
        return player.getMainHandItem().is(ModItems.CLOAK_ITEM.get())
                || player.getOffhandItem().is(ModItems.CLOAK_ITEM.get());
    }
}
