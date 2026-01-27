package com.thunder.wildernessodysseyapi.Client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.item.cloak.CloakItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

public class CloakRenderLayer extends RenderLayer<Player, PlayerModel<Player>> {
    private static final ResourceLocation CLOAK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "textures/entity/cloak/cloak.png"
    );
    private static final float CLOAK_ALPHA = 0.55f;

    public CloakRenderLayer(RenderLayerParent<Player, PlayerModel<Player>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       Player player,
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
        getParentModel().renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, CLOAK_ALPHA);
    }

    private static boolean shouldRenderCloak(Player player) {
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
