package com.thunder.wildernessodysseyapi.Cloak;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class ClientEventHandler {
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && event.phase == TickEvent.Phase.END) {
            CloakRenderHandler.captureBehindView(mc, mc.player);
        }
    }
}
