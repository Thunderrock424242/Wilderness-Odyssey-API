package com.thunder.wildernessodysseyapi.lorebook.loot;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModLootFunctions {
    public static final DeferredRegister<LootItemFunctionType> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, ModConstants.MOD_ID);

    public static final DeferredHolder<LootItemFunctionType, LootItemFunctionType> LORE_BOOK =
            LOOT_FUNCTIONS.register("lore_book", () -> new LootItemFunctionType(LoreBookLootFunction.CODEC));

    private ModLootFunctions() {
    }
}
