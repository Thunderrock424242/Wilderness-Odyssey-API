package com.thunder.wildernessodysseyapi.quest.reward;

import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Typed server effects. Capacity is checked before insertion; no overflow item is dropped into the world. */
public final class QuestRewardDelivery {
    public boolean canDeliver(ServerPlayer player, RewardDefinition reward) {
        requireThread(player);
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        return switch (reward.value()) {
            case RewardDefinition.Item value -> {
                if (!BuiltInRegistries.ITEM.containsKey(value.item())) yield false;
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(value.item()), value.count());
                if (stack.is(Items.AIR) || value.count() < 1 || value.count() > 64) yield false;
                yield canFit(player, stack);
            }
            case RewardDefinition.Experience value -> value.amount() > 0 && value.amount() <= 1_000_000;
            case RewardDefinition.Lore value -> value.lore().getNamespace().equals("wildernessodysseyapi")
                    && LoreBookManager.config().books().stream().anyMatch(book -> value.lore().getPath().equals(book.id()));
        };
    }

    public QuestRewardLedger.DeliveryOutcome deliver(ServerPlayer player, RewardDefinition reward) {
        requireThread(player);
        if (!canDeliver(player, reward)) return QuestRewardLedger.DeliveryOutcome.NOT_APPLIED;
        switch (reward.value()) {
            case RewardDefinition.Item value -> {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(value.item()), value.count());
                player.getInventory().add(stack);
                player.inventoryMenu.broadcastChanges();
                if (!stack.isEmpty()) return QuestRewardLedger.DeliveryOutcome.UNKNOWN;
            }
            case RewardDefinition.Experience value -> player.giveExperiencePoints(value.amount());
            case RewardDefinition.Lore value -> LoreBookManager.markCollected(player, value.lore().getPath());
        }
        return QuestRewardLedger.DeliveryOutcome.DELIVERED;
    }

    public static boolean canFit(ServerPlayer player, ItemStack offered) {
        int remaining = offered.getCount();
        for (var slot : player.getInventory().items) {
            if (slot.isEmpty()) remaining -= Math.min(offered.getMaxStackSize(), player.getInventory().getMaxStackSize());
            else if (ItemStack.isSameItemSameComponents(slot, offered)) {
                remaining -= Math.max(0, Math.min(slot.getMaxStackSize(), player.getInventory().getMaxStackSize()) - slot.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static void requireThread(ServerPlayer player) {
        if (player.getServer() == null || !player.getServer().isSameThread()) throw new IllegalStateException("Quest effects require the server thread.");
    }
}
