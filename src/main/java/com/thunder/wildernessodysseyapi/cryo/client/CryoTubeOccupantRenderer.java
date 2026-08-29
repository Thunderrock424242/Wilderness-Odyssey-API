package com.thunder.wildernessodysseyapi.cryo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thunder.wildernessodysseyapi.cinematic.client.CinematicClientController;
import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupClientPresentation;
import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupPresentationModel;
import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupSequence;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Adds only the cinematic player occupant over the original baked cryo-tube model.
 *
 * <p>Minecraft remains responsible for rendering the unchanged Blockbench JSON.
 * This renderer never draws, replaces, or animates the tube geometry.</p>
 */
public final class CryoTubeOccupantRenderer implements BlockEntityRenderer<CryoTubeBlockEntity> {
    public CryoTubeOccupantRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            CryoTubeBlockEntity tube,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        CinematicClientController cinematic = CinematicClientController.get();
        if (!cinematic.isActive()
                || !CryoWakeupSequence.ID.equals(cinematic.sequenceId())
                || !tube.getBlockPos().equals(cinematic.anchor())
                || !CryoWakeupClientPresentation.shouldRenderOccupant(cinematic.stageId())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        BlockState state = tube.getBlockState();
        Direction facing = state.hasProperty(CryoTubeBlock.BlockImpl.FACING)
                ? state.getValue(CryoTubeBlock.BlockImpl.FACING)
                : Direction.NORTH;
        float time = cinematic.sequenceElapsedTicks(partialTick);
        float progress = cinematic.stageProgress(partialTick);
        double bob = Math.sin(time * 0.075D) * 0.025D;
        float sway = (float) Math.sin(time * 0.045D) * 1.35F;

        poseStack.pushPose();
        rotateWithTube(poseStack, facing);
        renderSuspensionVolume(poseStack, bufferSource, cinematic.stageId(), progress, time);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(
                0.5D - facing.getStepX() * 0.08D,
                -0.52D + bob,
                0.5D - facing.getStepZ() * 0.08D
        );
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway));
        applyRecline(poseStack, facing);
        poseStack.scale(0.86F, 0.86F, 0.86F);
        PlayerRenderer renderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
        int softMedicalLight = LightTexture.pack(11, 15);
        renderer.render(player, facing.toYRot(), partialTick, poseStack, bufferSource, softMedicalLight);
        poseStack.popPose();

        poseStack.pushPose();
        rotateWithTube(poseStack, facing);
        renderLifeSupportRig(poseStack, bufferSource);
        renderDiagnosticSweep(poseStack, bufferSource, cinematic.stageId(), progress, time);
        renderBubbles(poseStack, bufferSource, cinematic.stageId(), progress, time);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CryoTubeBlockEntity tube) {
        var pos = tube.getBlockPos();
        return new AABB(
                pos.getX() - 1.0D,
                pos.getY() - 1.0D,
                pos.getZ() - 1.0D,
                pos.getX() + 2.0D,
                pos.getY() + 2.0D,
                pos.getZ() + 2.0D
        );
    }

    private static void rotateWithTube(PoseStack poseStack, Direction facing) {
        float rotation = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
    }

    private static void applyRecline(PoseStack poseStack, Direction facing) {
        switch (facing) {
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-2.8F));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(2.8F));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-2.8F));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(2.8F));
            default -> {
            }
        }
    }

    private static void renderSuspensionVolume(
            PoseStack poseStack,
            MultiBufferSource buffers,
            net.minecraft.resources.ResourceLocation stage,
            float progress,
            float time
    ) {
        float fluid = fluidAmount(stage, progress);
        if (fluid <= 0.01F) {
            return;
        }
        float pulse = 0.92F + (float) Math.sin(time * 0.035F) * 0.08F;
        int alpha = Math.round(34.0F * fluid * pulse);
        float top = -0.66F + 1.82F * fluid;
        VertexConsumer vertices = buffers.getBuffer(CryoTubeRenderTypes.cinematic());
        box(vertices, poseStack.last().pose(), 0.08F, -0.66F, -0.12F, 0.92F, top, 0.53F,
                40, 117, 127, alpha);
    }

    private static void renderDiagnosticSweep(
            PoseStack poseStack,
            MultiBufferSource buffers,
            net.minecraft.resources.ResourceLocation stage,
            float progress,
            float time
    ) {
        if (!CryoWakeupSequence.MEDICAL_DIAGNOSTIC.equals(stage)
                && !CryoWakeupSequence.REVIVAL_PROTOCOL.equals(stage)
                && !CryoWakeupSequence.CARDIAC_PACING.equals(stage)) {
            return;
        }
        float y = -0.58F + (time * 0.018F % 1.0F) * 1.62F;
        int red = CryoWakeupSequence.CARDIAC_PACING.equals(stage) ? 255 : 104;
        int green = CryoWakeupSequence.CARDIAC_PACING.equals(stage) ? 82 : 239;
        int blue = CryoWakeupSequence.CARDIAC_PACING.equals(stage) ? 92 : 226;
        int alpha = 56 + Math.round((1.0F - Math.abs(progress * 2.0F - 1.0F)) * 24.0F);
        VertexConsumer vertices = buffers.getBuffer(CryoTubeRenderTypes.cinematic());
        box(vertices, poseStack.last().pose(), 0.10F, y, -0.135F, 0.90F, y + 0.018F, 0.545F,
                red, green, blue, alpha);
    }

    private static void renderBubbles(
            PoseStack poseStack,
            MultiBufferSource buffers,
            net.minecraft.resources.ResourceLocation stage,
            float progress,
            float time
    ) {
        float fluid = fluidAmount(stage, progress);
        if (fluid <= 0.04F) {
            return;
        }
        float bottom = -0.60F;
        float top = bottom + 1.68F * fluid;
        VertexConsumer vertices = buffers.getBuffer(CryoTubeRenderTypes.cinematic());
        Matrix4f matrix = poseStack.last().pose();
        for (int i = 0; i < 11; i++) {
            float phase = fractional(time * (0.0065F + i * 0.00022F) + i * 0.173F);
            float y = bottom + phase * Math.max(0.04F, top - bottom);
            float x = 0.19F + fractional(i * 0.381F) * 0.62F;
            float z = -0.02F + fractional(i * 0.613F) * 0.42F;
            float size = 0.010F + (i % 4) * 0.004F;
            crossedBillboard(vertices, matrix, x, y, z, size, 132, 245, 236, 76);
        }
    }

    private static void renderLifeSupportRig(PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer surfaces = buffers.getBuffer(CryoTubeRenderTypes.cinematic());
        Matrix4f matrix = poseStack.last().pose();

        // The mask sits just in front of the presentation player's face in the
        // tube's canonical north-facing space. rotateWithTube handles the other
        // block facings without changing the restored Blockbench model.
        box(surfaces, matrix, 0.365F, 0.80F, 0.305F, 0.635F, 0.98F, 0.385F,
                126, 225, 219, 68);
        box(surfaces, matrix, 0.455F, 0.76F, 0.275F, 0.545F, 0.84F, 0.335F,
                94, 201, 196, 112);

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(0.365D, 0.80D, 0.304D, 0.635D, 0.98D, 0.386D),
                0.52F, 0.94F, 0.90F, 0.82F);
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(0.615D, 0.72D, 0.325D, 0.655D, 0.84D, 0.375D),
                0.36F, 0.82F, 0.80F, 0.72F);
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(0.645D, 0.10D, 0.325D, 0.685D, 0.75D, 0.375D),
                0.36F, 0.82F, 0.80F, 0.72F);
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(0.665D, 0.08D, 0.325D, 0.90D, 0.13D, 0.375D),
                0.52F, 0.94F, 0.90F, 0.82F);
    }

    private static float fluidAmount(net.minecraft.resources.ResourceLocation stage, float progress) {
        if (CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage)) {
            float t = Math.max(0.0F, Math.min(1.0F, progress));
            return 1.0F - t * t * (3.0F - 2.0F * t);
        }
        return CryoWakeupPresentationModel.isExteriorStage(stage) ? 1.0F : 0.0F;
    }

    private static float fractional(float value) {
        return value - (float) Math.floor(value);
    }

    private static void crossedBillboard(
            VertexConsumer vertices,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float size,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        quad(vertices, matrix,
                x - size, y - size, z,
                x + size, y - size, z,
                x + size, y + size, z,
                x - size, y + size, z,
                red, green, blue, alpha);
        quad(vertices, matrix,
                x, y - size, z - size,
                x, y - size, z + size,
                x, y + size, z + size,
                x, y + size, z - size,
                red, green, blue, alpha);
    }

    private static void box(
            VertexConsumer vertices,
            Matrix4f matrix,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        quad(vertices, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, red, green, blue, alpha);
        quad(vertices, matrix, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, red, green, blue, alpha);
        quad(vertices, matrix, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, red, green, blue, alpha);
        quad(vertices, matrix, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, red, green, blue, alpha);
        quad(vertices, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
        quad(vertices, matrix, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, red, green, blue, alpha);
    }

    private static void quad(
            VertexConsumer vertices,
            Matrix4f matrix,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        vertices.addVertex(matrix, x0, y0, z0).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha);
    }
}
