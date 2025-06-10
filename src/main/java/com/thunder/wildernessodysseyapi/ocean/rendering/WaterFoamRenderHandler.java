package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Renders foam/waves on top of the terrain in the AFTER_SKY stage.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class WaterFoamRenderHandler {
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        // bind our shader with the updated time uniform
        WaterSystem.bindWaveShader();

        PoseStack pose = event.getPoseStack();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        // sample light at the camera position
        var cam = mc.cameraEntity;
        BlockPos lightPos = cam != null
                ? cam.blockPosition()
                : new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        int packedLight = level.getLightEngine().getRawBrightness(lightPos, 0);

        // now draw the foam & waves
        WaveRenderer.renderFoamAndWaves(pose, partialTicks, packedLight, level);
    }
}