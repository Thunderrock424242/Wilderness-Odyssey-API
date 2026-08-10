package com.thunder.wildernessodysseyapi.lorebook.item;

import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.lorebook.network.OpenCodexPayload;
import com.thunder.wildernessodysseyapi.lorebook.network.SyncCodexJournalPayload;
import com.thunder.wildernessodysseyapi.lorebook.network.SyncLoreBookPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opens the player's writable journal and collected lore-journal library.
 *
 * <p>The server sends authoritative journal and unlock state before asking the
 * client to construct the screen.</p>
 */
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
            PacketDistributor.sendToPlayer(serverPlayer,
                    new SyncCodexJournalPayload(LoreBookManager.getJournalText(serverPlayer)));
            PacketDistributor.sendToPlayer(serverPlayer, new OpenCodexPayload(true));
        }
        return InteractionResultHolder.success(stack);
    }
}
