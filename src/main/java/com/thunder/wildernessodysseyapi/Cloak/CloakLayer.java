package com.thunder.wildernessodysseyapi.Cloak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(value = Dist.CLIENT)
public class CloakLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public CloakLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack matrixStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        // Check the same NBT boolean on the client‐side player
        boolean cloakEnabled = player.getPersistentData().getBoolean("cloakEnabled");
        if (!cloakEnabled) return;

        // Bind our offscreen texture (behind-player view)
        CloakRenderHandler.applyCloakTexture();

        // Use 'entityTranslucent' so we can see through the cloak
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(player)));
        this.getParentModel().body.render(matrixStack, consumer, packedLight, 0xF000F0);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        // This is just a dummy—our real texture is bound already via applyCloakTexture().
        return ResourceLocation.tryBuild(MOD_ID, "textures/entity/placeholder_cloak.png");
    }
}
