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
    private static final ResourceLocation WAVE_SHADER_LOCATION =
            ResourceLocation.tryParse("wildernessodysseyapi:shaders/core/wave_shader.json");
    private static final ResourceLocation FOAM_TEXTURE =
            ResourceLocation.tryParse("wildernessodysseyapi:textures/misc/foam.png");

    /**
     * Call once during client setup (or your main mod class) to load the shader JSON.
     */
    public static void initializeShader() {
        try {
            if (WAVE_SHADER_LOCATION == null) {
                throw new IllegalStateException("Invalid resource location for wave shader");
            }
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
     * Renders a “foam” quad offset by a small, time‐driven vertical shift.
     * The actual wave vertex displacement is done in the GPU shader.
     *
     * @param poseStack   current PoseStack
     * @param partialTick interpolation tick
     * @param light       packed light (use 0xF000F0 for full bright if you want)
     */
    public static void renderFoamAndWaves(PoseStack poseStack, float partialTick, int light) {
        if (waveShader == null) {
            // Shader failed to load or hasn’t been initialized
            return;
        }
        if (FOAM_TEXTURE == null) {
            // Invalid foam texture
            return;
        }

        // Compute a small vertical offset purely for the CPU‐side quad (cosmetic foam bounce).
        float time = (System.currentTimeMillis() / 1000.0F) + partialTick;
        float waveOffset = (float) (Math.sin(time * 2.0F) * 0.2F);

        // Push a small vertical shift so that the foam quad bobs up/down in sync with the shader’s wave.
        poseStack.pushPose();
        poseStack.translate(0.0F, waveOffset, 0.0F);

        // Grab the current 4×4 matrix from the PoseStack
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        // Bind our custom wave shader
        RenderSystem.setShader(() -> waveShader);

        // Tell GL which texture to use for “foam.” (slot 0)
        RenderSystem.setShaderTexture(0, FOAM_TEXTURE);

        // Prepare a buffer to draw a single fullscreen‐quad (2×2) in world‐space
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());

        // Compute foam alpha based on how high the offset is (optional tweak)
        float foamAlpha = Math.max(0.0F, waveOffset * 5.0F);

        // Four corners of a quad at y=0 in world coordinates, spanning [-1..1] in X and Z.
        // The GPU shader will displace these vertices along Y in the vertex shader.
        consumer.addVertex(pose, -1.0F, 0.0F, -1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(0.0F, 0.0F)
                .uv2(light)
                .endVertex();
        consumer.addVertex(pose, 1.0F, 0.0F, -1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(1.0F, 0.0F)
                .uv2(light)
                .endVertex();
        consumer.addVertex(pose, 1.0F, 0.0F, 1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(1.0F, 1.0F)
                .uv2(light)
                .endVertex();
        consumer.addVertex(pose, -1.0F, 0.0F, 1.0F)
                .color(255, 255, 255, (int) (255 * foamAlpha))
                .uv(0.0F, 1.0F)
                .uv2(light)
                .endVertex();

        // Push the quad through the buffer
        bufferSource.endBatch();

        poseStack.popPose();

        // Reset to Minecraft’s default shader afterwards
        RenderSystem.setShader(RenderSystem::getShader);
    }
}
