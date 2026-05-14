package com.thunder.wildernessodysseyapi.item;

import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.network.OpenCodexPayload;
import com.thunder.wildernessodysseyapi.network.SyncLoreBookPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class FieldCodexItem extends Item {
    public FieldCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            for (String collectedId : LoreBookManager.getCollected(serverPlayer)) {
                PacketDistributor.sendToPlayer(serverPlayer, new SyncLoreBookPayload(collectedId));
            }
            PacketDistributor.sendToPlayer(serverPlayer, new OpenCodexPayload(true));
        }
        return InteractionResultHolder.success(stack);
    }
}
