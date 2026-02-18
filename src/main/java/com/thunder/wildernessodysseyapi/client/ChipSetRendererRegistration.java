package com.thunder.wildernessodysseyapi.client;

import com.thunder.wildernessodysseyapi.item.ModItems;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

public final class ChipSetRendererRegistration {
    private ChipSetRendererRegistration() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> CuriosRendererRegistry.register(ModItems.CLOAK_CHIP.get(), ChipSetCurioRenderer::new));
    }
}
