package com.thunder.wildernessodysseyapi.lorebook.loot;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * Loot predicate that only passes when the looting player still has at least
 * one undiscovered lore book available.
 */
public class LoreBookAvailableCondition implements LootItemCondition {
    public static final MapCodec<LoreBookAvailableCondition> CODEC = MapCodec.unit(LoreBookAvailableCondition::new);

    /**
     * Convenience builder for loot table JSON and runtime registration.
     */
    public static LootItemCondition.Builder builder() {
        return LoreBookAvailableCondition::new;
    }

    @Override
    public LootItemConditionType getType() {
        return ModLootConditions.LORE_BOOK_AVAILABLE.get();
    }

    /**
     * Checks if this loot context has a server player with remaining undiscovered
     * lore books.
     */
    @Override
    public boolean test(LootContext lootContext) {
        if (!(lootContext.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) {
            return false;
        }
        return LoreBookManager.nextEntry(player).isPresent();
    }
}
