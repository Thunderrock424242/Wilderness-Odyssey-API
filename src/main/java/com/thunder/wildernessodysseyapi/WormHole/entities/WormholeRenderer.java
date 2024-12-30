package com.thunder.wildernessodysseyapi.WormHole.entities;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class WormholeRenderer extends EntityRenderer<EntityWormhole> {
    private ShaderInstance vortexShader;

    public WormholeRenderer(EntityRendererProvider.Context context) {
        super(context);
        // Load the shader
        this.vortexShader = Minecraft.getInstance().getResourceManager()
                .loadShader(new ResourceLocation("wormholemod", "shaders/post/vortex.json"));
    }

    @Override
    public void render(EntityWormhole entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Use the shader during rendering
        RenderSystem.setShader(() -> vortexShader);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * @param entityWormhole
     * @return
     */
    @Override
    public ResourceLocation getTextureLocation(EntityWormhole entityWormhole) {
        return null;
    }
}
