package com.thunder.wildernessodysseyapi.lorebook.loot;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

public class LoreBookAvailableCondition implements LootItemCondition {
    public static final MapCodec<LoreBookAvailableCondition> CODEC = MapCodec.unit(LoreBookAvailableCondition::new);

    public static LootItemCondition.Builder builder() {
        return LoreBookAvailableCondition::new;
    }

    @Override
    public LootItemConditionType getType() {
        return ModLootConditions.LORE_BOOK_AVAILABLE.get();
    }

    @Override
    public boolean test(LootContext lootContext) {
        if (!(lootContext.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) {
            return false;
        }
        return LoreBookManager.nextEntry(player).isPresent();
    }
}
