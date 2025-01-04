package com.thunder.wildernessodysseyapi.ocean;

import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber
public class ModClientSetup {

    /**
     * Handles client setup logic, such as initializing shaders.
     */
    public static void onClientSetup(FMLClientSetupEvent event) {
        WaveRenderer.initShader(); // Initialize wave-related shaders if needed
    }

    /**
     * Handles the rendering of foam and wave effects for vanilla water blocks.
     */
    @SubscribeEvent
    public static void onRenderWorld(RenderLevelLastEvent event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null) {
            // Use the WaveRenderer to render foam and waves
            WaveRenderer.renderFoamAndWaves(event.getPoseStack(), event.getPartialTick(), 0xF000F0);
        }
    }
}
