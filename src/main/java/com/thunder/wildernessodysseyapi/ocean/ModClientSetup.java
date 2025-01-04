package com.thunder.wildernessodysseyapi.ocean;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber
public class ModClientSetup {

    /**
     * Handles client setup logic, such as initializing shaders.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        WaveRenderer.initializeShader(); // Update this to match your WaveRenderer method
    }

    /**
     * Handles the rendering of foam and wave effects for vanilla water blocks.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return; // Only render after the sky stage to ensure proper layering
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            PoseStack poseStack = event.getPoseStack();

            // Use frame time as partial ticks
            float partialTicks = Minecraft.getInstance().getFrameTime();

            // Use the WaveRenderer to render foam and waves
            WaveRenderer.renderFoamAndWaves(poseStack, partialTicks, 0xF000F0);
        }
    }

}
