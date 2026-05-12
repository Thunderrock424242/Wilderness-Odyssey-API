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
    private static final float[][] SKY_CROSS_SWEEP = {
            {-0.74F, -0.18F},
            {-0.34F, -0.06F},
            {0.02F, 0.06F},
            {0.34F, 0.20F},
            {0.72F, 0.28F}
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
                poseStack.translate(0.0D, 0.20D, 0.0D);
                poseStack.scale(34.0F, 1.0F, 26.0F);
                drawSkyTear(buffer, poseStack, age);
            }
            case BEFORE_GROUND_RETURN -> {
                poseStack.scale(3.4F, 1.0F, 2.8F);
                drawGroundTear(buffer, poseStack, age);
            }
            case TRANSIENT_RETURN -> {
                poseStack.scale(2.8F, 1.0F, 2.3F);
                drawGroundTear(buffer, poseStack, age);
            }
            case OVERWORLD_SINKHOLE -> {
                poseStack.scale(7.4F, 1.0F, 6.0F);
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
        return 512;
    }

    private static void drawGroundTear(VertexConsumer buffer, PoseStack poseStack, float age) {
        float pulse = 1.0F + (float) Math.sin(age * 0.08F) * 0.08F;
        float sway = (float) Math.sin(age * 0.022F) * 2.2F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(18.0F + sway));
        drawHorizontalRibbon(buffer, poseStack, GROUND_MAIN_PATH, 0.34F * pulse, -0.12F, AURORA_VIOLET);
        drawHorizontalRibbon(buffer, poseStack, GROUND_MAIN_PATH, 0.28F * pulse, 0.10F, AURORA_CYAN);
        drawHorizontalRibbonVolume(buffer, poseStack, GROUND_MAIN_PATH, 0.20F * pulse, 0.12F, VIOLET);
        drawHorizontalRibbonVolume(buffer, poseStack, GROUND_MAIN_PATH, 0.12F * pulse, 0.08F, CYAN);
        drawHorizontalRibbonVolume(buffer, poseStack, GROUND_MAIN_PATH, 0.050F * pulse, 0.035F, WHITE_CORE);
        drawHorizontalRibbonVolume(buffer, poseStack, GROUND_BRANCH_LEFT, 0.10F * pulse, 0.060F, CYAN);
        drawHorizontalRibbonVolume(buffer, poseStack, GROUND_BRANCH_RIGHT, 0.10F * pulse, 0.060F, CYAN);
        drawHorizontalRibbonVolume(buffer, poseStack, GROUND_BRANCH_LEFT, 0.040F * pulse, 0.028F, WHITE_CORE);
        drawHorizontalRibbonVolume(buffer, poseStack, GROUND_BRANCH_RIGHT, 0.040F * pulse, 0.028F, WHITE_CORE);
        poseStack.popPose();
    }

    private static void drawSkyTear(VertexConsumer buffer, PoseStack poseStack, float age) {
        float pulse = 1.0F + (float) Math.sin(age * 0.05F) * 0.10F;
        float driftX = (float) Math.sin(age * 0.014F) * 0.052F;
        float driftY = (float) Math.cos(age * 0.011F) * 0.060F;
        float driftZ = (float) Math.cos(age * 0.017F) * 0.048F;
        float twist = (float) Math.sin(age * 0.012F) * 3.4F;

        poseStack.pushPose();
        poseStack.translate(driftX, driftY, driftZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(twist));
        for (float rotation : new float[] {-12.0F, 0.0F, 12.0F}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            drawHorizontalRibbon(buffer, poseStack, SKY_MAIN_PATH, 0.58F * pulse, -0.26F, AURORA_VIOLET);
            drawHorizontalRibbon(buffer, poseStack, SKY_MAIN_PATH, 0.48F * pulse, 0.18F, AURORA_CYAN);
            drawHorizontalRibbon(buffer, poseStack, SKY_CROSS_SWEEP, 0.40F * pulse, -0.18F, AURORA_CYAN);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_MAIN_PATH, 0.34F * pulse, 0.22F, VIOLET);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_MAIN_PATH, 0.22F * pulse, 0.16F, CYAN);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_MAIN_PATH, 0.090F * pulse, 0.070F, WHITE_CORE);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_BRANCH_LEFT, 0.18F * pulse, 0.12F, CYAN);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_BRANCH_RIGHT, 0.18F * pulse, 0.12F, CYAN);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_CROSS_SWEEP, 0.16F * pulse, 0.10F, VIOLET);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_BRANCH_LEFT, 0.072F * pulse, 0.050F, WHITE_CORE);
            drawHorizontalRibbonVolume(buffer, poseStack, SKY_BRANCH_RIGHT, 0.072F * pulse, 0.050F, WHITE_CORE);
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

    private static void drawHorizontalRibbonVolume(VertexConsumer buffer, PoseStack poseStack, float[][] points, float halfWidth, float halfHeight, int color) {
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

            float nx = -dz / length;
            float nz = dx / length;
            float px = nx * halfWidth;
            float pz = nz * halfWidth;
            float topY = halfHeight;
            float bottomY = -halfHeight;

            quad(buffer, poseStack.last(),
                    x0 + px, topY, z0 + pz,
                    x1 + px, topY, z1 + pz,
                    x1 - px, topY, z1 - pz,
                    x0 - px, topY, z0 - pz,
                    color, 0.0F, 1.0F, 0.0F);
            quad(buffer, poseStack.last(),
                    x0 - px, bottomY, z0 - pz,
                    x1 - px, bottomY, z1 - pz,
                    x1 + px, bottomY, z1 + pz,
                    x0 + px, bottomY, z0 + pz,
                    color, 0.0F, -1.0F, 0.0F);
            quad(buffer, poseStack.last(),
                    x0 + px, bottomY, z0 + pz,
                    x1 + px, bottomY, z1 + pz,
                    x1 + px, topY, z1 + pz,
                    x0 + px, topY, z0 + pz,
                    color, nx, 0.0F, nz);
            quad(buffer, poseStack.last(),
                    x0 - px, topY, z0 - pz,
                    x1 - px, topY, z1 - pz,
                    x1 - px, bottomY, z1 - pz,
                    x0 - px, bottomY, z0 - pz,
                    color, -nx, 0.0F, -nz);
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
