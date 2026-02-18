package com.thunder.wildernessodysseyapi.lorebook.loot;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModLootConditions {
    public static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, ModConstants.MOD_ID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> LORE_BOOK_AVAILABLE =
            LOOT_CONDITIONS.register("lore_book_available", () -> new LootItemConditionType(LoreBookAvailableCondition.CODEC));

    private ModLootConditions() {
    }
}
