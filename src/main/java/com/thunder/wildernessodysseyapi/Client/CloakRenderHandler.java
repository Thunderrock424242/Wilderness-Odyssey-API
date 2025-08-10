package com.thunder.wildernessodysseyapi.Client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * Renders cloaked entities as a translucent pixelated box.
 */
public class CloakRenderHandler {

    /**
     * Render the cloak effect for a single entity.
     */
    public static void renderCloak(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();

        poseStack.translate(-width / 2.0f, 0.0f, -width / 2.0f);
        poseStack.scale(width, height, width);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.GLASS.defaultBlockState(), poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
