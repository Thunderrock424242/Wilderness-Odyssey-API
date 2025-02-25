package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.io.IOException;

public class WaveRenderer {

    private static ShaderInstance waveShader;
    private static final ResourceLocation WAVE_SHADER_LOCATION = new ResourceLocation("wildernessodysseyapi:shaders/core/wave_shader");
    private static final ResourceLocation FOAM_TEXTURE = new ResourceLocation("wildernessodysseyapi:textures/misc/foam.png");

    public static void initializeShader() {
        try {
            waveShader = new ShaderInstance(
                    Minecraft.getInstance().getResourceManager(),
                    WAVE_SHADER_LOCATION,
                    DefaultVertexFormat.POSITION_TEX_COLOR
            );
        } catch (IOException e) {
            System.err.println("Failed to initialize wave shader: " + e.getMessage());
            waveShader = null;
        }
    }

    public static void renderFoamAndWaves(PoseStack poseStack, float partialTicks, int light, Level level) {
        if (waveShader == null) {
            return;
        }

        float waveHeight = WaterSystem.getWaveHeight(level);
        float time = (System.currentTimeMillis() / 1000.0F) + partialTicks;
        float waveOffset = (float) Math.sin(time * 2.0F) * 0.2F * waveHeight;

        poseStack.pushPose();
        poseStack.translate(0.0F, waveOffset, 0.0F);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        RenderSystem.setShader(() -> waveShader);
        RenderSystem.setShaderTexture(0, FOAM_TEXTURE);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.translucent());

        float foamAlpha = Math.max(0.0F, waveOffset * 5.0F);

        vertexConsumer.vertex(matrix, -1.0F, 0.0F, -1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(matrix, 1.0F, 0.0F, -1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(matrix, 1.0F, 0.0F, 1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(1.0F, 1.0F).endVertex();
        vertexConsumer.vertex(matrix, -1.0F, 0.0F, 1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(0.0F, 1.0F).endVertex();

        bufferSource.endBatch();
        poseStack.popPose();
        RenderSystem.setShader(RenderSystem::getShader);
    }
}
