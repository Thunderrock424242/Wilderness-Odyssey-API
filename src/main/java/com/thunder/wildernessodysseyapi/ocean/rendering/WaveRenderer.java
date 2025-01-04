package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class WaveRenderer {
    private static final ResourceLocation FOAM_TEXTURE = ResourceLocation.tryParse("mymod:textures/misc/foam.png");

    public static void renderFoamAndWaves(PoseStack poseStack, float partialTicks, int light) {
        if (FOAM_TEXTURE == null) {
            System.err.println("Invalid foam texture resource location!");
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        float time = (System.currentTimeMillis() / 1000.0F) + partialTicks;
        float waveOffset = (float) Math.sin(time * 2.0F) * 0.2F; // Waves

        poseStack.pushPose();
        poseStack.translate(0.0F, waveOffset, 0.0F); // Apply wave offset
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShaderTexture(0, FOAM_TEXTURE);
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float foamAlpha = Math.max(0.0F, waveOffset * 5.0F); // Foam intensity based on wave height

        bufferBuilder.vertex(matrix, -1.0F, 0.0F, -1.0F).uv(0.0F, 0.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();
        bufferBuilder.vertex(matrix, 1.0F, 0.0F, -1.0F).uv(1.0F, 0.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();
        bufferBuilder.vertex(matrix, 1.0F, 0.0F, 1.0F).uv(1.0F, 1.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();
        bufferBuilder.vertex(matrix, -1.0F, 0.0F, 1.0F).uv(0.0F, 1.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();

        tesselator.end();
        poseStack.popPose();
    }
}
