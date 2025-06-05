package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.ocean.rendering.SmoothFluidRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {
    @Inject(method = "renderLiquid", at = @At("HEAD"), cancellable = true)
    private void onRenderLiquid(BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (level.getFluidState(pos).getType() == Fluids.WATER) {
            SmoothFluidRenderer.render(level, pos, poseStack, buffer, Minecraft.getInstance().getFrameTime());
            cir.setReturnValue(true);
        }
    }
}