package com.thunder.wildernessodysseyapi.Client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.item.cloak.CloakItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;

public class CloakRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation CLOAK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "textures/entity/cloak/cloak.png"
    );
    private static final float CLOAK_ALPHA = 0.55f;

    public CloakRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       AbstractClientPlayer player,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        if (!shouldRenderCloak(player)) {
            return;
        }

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(CLOAK_TEXTURE));
        int packedColor = FastColor.ARGB32.color((int) (CLOAK_ALPHA * 255.0F), 255, 255, 255);
        getParentModel().renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, packedColor);
    }

    private static boolean shouldRenderCloak(AbstractClientPlayer player) {
        if (!player.hasEffect(MobEffects.INVISIBILITY)) {
            return false;
        }

        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return false;
        }

        return CloakItem.isHoldingCloak(player)
                && CloakItem.hasCompassLink(player)
                && CloakItem.hasCloakChip(player);
    }
}
