package com.thunder.wildernessodysseyapi.ocean.client;

import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.common.EventBusSubscriber;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Compiles and loads wave_shader.vsh/.fsh via wave_shader.json
        WaveRenderer.initializeShader();
    }
}
