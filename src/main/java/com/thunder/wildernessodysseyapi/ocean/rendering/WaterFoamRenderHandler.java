package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class WaterFoamRenderHandler {
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        // 1) Bind shader & update uniforms
        WaveRenderer.bindShader(
                WaterSystem.getWaveTime(),
                WaterSystem.getTideOffset()
        );

        // 2) Gather inputs
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick().getGameTimeDeltaTicks();
        BlockPos camPos;
        if (mc.cameraEntity != null) {
            camPos = mc.cameraEntity.blockPosition();
        } else {
            assert mc.player != null;
            camPos = new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        }
        int packedLight = level.getLightEngine().getRawBrightness(camPos, 0);

        // 3) Render foam + dynamic water
        WaveRenderer.renderFoamAndWaves(pose, partial, packedLight);

        // 4) End batch so other translucent draws come after
        mc.renderBuffers().bufferSource().endBatch();
    }
}
