package com.thunder.wildernessodysseyapi.RenderEngine;

import com.thunder.wildernessodysseyapi.RenderEngine.Threading.RenderThreadManager;
import com.thunder.wildernessodysseyapi.RenderEngine.model.ModdedModelLoader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(RegisterRenderersEvent event) {
        ModdedModelLoader.initialize(); // Ensure modded models load
    }

    @SubscribeEvent
    public static void onShutdown(net.minecraftforge.fml.event.lifecycle.FMLClientStoppingEvent event) {
        RenderThreadManager.shutdown(); // Properly shut down render thread
    }
}