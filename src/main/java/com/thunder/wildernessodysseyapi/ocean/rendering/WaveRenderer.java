package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class WaveRenderer {
    private static final ResourceLocation FOAM_TEXTURE = ResourceLocation.tryParse("mymod:textures/misc/foam.png");
    private static final float TIDE_CYCLE_DURATION = 120.0F; // 120 seconds for a full tide cycle

    public static void renderTidesAndFoam(PoseStack poseStack, float partialTicks, int light) {
        if (FOAM_TEXTURE == null) {
            return; // Skip if foam texture is invalid
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        float time = (System.currentTimeMillis() / 1000.0F) + partialTicks;
        float waveOffset = (float) Math.sin(time * 2.0F) * 0.2F; // Waves

        // Only render foam for blocks near the player
        PoseStack nearbyPoseStack = new PoseStack();
        nearbyPoseStack.translate(0.0F, waveOffset, 0.0F);
        renderFoamLayer(bufferBuilder, nearbyPoseStack, light, waveOffset);
    }

    private static void renderFoamLayer(BufferBuilder buffer, PoseStack poseStack, int light, float waveOffset) {
        assert FOAM_TEXTURE != null;
        RenderSystem.setShaderTexture(0, FOAM_TEXTURE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float foamAlpha = Math.max(0.0F, waveOffset * 5.0F);

        buffer.vertex(poseStack.last().pose(), -1.0F, 0.0F, -1.0F).uv(0.0F, 0.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();
        buffer.vertex(poseStack.last().pose(), 1.0F, 0.0F, -1.0F).uv(1.0F, 0.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();
        buffer.vertex(poseStack.last().pose(), 1.0F, 0.0F, 1.0F).uv(1.0F, 1.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();
        buffer.vertex(poseStack.last().pose(), -1.0F, 0.0F, 1.0F).uv(0.0F, 1.0F).color(255, 255, 255, (int) (255 * foamAlpha)).uv2(light).endVertex();

        Tesselator.getInstance().end();
    }
}
