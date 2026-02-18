package com.thunder.wildernessodysseyapi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.config.CurioRenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class ChipSetCurioRenderer implements ICurioRenderer {
    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack,
                                                                          SlotContext slotContext,
                                                                          PoseStack poseStack,
                                                                          RenderLayerParent<T, M> renderLayerParent,
                                                                          MultiBufferSource buffer,
                                                                          int packedLight,
                                                                          float limbSwing,
                                                                          float limbSwingAmount,
                                                                          float partialTick,
                                                                          float ageInTicks,
                                                                          float netHeadYaw,
                                                                          float headPitch) {
        if (!CurioRenderConfig.RENDER_CHIP_SET.get()) {
            return;
        }

        T entity = (T) slotContext.entity();
        poseStack.pushPose();
        ICurioRenderer.translateIfSneaking(poseStack, entity);

        M model = renderLayerParent.getModel();
        if (model instanceof HumanoidModel<?> humanoidModel) {
            ICurioRenderer.followHeadRotations(entity, humanoidModel.head);
            humanoidModel.head.translateAndRotate(poseStack);
        }

        poseStack.translate(0.35F, -0.2F, 0.25F);
        poseStack.scale(0.35F, 0.35F, 0.35F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.HEAD,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
    }
}
