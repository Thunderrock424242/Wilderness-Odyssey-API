package com.thunder.wildernessodysseyapi.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.entity.RiftListenerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders the sensory Rift Listener with its entity-specific humanoid texture atlas. */
public class RiftListenerRenderer extends MobRenderer<RiftListenerEntity, HumanoidModel<RiftListenerEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "textures/entity/rift_listener.png");

    public RiftListenerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.55F);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftListenerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(RiftListenerEntity entity, PoseStack poseStack, float partialTickTime) {
        float width = entity.getListenerState() == RiftListenerEntity.STATE_HUNTING ? 1.12F : 0.92F;
        poseStack.scale(width, 1.72F, width);
    }
}
