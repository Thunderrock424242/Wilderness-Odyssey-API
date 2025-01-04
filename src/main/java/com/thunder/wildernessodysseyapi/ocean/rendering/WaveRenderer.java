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
import org.joml.Matrix4f;

import java.io.IOException;

public class WaveRenderer {

    private static ShaderInstance waveShader;
    private static final ResourceLocation WAVE_SHADER_LOCATION = ResourceLocation.tryParse("mymod:shaders/wave_shader");
    private static final ResourceLocation FOAM_TEXTURE = ResourceLocation.tryParse("mymod:textures/misc/foam.png");

    /**
     * Initializes the custom wave shader.
     */
    public static void initializeShader() {
        try {
            assert WAVE_SHADER_LOCATION != null;
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

    /**
     * Renders foam and wave effects using the custom shader.
     */
    public static void renderFoamAndWaves(PoseStack poseStack, float partialTicks, int light) {
        if (waveShader == null) {
            System.err.println("Wave shader not initialized. Skipping rendering.");
            return;
        }

        if (FOAM_TEXTURE == null) {
            System.err.println("Invalid foam texture resource location!");
            return;
        }

        // Get BufferSource for VertexConsumer
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.translucent());

        float time = (System.currentTimeMillis() / 1000.0F) + partialTicks;
        float waveOffset = (float) Math.sin(time * 2.0F) * 0.2F;

        // Transform using PoseStack
        poseStack.pushPose();
        poseStack.translate(0.0F, waveOffset, 0.0F);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        // Set the shader and texture
        RenderSystem.setShader(() -> waveShader);
        RenderSystem.setShaderTexture(0, FOAM_TEXTURE);

        float foamAlpha = Math.max(0.0F, waveOffset * 5.0F);

        // Define vertices with position, UV mapping, and color using addVertex
        vertexConsumer
                .addVertex(pose, -1.0F, 0.0F, -1.0F)
                .setColor(255, 255, 255, (int) (255 * foamAlpha))
                .setUv(0.0F, 0.0F)
                .setLight(light)
                .endVertex();

        vertexConsumer
                .addVertex(pose, 1.0F, 0.0F, -1.0F)
                .setColor(255, 255, 255, (int) (255 * foamAlpha))
                .setUv(1.0F, 0.0F)
                .setLight(light)
                .endVertex();

        vertexConsumer
                .addVertex(pose, 1.0F, 0.0F, 1.0F)
                .setColor(255, 255, 255, (int) (255 * foamAlpha))
                .setUv(1.0F, 1.0F)
                .setLight(light)
                .endVertex();

        vertexConsumer
                .addVertex(pose, -1.0F, 0.0F, 1.0F)
                .setColor(255, 255, 255, (int) (255 * foamAlpha))
                .setUv(0.0F, 1.0F)
                .setLight(light)
                .endVertex();

        bufferSource.endBatch();

        // Restore PoseStack
        poseStack.popPose();

        // Reset the shader
        RenderSystem.setShader(RenderSystem::getShader);
    }
}
