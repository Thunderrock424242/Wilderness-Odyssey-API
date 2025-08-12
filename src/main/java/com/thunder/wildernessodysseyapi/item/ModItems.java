package com.thunder.wildernessodysseyapi.item;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

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
     * Register.
     *
     * @param eventBus the event bus
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
