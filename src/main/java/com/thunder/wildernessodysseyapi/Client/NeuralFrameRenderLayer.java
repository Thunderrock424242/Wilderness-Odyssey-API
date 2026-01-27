package com.thunder.wildernessodysseyapi.Client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

public class NeuralFrameRenderLayer extends RenderLayer<Player, PlayerModel<Player>> {
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "textures/entity/neural_frame.png"
    );
    private final NeuralFrameModel model;

    public NeuralFrameRenderLayer(RenderLayerParent<Player, PlayerModel<Player>> parent) {
        super(parent);
        this.model = new NeuralFrameModel(Minecraft.getInstance().getEntityModels().bakeLayer(NeuralFrameModel.LAYER_LOCATION));
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
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return;
        }

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(FRAME_TEXTURE));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
    }
}
