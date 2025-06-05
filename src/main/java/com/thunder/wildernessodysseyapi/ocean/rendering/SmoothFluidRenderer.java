package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.ocean.fluid.FluidHeightTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

public class SmoothFluidRenderer {
    public static void render(BlockAndTintGetter level, BlockPos pos, PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
        float height = FluidHeightTracker.getInterpolated(pos, partialTick);

        VertexConsumer consumer = buffer.getBuffer(SmoothFluidRenderType.SMOOTH_WATER);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), height + pos.getY(), pos.getZ());

        // Simple water surface quad
        consumer.vertex(poseStack.last().pose(), 0, 0, 0).color(0, 100, 255, 150).uv(0, 0).endVertex();
        consumer.vertex(poseStack.last().pose(), 1, 0, 0).color(0, 100, 255, 150).uv(1, 0).endVertex();
        consumer.vertex(poseStack.last().pose(), 1, 0, 1).color(0, 100, 255, 150).uv(1, 1).endVertex();
        consumer.vertex(poseStack.last().pose(), 0, 0, 1).color(0, 100, 255, 150).uv(0, 1).endVertex();

        poseStack.popPose();
    }
}