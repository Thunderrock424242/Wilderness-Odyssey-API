package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.ocean.fluid.FluidHeightTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(value = Dist.CLIENT)
public class SmoothFluidRenderer {

    /**
     * Standard version for use with PoseStack + BufferSource (e.g. from BlockEntityRenderer).
     */
    public static void render(BlockAndTintGetter level,
                              BlockPos pos,
                              PoseStack poseStack,
                              MultiBufferSource buffer,
                              float partialTick) {
        // 1. Interpolated fluid height:
        float height = FluidHeightTracker.getInterpolated(pos, partialTick);

        // 2. Get a VertexConsumer for our custom RenderType:
        VertexConsumer consumer = buffer.getBuffer(SmoothFluidRenderType.SMOOTH_WATER);

        // 3. Retrieve packed light (as a single int) and split into (u, v):
        int packedLight = Minecraft.getInstance().level.getLightEngine().getRawBrightness(pos, 0);
        int lightU = packedLight & 0xFFFF;
        int lightV = (packedLight >>> 16) & 0xFFFF;

        // 4. Compute color (e.g., translucent bluish foam):
        float foamAlpha = 0.8f;
        int color = FastColor.ARGB32.color((int)(255 * foamAlpha), 0, 100, 255);

        // 5. Y‐offset for the quad:
        float y = height;

        // 6. Push PoseStack transform to world‐space at block (pos.getX(), pos.getY(), pos.getZ()):
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

        // 7. Add the four vertices of a flat quad at height “y”:
        consumer.addVertex(poseStack.last().pose(), 0.0F, y, 0.0F)
                .setColor(color)
                .setUv(0.0F, 0.0F)
                .setUv2(lightU, lightV);   // <— now passing two ints

        consumer.addVertex(poseStack.last().pose(), 1.0F, y, 0.0F)
                .setColor(color)
                .setUv(1.0F, 0.0F)
                .setUv2(lightU, lightV);

        consumer.addVertex(poseStack.last().pose(), 1.0F, y, 1.0F)
                .setColor(color)
                .setUv(1.0F, 1.0F)
                .setUv2(lightU, lightV);

        consumer.addVertex(poseStack.last().pose(), 0.0F, y, 1.0F)
                .setColor(color)
                .setUv(0.0F, 1.0F)
                .setUv2(lightU, lightV);

        // 8. Pop PoseStack:
        poseStack.popPose();
    }

    /**
     * Direct version for use in BlockRenderDispatcher#renderLiquid(...)
     * (no PoseStack or MultiBufferSource available there).
     */
    public static void renderDirect(VertexConsumer consumer,
                                    BlockAndTintGetter level,
                                    BlockPos pos,
                                    FluidState fluidState) {
        // Only draw for pure water:
        if (fluidState.getType() != Fluids.WATER) return;

        // 1. Current fluid height (no interpolation, since renderLiquid is per‐tick):
        float height = fluidState.getOwnHeight();

        // 2. Split packed light into (u, v):
        int packedLight = Minecraft.getInstance().level.getLightEngine().getRawBrightness(pos, 0);
        int lightU = packedLight & 0xFFFF;
        int lightV = (packedLight >>> 16) & 0xFFFF;

        // 3. Compute color (e.g. translucent blue):
        float foamAlpha = 0.8f;
        int color = FastColor.ARGB32.color((int)(255 * foamAlpha), 0, 100, 255);

        // 4. Compute world‐space Y coordinate:
        float y = pos.getY() + height;

        // 5. Four corners of quad at (pos.x ± 0/1, y, pos.z ± 0/1):
        consumer.addVertex((PoseStack.Pose) null, pos.getX() + 0.0F, y, pos.getZ() + 0.0F)
                .setColor(color)
                .setUv(0.0F, 0.0F)
                .setUv2(lightU, lightV);

        consumer.addVertex((PoseStack.Pose) null, pos.getX() + 1.0F, y, pos.getZ() + 0.0F)
                .setColor(color)
                .setUv(1.0F, 0.0F)
                .setUv2(lightU, lightV);

        consumer.addVertex((PoseStack.Pose) null, pos.getX() + 1.0F, y, pos.getZ() + 1.0F)
                .setColor(color)
                .setUv(1.0F, 1.0F)
                .setUv2(lightU, lightV);

        consumer.addVertex((PoseStack.Pose) null, pos.getX() + 0.0F, y, pos.getZ() + 1.0F)
                .setColor(color)
                .setUv(0.0F, 1.0F)
                .setUv2(lightU, lightV);
    }
}
