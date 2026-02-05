package com.thunder.wildernessodysseyapi.lorebook.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LoreBookLootFunction extends LootItemConditionalFunction {
    public static final MapCodec<LoreBookLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            commonFields(instance).apply(instance, LoreBookLootFunction::new)
    );

    protected LoreBookLootFunction(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType getType() {
        return ModLootFunctions.LORE_BOOK.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext lootContext) {
        if (!(lootContext.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) {
            return ItemStack.EMPTY;
        }
        return LoreBookManager.nextEntry(player)
                .map(LoreBookManager::createBookStack)
                .orElse(ItemStack.EMPTY);
    }

    public static Builder<?> builder() {
        return simpleBuilder(LoreBookLootFunction::new);
    }
}
