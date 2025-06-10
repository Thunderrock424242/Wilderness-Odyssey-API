package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;

import java.io.IOException;

public class WaveRenderer {

    private static ShaderInstance waveShader;
    private static final ResourceLocation WAVE_SHADER_LOCATION =
            ResourceLocation.tryParse("wildernessodysseyapi:shaders/core/wave_shader.json");
    private static final ResourceLocation FOAM_TEXTURE =
            ResourceLocation.tryParse("wildernessodysseyapi:textures/misc/foam.png");

    /**
     * Initializes the custom wave shader.
     */
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

    /**
     * Renders foam and wave effects using the custom shader.
     *
     * @param poseStack  the current PoseStack
     * @param partialTicks  interpolation value for the current tick
     * @param packedLight  a single integer containing both block‐light and sky‐light (packed as 0xVVVVUUUU)
     * @param level  the world (used for any location‐based calculations)
     */
    public static void renderFoamAndWaves(PoseStack poseStack, float partialTicks, int packedLight, Level level) {
        if (waveShader == null) {
            return;
        }

        // Example location-based wave height factor (you can substitute actual X/Z if desired)
        float waveHeightFactor = (float) WaterSystem.getWaveHeightAt(0.0, 0.0);

        // Compute a time-based offset
        float time = (System.currentTimeMillis() / 1000.0F) + partialTicks;
        float waveOffset = (float) Math.sin(time * 2.0F) * 0.2F * waveHeightFactor;

        // Push and translate the PoseStack by the vertical wave offset
        poseStack.pushPose();
        poseStack.translate(0.0F, waveOffset, 0.0F);

        // Grab the current transformation matrix
        Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        // Bind our custom wave shader and foam texture
        RenderSystem.setShader(() -> waveShader);
        RenderSystem.setShaderTexture(0, FOAM_TEXTURE);

        // Acquire a VertexConsumer for translucent rendering
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());

        // Compute foam alpha (0..1) and pack into an ARGB32 color (white with varying alpha)
        float foamAlpha = Math.max(0.0F, waveOffset * 5.0F);
        foamAlpha = Math.min(1.0F, Math.max(0.0F, foamAlpha));
        int packedColor = FastColor.ARGB32.color((int) (255 * foamAlpha), 255, 255, 255);

        // Unpack the “packedLight” (single int) into two 16-bit halves:
        //   lower 16 bits = lightU, upper 16 bits = lightV
        int lightU = packedLight & 0xFFFF;
        int lightV = (packedLight >>> 16) & 0xFFFF;

        // Define four vertices for a simple quad. Each vertex uses:
        //   - POSITION (x, y, z)
        //   - COLOR (packedColor)
        //   - UV   (texture coords)
        //   - UV2  (lightU, lightV)
        //
        // Vertex 1: (-1, 0, -1) with UV (0, 0)
        consumer.addVertex(matrix, -1.0F, 0.0F, -1.0F)
                .setColor(packedColor)
                .setUv(0.0F, 0.0F)
                .setUv2(lightU, lightV);

        // Vertex 2: ( 1, 0, -1) with UV (1, 0)
        consumer.addVertex(matrix, 1.0F, 0.0F, -1.0F)
                .setColor(packedColor)
                .setUv(1.0F, 0.0F)
                .setUv2(lightU, lightV);

        // Vertex 3: ( 1, 0,  1) with UV (1, 1)
        consumer.addVertex(matrix, 1.0F, 0.0F, 1.0F)
                .setColor(packedColor)
                .setUv(1.0F, 1.0F)
                .setUv2(lightU, lightV);

        // Vertex 4: (-1, 0,  1) with UV (0, 1)
        consumer.addVertex(matrix, -1.0F, 0.0F, 1.0F)
                .setColor(packedColor)
                .setUv(0.0F, 1.0F)
                .setUv2(lightU, lightV);

        // “Flush” the quad so that it actually draws this batch
        bufferSource.endBatch();

        // Pop the PoseStack so we return to the previous transform
        poseStack.popPose();

        // Reset to the default shader
        RenderSystem.setShader(RenderSystem::getShader);
    }
}
