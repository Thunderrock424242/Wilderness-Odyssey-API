package com.thunder.wildernessodysseyapi.Cloak;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID,value = Dist.CLIENT)
public class CloakToggleHandler {

    private static boolean wasKeyPressed = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Player player = mc.player;

        // Check if the off-hand swap key (default “F”) is pressed
        boolean isKeyPressed = mc.options.keySwapOffhand.isDown();

        if (isKeyPressed && !wasKeyPressed) {
            ItemStack offHandItem = player.getOffhandItem();

            // If they’re holding an Amethyst Shard in off-hand, toggle a boolean in NBT
            if (offHandItem.getItem() == Items.AMETHYST_SHARD) {
                // Read/write directly from player.getPersistentData()
                CompoundTag persistent = player.getPersistentData();
                boolean newState = !persistent.getBoolean("cloakEnabled");
                persistent.putBoolean("cloakEnabled", newState);

                // Feedback: chat message
                player.sendSystemMessage(
                        Component.literal("Cloak " + (newState ? "enabled!" : "disabled!"))
                                .withStyle(newState ? ChatFormatting.AQUA : ChatFormatting.RED)
                );

                // Play chime sound
                player.level().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F
                );

                // Consume one shard if not in Creative
                if (!player.isCreative()) {
                    offHandItem.shrink(1);
                }
            }
        }

        wasKeyPressed = isKeyPressed;
    }
}
