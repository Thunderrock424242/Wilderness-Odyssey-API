package com.thunder.wildernessodysseyapi.WorldGen.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = "wildernessodysseyapi", value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // You can put client init code here if needed.
    }

    public static void registerClientEvents() {
        NeoForge.EVENT_BUS.register(UnderwaterBioluminescence.class); // ✅ correct for 1.21.1+
    }
}