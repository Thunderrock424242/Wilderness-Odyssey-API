package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.ocean.rendering.SmoothFluidRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {

    @Inject(method = "renderLiquid", at = @At("HEAD"), cancellable = true)
    private void injectSmoothWaterRendering(
            BlockPos pos,
            BlockAndTintGetter level,
            VertexConsumer consumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        if (fluidState.getType() == Fluids.WATER) {
            // Use a version of SmoothFluidRenderer that draws directly into the VertexConsumer
            SmoothFluidRenderer.renderDirect(consumer, level, pos, fluidState);
            ci.cancel(); // cancel vanilla rendering
        }
    }
}
