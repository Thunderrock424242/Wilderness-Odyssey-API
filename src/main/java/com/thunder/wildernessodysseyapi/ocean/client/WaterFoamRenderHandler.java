package com.thunder.wildernessodysseyapi.ocean.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
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

        PoseStack poseStack = event.getPoseStack();
        DeltaTracker dt = event.getPartialTick();
        float partialTicks = dt.getGameTimeDeltaPartialTick(true);

        // Sample lighting at the camera (or player) position:
        var cam = mc.cameraEntity;
        BlockPos lightPos = (cam != null)
                ? cam.blockPosition()
                : new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        int packedLight = level.getLightEngine().getRawBrightness(lightPos, 0);

        WaveRenderer.renderFoamAndWaves(poseStack, partialTicks, packedLight, level);
    }
}
