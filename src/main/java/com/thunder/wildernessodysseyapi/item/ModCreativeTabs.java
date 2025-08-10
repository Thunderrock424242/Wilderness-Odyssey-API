package com.thunder.wildernessodysseyapi.item;

import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Registers creative mode tabs for the mod.
 */
public class ModCreativeTabs {
    /**
     * Registry for creative mode tabs.
     */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    /**
     * Main mod creative tab.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WILDERNESS_ODYSSEY_TAB = TABS.register(
            "wildernessodysseyapi",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wildernessodysseyapi"))
                    .icon(() -> new ItemStack(Items.AMETHYST_SHARD))
                    .displayItems((parameters, output) -> {
                        output.accept(CryoTubeBlock.CRYO_TUBE.get());
                        output.accept(ModItems.SKY_TORCH_STAFF.get());
                    })
                    .build()
    );

    /**
     * Register the creative tabs with the given event bus.
     */
    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}

