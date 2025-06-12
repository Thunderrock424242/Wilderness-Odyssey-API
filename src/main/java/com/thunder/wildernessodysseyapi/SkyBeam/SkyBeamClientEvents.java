package com.thunder.wildernessodysseyapi.SkyBeam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.PoseStack;

@EventBusSubscriber(Dist.CLIENT)
public class SkyBeamClientEvents {

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();

        long gameTime = Minecraft.getInstance().level.getGameTime();
        SkyBeamManager.tick(gameTime);

        for (SkyBeamData beam : SkyBeamManager.ACTIVE_BEAMS) {
            SkyBeamRenderer.renderBeam(poseStack, buffer, cam, beam, gameTime);
        }
    }
}
