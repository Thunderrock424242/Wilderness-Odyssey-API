package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.StructureBlockRenderer;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureBlockRenderer.class)
public abstract class StructureBlockRendererMixin {

    @Unique
    private boolean wildernessodysseyapi$depthForced;

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/StructureBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"))
    private void wildernessodysseyapi$startOverlayRender(StructureBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo ci) {
        if (!wildernessodysseyapi$shouldForceOverlay(blockEntity)) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        wildernessodysseyapi$depthForced = true;
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/StructureBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("TAIL"))
    private void wildernessodysseyapi$finishOverlayRender(StructureBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo ci) {
        if (!wildernessodysseyapi$depthForced) {
            return;
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        wildernessodysseyapi$depthForced = false;
    }

    @Inject(method = "getViewDistance", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$extendViewDistance(CallbackInfoReturnable<Integer> cir) {
        int radius = StructureBlockSettings.MAX_STRUCTURE_OFFSET + StructureBlockSettings.MAX_STRUCTURE_SIZE;
        int extended = Math.max(512, radius);
        cir.setReturnValue(extended);
    }

    @Unique
    private static boolean wildernessodysseyapi$shouldForceOverlay(StructureBlockEntity blockEntity) {
        StructureMode mode = blockEntity.getMode();
        return mode == StructureMode.SAVE || mode == StructureMode.LOAD;
    }
}
