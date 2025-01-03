package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockAndTintGetter;
import org.joml.Matrix4f;

public class WaveRenderer {
    private static final Minecraft MC = Minecraft.getInstance();
    private static ShaderInstance waveShader;

    // Initialize the shader (called during mod initialization)
    public static void initShader() {
        try {
            // Use tryParse for resource locations
            ResourceLocation vertexShaderLocation = ResourceLocation.tryParse("mymod:shaders/wave_shader.vsh");
            ResourceLocation fragmentShaderLocation = ResourceLocation.tryParse("mymod:shaders/wave_shader.fsh");

            // Validate resource locations
            if (vertexShaderLocation == null || fragmentShaderLocation == null) {
                System.err.println("Invalid resource location for shaders!");
                return;
            }

            // Initialize the shader
            waveShader = new ShaderInstance(vertexShaderLocation, fragmentShaderLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void renderWave(BlockState state, BlockPos pos, BlockAndTintGetter world, PoseStack poseStack, int light, int overlay) {
        if (waveShader != null) {
            RenderSystem.setShader(() -> waveShader);

            // Pass time as a uniform to the shader
            waveShader.safeGetUniform("time").set(System.currentTimeMillis() / 1000.0f);
        }

        // Calculate wave offset based on position and time
        float waveOffset = (float) (Math.sin(pos.getX() * 0.1 + ((Level) world).getDayTime() * 0.01) * 0.05);

        poseStack.pushPose();
        poseStack.translate(0, waveOffset, 0);

        // Render the block model with wave motion
        MC.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), MC.renderBuffers().bufferSource().getBuffer(RenderType.translucent()),
                state, MC.getBlockRenderer().getBlockModel(state), 1.0f, 1.0f, 1.0f, light, overlay);

        poseStack.popPose();

        // Reset the shader to avoid affecting other rendering
        RenderSystem.setShader(() -> (ShaderInstance) MC.renderBuffers().bufferSource().getBuffer(RenderType.translucent()));
    }

    public static void renderFoam(BlockPos pos, PoseStack poseStack, int light) {
        // Use tryParse to safely parse the resource location
        ResourceLocation foamTextureLocation = ResourceLocation.tryParse("mymod:blocks/foam_overlay");

        // Handle invalid resource location
        if (foamTextureLocation == null) {
            System.err.println("Invalid resource location for foam texture!");
            return;
        }

        // Get the foam texture sprite
        TextureAtlasSprite foamTexture = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(foamTextureLocation);

        // Get the rendering buffer
        VertexConsumer buffer = Minecraft.getInstance()
                .renderBuffers()
                .bufferSource()
                .getBuffer(RenderType.translucent());

        // UV coordinates for the texture
        float minU = foamTexture.getU0();
        float maxU = foamTexture.getU1();
        float minV = foamTexture.getV0();
        float maxV = foamTexture.getV1();

        // Push PoseStack for transformations
        poseStack.pushPose();

        // Example of applying custom translation (like wave or position offset)
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

        // Add vertices for the quad
        buffer.vertex(poseStack.last().pose(), -1.0F, 1.0F, 0.0F)
                .color(255, 255, 255, 255)
                .uv(minU, minV)
                .uv2(light)
                .endVertex();

        buffer.vertex(poseStack.last().pose(), 1.0F, 1.0F, 0.0F)
                .color(255, 255, 255, 255)
                .uv(maxU, minV)
                .uv2(light)
                .endVertex();

        buffer.vertex(poseStack.last().pose(), 1.0F, 0.0F, 0.0F)
                .color(255, 255, 255, 255)
                .uv(maxU, maxV)
                .uv2(light)
                .endVertex();

        buffer.vertex(poseStack.last().pose(), -1.0F, 0.0F, 0.0F)
                .color(255, 255, 255, 255)
                .uv(minU, maxV)
                .uv2(light)
                .endVertex();

        // Pop PoseStack to restore the previous state
        poseStack.popPose();
    }
}
