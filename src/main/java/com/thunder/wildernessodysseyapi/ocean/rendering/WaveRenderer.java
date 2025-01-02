package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public class WaveRenderer {
    private static final Minecraft MC = Minecraft.getInstance();

    public static void renderWave(BlockState state, BlockPos pos, BlockAndTintGetter world, PoseStack poseStack, int light, int overlay) {
        BlockRenderDispatcher blockRenderer = MC.getBlockRenderer();
        BakedModel model = blockRenderer.getBlockModel(state);

        // Modify UVs dynamically for wave animation
        float waveOffset = (float) (Math.sin(pos.getX() * 0.1 + world.getDayTime() * 0.01) * 0.05);

        poseStack.pushPose();
        poseStack.translate(0, waveOffset, 0); // Move block up and down with wave effect

        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(), MC.renderBuffers().bufferSource().getBuffer(RenderType.translucent()),
                state, model, 1.0f, 1.0f, 1.0f, light, overlay);
        poseStack.popPose();
    }

    public static void renderFoam(BlockPos pos, PoseStack poseStack, int light) {
        // Render foam texture as an overlay
        TextureAtlasSprite foamTexture = MC.getTextureAtlas().apply(new Material(MC.getTextureAtlas(), new ResourceLocation("mymod:textures/blocks/foam_overlay")));
        MC.getBlockRenderer().renderSingleBlock(
                foamTexture.getModel(),
                poseStack, MC.renderBuffers().bufferSource().getBuffer(RenderType.translucent()),
                light, 0);
    }
}
