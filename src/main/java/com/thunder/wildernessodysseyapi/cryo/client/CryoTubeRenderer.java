package com.thunder.wildernessodysseyapi.cryo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.cinematic.client.CinematicClientController;
import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupClientPresentation;
import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupSequence;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Animated tube renderer with a presentation-only local-player occupant during the exterior reveal. */
public final class CryoTubeRenderer extends GeoBlockRenderer<CryoTubeBlockEntity> {
    public CryoTubeRenderer(BlockEntityRendererProvider.Context context) {
        super(new CryoTubeModel());
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
        renderPresentationOccupant(tube, partialTick, poseStack, bufferSource, packedLight);
        super.render(tube, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    @Override
    public RenderType getRenderType(
            CryoTubeBlockEntity animatable,
            ResourceLocation texture,
            MultiBufferSource bufferSource,
            float partialTick
    ) {
        return RenderType.entityTranslucent(texture);
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

    private static void renderPresentationOccupant(
            CryoTubeBlockEntity tube,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
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
        double bob = Math.sin((minecraft.level.getGameTime() + partialTick) * 0.075D) * 0.025D;

        poseStack.pushPose();
        poseStack.translate(
                0.5D - facing.getStepX() * 0.08D,
                -0.52D + bob,
                0.5D - facing.getStepZ() * 0.08D
        );
        poseStack.scale(0.86F, 0.86F, 0.86F);
        PlayerRenderer renderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
        renderer.render(player, facing.toYRot(), partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
