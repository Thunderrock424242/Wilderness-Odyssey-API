package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.quest.integration.LoreQuestBridge;
import com.thunder.wildernessodysseyapi.quest.objective.ItemObservation;
import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgressStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashSet;

/** Server fact adapters. Possession reconciles actual carried slots and never consumes items. */
public final class QuestObjectiveEvents {
    private QuestObjectiveEvents() { }
    @SubscribeEvent public static void tags(net.neoforged.neoforge.event.TagsUpdatedEvent event) { QuestServerEvents.tagsUpdated(event); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) QuestServerEvents.login(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) QuestServerEvents.logout(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            new QuestPlayerProgressStore().copy(event.getOriginal(), player);
            QuestServerEvents.forget(player);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) QuestServerEvents.login(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Refresh presence before inventory can unlock a quest in the destination dimension.
            QuestServerEvents.observe(player, new QuestEvent.Dimension(player.level().dimension().location()));
            seed(player);
        }
    }
    @SubscribeEvent public static void pickedUp(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) inventory(player);
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) inventory(player);
    }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) inventory(player);
    }
    public static void seed(ServerPlayer player) {
        inventory(player);
        QuestServerEvents.observe(player, new QuestEvent.Dimension(player.level().dimension().location()));
        LoreBookManager.getCollected(player).forEach(id -> LoreQuestBridge.onCollected(player, id));
    }
    public static void inventory(ServerPlayer player) {
        QuestServerEvents.observe(player, new QuestEvent.Possession(captureInventory(player)));
    }
    public static ItemObservation captureInventory(ServerPlayer player) {
        if (player.getServer() == null || !player.getServer().isSameThread()) throw new IllegalStateException("Quest inventory capture requires the server thread.");
        var stacks = new ArrayList<ItemObservation.Stack>();
        var inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            var stack = inventory.getItem(index); if (stack.isEmpty()) continue;
            var tags = new HashSet<net.minecraft.resources.ResourceLocation>(); stack.getTags().forEach(tag -> tags.add(tag.location()));
            stacks.add(new ItemObservation.Stack(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), tags));
        }
        return new ItemObservation(stacks);
    }
}
