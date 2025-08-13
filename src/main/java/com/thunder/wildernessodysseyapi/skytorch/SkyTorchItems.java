package com.thunder.wildernessodysseyapi.skytorch;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Registers Sky Torch related items.
 */
public class SkyTorchItems {
    /**
     * Item registry for Sky Torch items.
     */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<SkyTorchStaffItem> SKY_TORCH_STAFF =
            ITEMS.register("sky_torch_staff", () -> new SkyTorchStaffItem(new Item.Properties().stacksTo(1)));

    /**
     * Register Sky Torch items with the event bus.
     *
     * @param eventBus the mod event bus
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
