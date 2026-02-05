package com.thunder.wildernessodysseyapi.lorebook;

import com.thunder.wildernessodysseyapi.lorebook.loot.LoreBookAvailableCondition;
import com.thunder.wildernessodysseyapi.lorebook.loot.LoreBookLootFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class LoreBookEvents {
    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();
        if (id == null || !id.getPath().startsWith("chests/")) {
            return;
        }
        float chance = LoreBookManager.config().chance();
        if (chance <= 0f) {
            return;
        }
        LootPool pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(Items.WRITTEN_BOOK)
                        .when(LootItemRandomChanceCondition.randomChance(chance))
                        .when((LootItemCondition.Builder) new LoreBookAvailableCondition())
                        .apply(LoreBookLootFunction.builder()))
                .build();
        event.getTable().addPool(pool);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            LoreBookManager.scanInventory(player);
        }
    }
}
