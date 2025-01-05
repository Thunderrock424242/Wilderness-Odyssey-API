package com.thunder.wildernessodysseyapi.ocean;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber
public class ModClientSetup {

    /**
     * Handles client setup logic, such as initializing shaders.
     */
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {


        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            WaveRenderer.initializeShader(); // Initialize wave-related shaders if needed
        }
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

            // Retrieve DeltaTracker from the event
            DeltaTracker deltaTracker = event.getPartialTick();

            // Extract the partial tick value
            float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);

            // Use the WaveRenderer to render foam and waves
            WaveRenderer.renderFoamAndWaves(poseStack, partialTicks, 0xF000F0);
        }
    }
}
