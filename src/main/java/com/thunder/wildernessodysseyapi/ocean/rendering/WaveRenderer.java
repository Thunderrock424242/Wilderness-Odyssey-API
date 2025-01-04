package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.IOException;

public class WaveRenderer {

    private static ShaderInstance waveShader;
    private static final ResourceLocation WAVE_SHADER_LOCATION = ResourceLocation.tryParse("mymod:shaders/wave_shader");
    private static final ResourceLocation FOAM_TEXTURE = ResourceLocation.tryParse("mymod:textures/misc/foam.png");

    public static void initializeShader() {
        try {
            waveShader = new ShaderInstance(
                    Minecraft.getInstance().getResourceManager(),
                    WAVE_SHADER_LOCATION,
                    DefaultVertexFormat.POSITION_TEX
            );
        } catch (IOException e) {
            System.err.println("Failed to initialize wave shader: " + e.getMessage());
            waveShader = null;
        }
    }

    public static void renderFoamAndWaves(PoseStack poseStack, float partialTicks, int light) {
        if (waveShader == null) {
            System.err.println("Wave shader not initialized. Skipping rendering.");
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        float time = (System.currentTimeMillis() / 1000.0F) + partialTicks;
        float waveOffset = (float) Math.sin(time * 2.0F) * 0.2F;

        poseStack.pushPose();
        poseStack.translate(0.0F, waveOffset, 0.0F);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(() -> waveShader);
        RenderSystem.setShaderTexture(0, FOAM_TEXTURE);

        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float foamAlpha = Math.max(0.0F, waveOffset * 5.0F);

        bufferBuilder.vertex(matrix, -1.0F, 0.0F, -1.0F).uv(0.0F, 0.0F).endVertex();
        bufferBuilder.vertex(matrix, 1.0F, 0.0F, -1.0F).uv(1.0F, 0.0F).endVertex();
        bufferBuilder.vertex(matrix, 1.0F, 0.0F, 1.0F).uv(1.0F, 1.0F).endVertex();
        bufferBuilder.vertex(matrix, -1.0F, 0.0F, 1.0F).uv(0.0F, 1.0F).endVertex();

        tesselator.end();

        poseStack.popPose();

        RenderSystem.setShader(RenderSystem::getShader);
    }
}
