package com.thunder.wildernessodysseyapi.ocean.client;

import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(value = Dist.CLIENT)
public class ModClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Compiles and loads wave_shader.vsh/.fsh via wave_shader.json
        WaveRenderer.initializeShader();
    }
}
