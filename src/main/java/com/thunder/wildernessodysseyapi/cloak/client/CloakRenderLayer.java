package com.thunder.wildernessodysseyapi.cloak.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuDiagnostics;
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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;

/**
 * Player render layer that draws an unstable purple silhouette around cloaked players.
 */
public class CloakRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation CLOAK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "textures/entity/neural_frame.png"
    );
    private static final float CORE_ALPHA = 0.34F;
    private static final float ECHO_ALPHA = 0.18F;

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

        try (GpuDiagnostics.Scope ignored = GpuDiagnostics.scope("cloak.outline")) {

            float time = ageInTicks + partialTick;
            float pulse = 0.5F + 0.5F * Mth.sin(time * 0.22F + player.getId());

            renderDistortionPass(poseStack, buffer, packedLight, 0.0D, 0.0D, 0.0D,
                    1.0F + pulse * 0.01F, CORE_ALPHA, 178, 62, 255);

            float horizontalWave = Mth.sin(time * 0.73F + player.getId() * 0.41F) * 0.026F;
            float verticalWave = Mth.cos(time * 0.47F) * 0.018F;
            renderDistortionPass(poseStack, buffer, packedLight, horizontalWave, verticalWave, -horizontalWave * 0.45D,
                    1.018F, ECHO_ALPHA, 110, 210, 255);

            float counterWave = Mth.cos(time * 0.61F + 1.8F) * 0.022F;
            renderDistortionPass(poseStack, buffer, packedLight, -counterWave, -verticalWave * 0.7D, counterWave * 0.35D,
                    1.012F, ECHO_ALPHA, 225, 76, 255);
        }
    }

    private void renderDistortionPass(PoseStack poseStack,
                                      MultiBufferSource buffer,
                                      int packedLight,
                                      double offsetX,
                                      double offsetY,
                                      double offsetZ,
                                      float scale,
                                      float alpha,
                                      int red,
                                      int green,
                                      int blue) {
        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, offsetZ);
        poseStack.scale(scale, 1.0F + (scale - 1.0F) * 0.65F, scale);
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(CLOAK_TEXTURE));
        int packedColor = FastColor.ARGB32.color((int) (alpha * 255.0F), red, green, blue);
        getParentModel().renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, packedColor);
        poseStack.popPose();
    }

    private static boolean shouldRenderCloak(AbstractClientPlayer player) {
        return player.hasEffect(MobEffects.INVISIBILITY);
    }
}
