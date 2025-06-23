package com.thunder.wildernessodysseyapi.Core;

import com.thunder.wildernessodysseyapi.Cloak.CloakRenderHandler;
import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // client-only registration
        CloakRenderHandler.init(); // Initialize framebuffer system
        WaveRenderer.initializeShader();
    }
}
