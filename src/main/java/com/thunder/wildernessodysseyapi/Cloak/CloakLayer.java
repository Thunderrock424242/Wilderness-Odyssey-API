package com.thunder.wildernessodysseyapi.Cloak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

public class CloakLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public CloakLayer(RenderLayerParent<T, M> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void render(PoseStack matrixStack, MultiBufferSource buffer, int light, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        entity.getCapability(MOD_ID.CLOAK_CAPABILITY).ifPresent(cloak -> {
            if (cloak.isCloakEnabled()) {
                CloakRenderHandler.applyCloakTexture((Player) entity);
                VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
                this.getParentModel().renderToBuffer(matrixStack, vertexconsumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return new ResourceLocation(MOD_ID, "textures/entity/cloak.png"); // Default texture
    }
}
