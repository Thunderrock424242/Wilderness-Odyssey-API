package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.cloak.CloakRenderHandler;
import com.thunder.wildernessodysseyapi.cloak.CloakItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents rendering of entities marked with the cloak tag.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CloakRenderMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$hideCloaked(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity.getTags().contains(CloakItem.CLOAK_TAG)) {
            CloakRenderHandler.renderCloak(entity, poseStack, buffer);
            ci.cancel();
        }
    }
}
