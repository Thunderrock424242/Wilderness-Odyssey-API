package com.thunder.wildernessodysseyapi.Cloak;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber
public class CloakToggleHandler {
    @SubscribeEvent
    public static void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack heldItem = event.getItemStack();

        // Check if the player is holding an Amethyst Crystal
        if (heldItem.getItem() == Items.AMETHYST_SHARD) {
            event.setCanceled(true); // Prevent default use action

            // Toggle cloak state
            player.getCapability(YourMod.CLOAK_CAPABILITY).ifPresent(cloak -> {
                boolean newState = !cloak.isCloakEnabled();
                cloak.setCloakEnabled(newState);

                // Send feedback message
                player.sendSystemMessage(Component.literal("Cloak " + (newState ? "enabled!" : "disabled!"))
                        .withStyle(newState ? ChatFormatting.AQUA : ChatFormatting.RED));

                // Play a sound effect
                player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);

                // Consume one Amethyst Crystal
                if (!player.isCreative()) {
                    heldItem.shrink(1);
                }
            });
        }
    }
}