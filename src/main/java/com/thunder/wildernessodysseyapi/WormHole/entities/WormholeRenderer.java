package com.thunder.wildernessodysseyapi.WormHole.entities;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class WormholeRenderer extends EntityRenderer<EntityWormhole> {
    private static final ResourceLocation VORTEX_TEXTURE = new ResourceLocation("wormholemod", "textures/entity/vortex_texture.png");

    public WormholeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityWormhole entity) {
        return VORTEX_TEXTURE;
    }

    @Override
    public void render(EntityWormhole entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Push transformation matrix
        poseStack.pushPose();

        // Pulsating effect
        float age = entity.tickCount + partialTicks;
        float scale = 2.0f + Mth.sin(age * 0.1f) * 0.2f; // Dynamic scaling
        poseStack.scale(scale, scale, scale);

        // Rotating effect
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * 2 % 360)); // Rotation on Y-axis

        // Enable blending for smooth transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Render the vortex texture
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(VORTEX_TEXTURE));
        renderQuad(poseStack, vertexConsumer, packedLight);

        // Disable blending
        RenderSystem.disableBlend();

        // Pop transformation matrix
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderQuad(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight) {
        PoseStack.Pose matrixEntry = poseStack.last();
        float size = 1.0f;

        // Define vertices for a simple textured quad
        vertexConsumer.vertex(matrixEntry.pose(), -size, -size, 0).color(255, 255, 255, 128).uv(0, 0).uv2(packedLight).normal(0, 0, 1).endVertex();
        vertexConsumer.vertex(matrixEntry.pose(), size, -size, 0).color(255, 255, 255, 128).uv(1, 0).uv2(packedLight).normal(0, 0, 1).endVertex();
        vertexConsumer.vertex(matrixEntry.pose(), size, size, 0).color(255, 255, 255, 128).uv(1, 1).uv2(packedLight).normal(0, 0, 1).endVertex();
        vertexConsumer.vertex(matrixEntry.pose(), -size, size, 0).color(255, 255, 255, 128).uv(0, 1).uv2(packedLight).normal(0, 0, 1).endVertex();
    }
}
