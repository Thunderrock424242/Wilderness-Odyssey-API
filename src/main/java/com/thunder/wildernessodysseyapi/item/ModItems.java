package com.thunder.wildernessodysseyapi.item;

import com.thunder.wildernessodysseyapi.cloak.CloakItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.world.item.Item;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * The type Mod items.
 */
public class ModItems {
    /**
     * The constant ITEMS.
     */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    /**
     * Handheld cloak item used to toggle cloaking on allowed entities.
     */
    public static final DeferredItem<CloakItem> CLOAK_ITEM =
            ITEMS.register("cloak_item", () -> new CloakItem(new Item.Properties().stacksTo(1)));


    // No standalone items needed, as the unbreakable block's BlockItem is automatically registered.
    /**
     * Register.
     *
     * @param eventBus the event bus
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
