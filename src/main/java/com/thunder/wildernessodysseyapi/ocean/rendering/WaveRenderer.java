package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.IOException;

public class WaveRenderer {
    private static ShaderInstance waveShader;
    private static final ResourceLocation JSON = ResourceLocation.tryBuild("wildernessodysseyapi", "wave_shader");

    public static void initializeShader() {
        try {
            assert JSON != null;
            waveShader = new ShaderInstance(
                    Minecraft.getInstance().getResourceManager(),
                    JSON,
                    DefaultVertexFormat.POSITION_TEX_COLOR
            );
        } catch (IOException e) {
            waveShader = null;
            System.err.println("Failed to init wave shader: " + e.getMessage());
        }
    }

    /** Binds the shader and sets both waveTime & tideOffset uniforms. */
    public static void bindShader(float waveTime, float tideOffset) {
        if (waveShader == null) return;
        RenderSystem.setShader(() -> waveShader);
        waveShader.safeGetUniform("waveTime" ).set(waveTime);
        waveShader.safeGetUniform("tideOffset").set(tideOffset);
    }

    /**
     * Draws foam + waves as a full‐screen translucent quad.
     * You must have already called bindShader(...)!
     */
    public static void renderFoamAndWaves(PoseStack pose, float partialTicks, int packedLight) {
        if (waveShader == null) return;

        // grab the world‐space->clip‐space matrix
        Matrix4f mat = pose.last().pose();
        VertexConsumer consumer = Minecraft.getInstance()
                .renderBuffers()
                .bufferSource()
                .getBuffer(RenderType.translucent());

        int lightU = packedLight & 0xFFFF;
        int lightV = (packedLight >>> 16) & 0xFFFF;

        // bottom‐right, top‐right, top‐left, bottom‐left (or in CCW order)
        consumer
                .addVertex(pose.last(),  1.0F,  1.0F, 0.0F)
                .setColor(255,255,255,255)
                .setUv(1.0F, 0.0F)
                .setUv2(lightU, lightV);

        consumer
                .addVertex(pose.last(), -1.0F,  1.0F, 0.0F)
                .setColor(255,255,255,255)
                .setUv(0.0F, 0.0F)
                .setUv2(lightU, lightV);

        consumer
                .addVertex(pose.last(), -1.0F, -1.0F, 0.0F)
                .setColor(255,255,255,255)
                .setUv(0.0F, 1.0F)
                .setUv2(lightU, lightV);

        consumer
                .addVertex(pose.last(),  1.0F, -1.0F, 0.0F)
                .setColor(255,255,255,255)
                .setUv(1.0F, 1.0F)
                .setUv2(lightU, lightV);
    }
}