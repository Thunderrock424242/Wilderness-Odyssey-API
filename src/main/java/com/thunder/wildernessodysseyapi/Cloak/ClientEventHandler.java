package com.thunder.wildernessodysseyapi.Cloak;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber
public class ClientEventHandler {
    @SubscribeEvent
    public static void onRenderTick(RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && event.phase == RenderTickEvent.Phase.END) {
            CloakRenderHandler.captureBehindView(mc, mc.player);
        }
    }
}