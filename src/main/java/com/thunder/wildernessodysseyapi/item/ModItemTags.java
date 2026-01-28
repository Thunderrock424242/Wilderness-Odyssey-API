package com.thunder.wildernessodysseyapi.item;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
    public static final TagKey<Item> CHIP_SET = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "chip_set")
    );

    private ModItemTags() {
    }
}
