package com.thunder.wildernessodysseyapi.item;

import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The type Mod items.
 */
public class ModItems {
    /**
     * The constant ITEMS.
     */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WildernessOdysseyAPIMainModClass.MOD_ID);

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
