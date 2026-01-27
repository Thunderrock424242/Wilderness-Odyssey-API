package com.thunder.wildernessodysseyapi.item;

import com.thunder.wildernessodysseyapi.item.cloak.CloakChipItem;
import com.thunder.wildernessodysseyapi.item.cloak.CloakItem;
import com.thunder.wildernessodysseyapi.item.neural.NeuralFrameItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
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

    public static final DeferredItem<Item> CLOAK_ITEM = ITEMS.register(
            "cloak_item",
            () -> new CloakItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> CLOAK_CHIP = ITEMS.register(
            "cloak_chip",
            () -> new CloakChipItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> NEURAL_FRAME = ITEMS.register(
            "neural_frame",
            () -> new NeuralFrameItem(new Item.Properties().stacksTo(1))
    );

    /**
     * Register.
     *
     * @param eventBus the event bus
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
