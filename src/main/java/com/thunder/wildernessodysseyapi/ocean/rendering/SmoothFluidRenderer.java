package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.ocean.fluid.FluidHeightTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockAndTintGetter;

public class SmoothFluidRenderer {
    public static void render(BlockAndTintGetter level, BlockPos pos, PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
        float height = FluidHeightTracker.getInterpolated(pos, partialTick);
        VertexConsumer consumer = buffer.getBuffer(SmoothFluidRenderType.SMOOTH_WATER);

        int light = Minecraft.getInstance().level.getLightEngine().getRawBrightness(pos, 0);
        float foamAlpha = 0.8f; // or vary this if you have foam dynamics

        int color = FastColor.ARGB32.color((int)(255 * foamAlpha), 0, 100, 255);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

        // render a flat quad on top of the block with interpolated height
        float y = height;

        consumer.addVertex(poseStack.last().pose(), 0.0F, y, 0.0F)
                .setColor(color)
                .setUv(0.0F, 0.0F)
                .setUv2(light)
                .endVertex();

        consumer.addVertex(poseStack.last().pose(), 1.0F, y, 0.0F)
                .setColor(color)
                .setUv(1.0F, 0.0F)
                .setUv2(light)
                .endVertex();

        consumer.addVertex(poseStack.last().pose(), 1.0F, y, 1.0F)
                .setColor(color)
                .setUv(1.0F, 1.0F)
                .setUv2(light)
                .endVertex();

        consumer.addVertex(poseStack.last().pose(), 0.0F, y, 1.0F)
                .setColor(color)
                .setUv(0.0F, 1.0F)
                .setUv2(light)
                .endVertex();

        poseStack.popPose();
    }
}