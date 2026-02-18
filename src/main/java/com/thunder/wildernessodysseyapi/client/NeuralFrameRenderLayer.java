package com.thunder.wildernessodysseyapi.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.config.CurioRenderConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.thunder.wildernessodysseyapi.item.cloak.CloakItem;
import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class NeuralFrameRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID,
            "textures/entity/neural_frame.png"
    );
    private final NeuralFrameModel model;

    public NeuralFrameRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        this.model = new NeuralFrameModel(Minecraft.getInstance().getEntityModels().bakeLayer(NeuralFrameModel.LAYER_LOCATION));
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
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return;
        }
        if (!CurioRenderConfig.RENDER_NEURAL_FRAME.get()) {
            return;
        }

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(FRAME_TEXTURE));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();

        if (CloakItem.hasCloakChip(player) && CurioRenderConfig.RENDER_CHIP_SET.get()) {
            ItemStack chipStack = new ItemStack(ModItems.CLOAK_CHIP.get());
            poseStack.pushPose();
            getParentModel().head.translateAndRotate(poseStack);
            poseStack.translate(0.35F, -0.2F, 0.25F);
            poseStack.scale(0.35F, 0.35F, 0.35F);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    chipStack,
                    ItemDisplayContext.HEAD,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    buffer,
                    player.level(),
                    player.getId()
            );
            poseStack.popPose();
        }
    }
}
