package com.thunder.wildernessodysseyapi.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.entity.RiftMawEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RiftMawRenderer extends MobRenderer<RiftMawEntity, HumanoidModel<RiftMawEntity>> {
    private static final ResourceLocation PLACEHOLDER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "textures/entity/neural_frame.png");

    public RiftMawRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 1.15F);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftMawEntity entity) {
        return PLACEHOLDER_TEXTURE;
    }

    @Override
    protected void scale(RiftMawEntity entity, PoseStack poseStack, float partialTickTime) {
        float emerge = Mth.clamp((entity.getEmergeTicks() + partialTickTime) / 60.0F, 0.25F, 1.0F);
        poseStack.scale(1.85F * emerge, 2.08F * emerge, 1.85F * emerge);
    }
}
