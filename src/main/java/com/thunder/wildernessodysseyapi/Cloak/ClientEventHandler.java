package com.thunder.wildernessodysseyapi.Cloak;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber
public class ClientEventHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) { // Use Post to run after game logic updates
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CloakRenderHandler.captureBehindView(mc, mc.player);
        }
    }
}