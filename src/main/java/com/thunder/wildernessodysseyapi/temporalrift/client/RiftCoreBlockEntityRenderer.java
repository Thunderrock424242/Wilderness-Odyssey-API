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

        drawSinkGlow(buffer, poseStack, age);
        drawVerticalTears(buffer, poseStack, age);

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

    private static void drawVerticalTears(VertexConsumer buffer, PoseStack poseStack, float age) {
        for (int i = 0; i < 5; i++) {
            float rotation = age * (2.8F + i * 0.22F) + i * 36.0F;
            float width = 0.38F + i * 0.055F;
            float height = 1.75F + (float) Math.sin(age * 0.08F + i) * 0.18F;
            int color = i % 2 == 0 ? VIOLET : CYAN;

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            poseStack.translate(0.0D, 0.02D + i * 0.025D, 0.0D);
            quad(buffer, poseStack.last(), -width, 0.0F, 0.0F, width, 0.06F, 0.0F, width * 0.34F, height, 0.0F, -width * 0.34F, height, 0.0F, color, 0.0F, 0.0F, 1.0F);
            quad(buffer, poseStack.last(), -width * 0.42F, 0.22F, 0.0F, width * 0.42F, 0.22F, 0.0F, width * 0.18F, height + 0.28F, 0.0F, -width * 0.18F, height + 0.28F, 0.0F, WHITE_CORE, 0.0F, 0.0F, 1.0F);
            poseStack.popPose();
        }
    }

    private static void drawSinkGlow(VertexConsumer buffer, PoseStack poseStack, float age) {
        float pulse = 0.82F + (float) Math.sin(age * 0.16F) * 0.12F;
        for (int i = 0; i < 4; i++) {
            float radius = pulse * (0.58F + i * 0.22F);
            int alpha = Math.max(28, 112 - i * 22);
            int ringColor = color(94 + i * 24, 20 + i * 12, 255, alpha);

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-age * (1.1F + i * 0.18F) + i * 18.0F));
            ring(buffer, poseStack.last(), radius, radius * 0.68F, ringColor);
            poseStack.popPose();
        }
    }

    private static void ring(VertexConsumer buffer, PoseStack.Pose pose, float outer, float inner, int color) {
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            float ox0 = (float) Math.cos(a0);
            float oz0 = (float) Math.sin(a0);
            float ox1 = (float) Math.cos(a1);
            float oz1 = (float) Math.sin(a1);
            quad(buffer, pose,
                    ox0 * outer, 0.01F, oz0 * outer,
                    ox1 * outer, 0.01F, oz1 * outer,
                    ox1 * inner, 0.01F, oz1 * inner,
                    ox0 * inner, 0.01F, oz0 * inner,
                    color, 0.0F, 1.0F, 0.0F);
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
