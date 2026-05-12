package com.thunder.wildernessodysseyapi.temporalrift.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;

public class RiftCoreBlockEntityRenderer implements BlockEntityRenderer<RiftCoreBlockEntity> {
    private static final int VIOLET = color(184, 70, 255, 178);
    private static final int CYAN = color(48, 226, 255, 138);
    private static final int WHITE_CORE = color(238, 247, 255, 118);

    public RiftCoreBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RiftCoreBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        float age = (level == null ? 0.0F : level.getGameTime()) + partialTick;
        VertexConsumer buffer = bufferSource.getBuffer(TemporalRiftRenderTypes.rift());

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.02D, 0.5D);
        float renderScale = blockEntity.getRenderScale();
        poseStack.scale(renderScale, renderScale, renderScale);
        switch (blockEntity.getVisualMode()) {
            case BEFORE_SKY_TEAR -> {
                poseStack.translate(0.0D, 0.5D, 0.0D);
                poseStack.scale(4.2F, 4.8F, 1.0F);
                drawSkyTear(buffer, poseStack, age);
            }
            case BEFORE_GROUND_RETURN -> {
                poseStack.scale(2.2F, 1.0F, 2.2F);
                drawHorizontalTears(buffer, poseStack, age);
            }
            case TRANSIENT_RETURN -> {
                poseStack.scale(1.8F, 1.0F, 1.8F);
                drawHorizontalTears(buffer, poseStack, age);
            }
            case OVERWORLD_SINKHOLE -> {
                poseStack.scale(2.8F, 1.0F, 2.8F);
                drawHorizontalTears(buffer, poseStack, age);
            }
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RiftCoreBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private static void drawHorizontalTears(VertexConsumer buffer, PoseStack poseStack, float age) {
        for (int i = 0; i < 6; i++) {
            float rotation = age * (1.65F + i * 0.14F) + i * 29.0F;
            float halfLength = 0.44F + i * 0.07F;
            float halfWidth = 0.05F + i * 0.012F;
            float shimmer = 0.02F + (float) Math.sin(age * 0.08F + i) * 0.01F;
            int color = i % 2 == 0 ? VIOLET : CYAN;

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            poseStack.translate(0.0D, 0.02D + i * 0.002D, 0.0D);
            quad(buffer, poseStack.last(),
                    -halfLength, shimmer, -halfWidth,
                    halfLength, shimmer, -halfWidth * 0.72F,
                    halfLength * 0.82F, shimmer, halfWidth,
                    -halfLength * 0.84F, shimmer, halfWidth * 0.78F,
                    color, 0.0F, 1.0F, 0.0F);
            quad(buffer, poseStack.last(),
                    -halfLength * 0.52F, shimmer + 0.01F, -halfWidth * 0.45F,
                    halfLength * 0.52F, shimmer + 0.01F, -halfWidth * 0.35F,
                    halfLength * 0.44F, shimmer + 0.01F, halfWidth * 0.45F,
                    -halfLength * 0.44F, shimmer + 0.01F, halfWidth * 0.35F,
                    WHITE_CORE, 0.0F, 1.0F, 0.0F);
            poseStack.popPose();
        }
    }

    private static void drawSkyTear(VertexConsumer buffer, PoseStack poseStack, float age) {
        for (int i = 0; i < 7; i++) {
            float sway = (float) Math.sin(age * 0.045F + i * 0.8F) * 0.035F;
            float topOffset = 0.28F + i * 0.045F;
            float baseOffset = 0.16F + i * 0.025F;
            float height = 0.96F + i * 0.06F;
            int color = i % 2 == 0 ? VIOLET : CYAN;

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 4.0F));
            quad(buffer, poseStack.last(),
                    -baseOffset + sway, -height * 0.54F, 0.0F,
                    baseOffset + sway, -height * 0.50F, 0.0F,
                    topOffset - sway, height * 0.48F, 0.0F,
                    -topOffset - sway, height * 0.54F, 0.0F,
                    color, 0.0F, 0.0F, 1.0F);
            quad(buffer, poseStack.last(),
                    -baseOffset * 0.45F + sway * 0.35F, -height * 0.40F, 0.01F,
                    baseOffset * 0.45F + sway * 0.35F, -height * 0.36F, 0.01F,
                    topOffset * 0.40F - sway * 0.35F, height * 0.36F, 0.01F,
                    -topOffset * 0.40F - sway * 0.35F, height * 0.40F, 0.01F,
                    WHITE_CORE, 0.0F, 0.0F, 1.0F);
            poseStack.popPose();
        }
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             int color, float nx, float ny, float nz) {
        vertex(buffer, pose, x0, y0, z0, color, nx, ny, nz);
        vertex(buffer, pose, x1, y1, z1, color, nx, ny, nz);
        vertex(buffer, pose, x2, y2, z2, color, nx, ny, nz);
        vertex(buffer, pose, x3, y3, z3, color, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                .setNormal(pose, nx, ny, nz);
    }

    private static int color(int red, int green, int blue, int alpha) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | (blue & 255);
    }
}
