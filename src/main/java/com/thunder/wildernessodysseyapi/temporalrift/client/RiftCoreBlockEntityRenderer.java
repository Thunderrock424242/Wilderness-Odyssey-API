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
    private static final int AURORA_VIOLET = color(148, 94, 255, 84);
    private static final int AURORA_CYAN = color(86, 232, 255, 72);
    private static final float[][] SKY_MAIN_PATH = {
            {-0.62F, 0.52F},
            {-0.32F, 0.28F},
            {-0.10F, 0.08F},
            {0.04F, -0.06F},
            {0.18F, -0.26F},
            {0.46F, -0.54F}
    };
    private static final float[][] SKY_BRANCH_LEFT = {
            {-0.10F, 0.08F},
            {-0.36F, 0.18F},
            {-0.68F, 0.36F},
            {-0.94F, 0.44F}
    };
    private static final float[][] SKY_BRANCH_RIGHT = {
            {0.04F, -0.06F},
            {0.30F, 0.02F},
            {0.58F, 0.16F},
            {0.88F, 0.24F}
    };
    private static final float[][] GROUND_MAIN_PATH = {
            {-0.72F, -0.10F},
            {-0.38F, -0.02F},
            {-0.12F, 0.06F},
            {0.14F, -0.02F},
            {0.42F, 0.05F},
            {0.76F, -0.08F}
    };
    private static final float[][] GROUND_BRANCH_LEFT = {
            {-0.18F, 0.04F},
            {-0.38F, 0.26F},
            {-0.54F, 0.44F}
    };
    private static final float[][] GROUND_BRANCH_RIGHT = {
            {0.18F, -0.01F},
            {0.34F, -0.26F},
            {0.54F, -0.42F}
    };

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
                poseStack.scale(8.6F, 6.8F, 1.0F);
                drawSkyTear(buffer, poseStack, age);
            }
            case BEFORE_GROUND_RETURN -> {
                poseStack.scale(2.0F, 1.0F, 2.0F);
                drawGroundTear(buffer, poseStack, age);
            }
            case TRANSIENT_RETURN -> {
                poseStack.scale(1.55F, 1.0F, 1.55F);
                drawGroundTear(buffer, poseStack, age);
            }
            case OVERWORLD_SINKHOLE -> {
                poseStack.scale(2.45F, 1.0F, 2.45F);
                drawGroundTear(buffer, poseStack, age);
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
        return 320;
    }

    private static void drawGroundTear(VertexConsumer buffer, PoseStack poseStack, float age) {
        float pulse = 1.0F + (float) Math.sin(age * 0.08F) * 0.08F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(18.0F));
        drawHorizontalRibbon(buffer, poseStack, GROUND_MAIN_PATH, 0.10F * pulse, 0.018F, VIOLET);
        drawHorizontalRibbon(buffer, poseStack, GROUND_MAIN_PATH, 0.055F * pulse, 0.024F, CYAN);
        drawHorizontalRibbon(buffer, poseStack, GROUND_MAIN_PATH, 0.022F * pulse, 0.030F, WHITE_CORE);
        drawHorizontalRibbon(buffer, poseStack, GROUND_BRANCH_LEFT, 0.055F * pulse, 0.022F, CYAN);
        drawHorizontalRibbon(buffer, poseStack, GROUND_BRANCH_RIGHT, 0.055F * pulse, 0.022F, CYAN);
        drawHorizontalRibbon(buffer, poseStack, GROUND_BRANCH_LEFT, 0.025F * pulse, 0.028F, WHITE_CORE);
        drawHorizontalRibbon(buffer, poseStack, GROUND_BRANCH_RIGHT, 0.025F * pulse, 0.028F, WHITE_CORE);
        poseStack.popPose();
    }

    private static void drawSkyTear(VertexConsumer buffer, PoseStack poseStack, float age) {
        float pulse = 1.0F + (float) Math.sin(age * 0.05F) * 0.10F;
        float driftX = (float) Math.sin(age * 0.014F) * 0.052F;
        float driftY = (float) Math.cos(age * 0.011F) * 0.028F;
        float twist = (float) Math.sin(age * 0.012F) * 2.6F;

        poseStack.pushPose();
        poseStack.translate(driftX, driftY, 0.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(twist));
        for (float rotation : new float[] {-24.0F, 0.0F, 24.0F}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            drawVerticalRibbon(buffer, poseStack, SKY_MAIN_PATH, 0.185F * pulse, -0.180F, AURORA_VIOLET);
            drawVerticalRibbon(buffer, poseStack, SKY_MAIN_PATH, 0.165F * pulse, 0.180F, AURORA_CYAN);
            drawVerticalRibbonVolume(buffer, poseStack, SKY_MAIN_PATH, 0.102F * pulse, 0.105F, VIOLET);
            drawVerticalRibbonVolume(buffer, poseStack, SKY_MAIN_PATH, 0.058F * pulse, 0.066F, CYAN);
            drawVerticalRibbonVolume(buffer, poseStack, SKY_MAIN_PATH, 0.022F * pulse, 0.028F, WHITE_CORE);
            drawVerticalRibbonVolume(buffer, poseStack, SKY_BRANCH_LEFT, 0.060F * pulse, 0.050F, CYAN);
            drawVerticalRibbonVolume(buffer, poseStack, SKY_BRANCH_RIGHT, 0.060F * pulse, 0.050F, CYAN);
            drawVerticalRibbonVolume(buffer, poseStack, SKY_BRANCH_LEFT, 0.026F * pulse, 0.022F, WHITE_CORE);
            drawVerticalRibbonVolume(buffer, poseStack, SKY_BRANCH_RIGHT, 0.026F * pulse, 0.022F, WHITE_CORE);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawVerticalRibbon(VertexConsumer buffer, PoseStack poseStack, float[][] points, float halfWidth, float z, int color) {
        for (int i = 0; i < points.length - 1; i++) {
            float x0 = points[i][0];
            float y0 = points[i][1];
            float x1 = points[i + 1][0];
            float y1 = points[i + 1][1];
            float dx = x1 - x0;
            float dy = y1 - y0;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.0001F) {
                continue;
            }

            float px = -dy / length * halfWidth;
            float py = dx / length * halfWidth;
            quad(buffer, poseStack.last(),
                    x0 + px, y0 + py, z,
                    x1 + px, y1 + py, z,
                    x1 - px, y1 - py, z,
                    x0 - px, y0 - py, z,
                    color, 0.0F, 0.0F, 1.0F);
        }
    }

    private static void drawVerticalRibbonVolume(VertexConsumer buffer, PoseStack poseStack, float[][] points, float halfWidth, float halfDepth, int color) {
        for (int i = 0; i < points.length - 1; i++) {
            float x0 = points[i][0];
            float y0 = points[i][1];
            float x1 = points[i + 1][0];
            float y1 = points[i + 1][1];
            float dx = x1 - x0;
            float dy = y1 - y0;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.0001F) {
                continue;
            }

            float nx = -dy / length;
            float ny = dx / length;
            float px = nx * halfWidth;
            float py = ny * halfWidth;
            float frontZ = halfDepth;
            float backZ = -halfDepth;

            quad(buffer, poseStack.last(),
                    x0 + px, y0 + py, frontZ,
                    x1 + px, y1 + py, frontZ,
                    x1 - px, y1 - py, frontZ,
                    x0 - px, y0 - py, frontZ,
                    color, 0.0F, 0.0F, 1.0F);
            quad(buffer, poseStack.last(),
                    x0 - px, y0 - py, backZ,
                    x1 - px, y1 - py, backZ,
                    x1 + px, y1 + py, backZ,
                    x0 + px, y0 + py, backZ,
                    color, 0.0F, 0.0F, -1.0F);
            quad(buffer, poseStack.last(),
                    x0 + px, y0 + py, backZ,
                    x1 + px, y1 + py, backZ,
                    x1 + px, y1 + py, frontZ,
                    x0 + px, y0 + py, frontZ,
                    color, nx, ny, 0.0F);
            quad(buffer, poseStack.last(),
                    x0 - px, y0 - py, frontZ,
                    x1 - px, y1 - py, frontZ,
                    x1 - px, y1 - py, backZ,
                    x0 - px, y0 - py, backZ,
                    color, -nx, -ny, 0.0F);
        }
    }

    private static void drawHorizontalRibbon(VertexConsumer buffer, PoseStack poseStack, float[][] points, float halfWidth, float y, int color) {
        for (int i = 0; i < points.length - 1; i++) {
            float x0 = points[i][0];
            float z0 = points[i][1];
            float x1 = points[i + 1][0];
            float z1 = points[i + 1][1];
            float dx = x1 - x0;
            float dz = z1 - z0;
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            if (length <= 0.0001F) {
                continue;
            }

            float px = -dz / length * halfWidth;
            float pz = dx / length * halfWidth;
            quad(buffer, poseStack.last(),
                    x0 + px, y, z0 + pz,
                    x1 + px, y, z1 + pz,
                    x1 - px, y, z1 - pz,
                    x0 - px, y, z0 - pz,
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
