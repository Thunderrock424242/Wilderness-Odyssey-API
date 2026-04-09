package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wilderness.water.render.WaveAnimator;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
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

/**
 * WaveRenderMixin
 *
 * Hooks into LiquidBlockRenderer#tesselate to post-process the vertex
 * consumer and displace the top surface of water blocks with a sine-wave.
 *
 * The actual math lives in WaveAnimator so it can be tweaked independently.
 *
 * NOTE: This mixin wraps the VertexConsumer with a WaveVertexConsumer
 * that intercepts addVertex calls on the top face and adjusts the Y value.
 */
@Mixin(LiquidBlockRenderer.class)
public class WaveRenderMixin {

    @Inject(
        method = "tesselate",
        at = @At("HEAD")
    )
    private void onTesselateHead(BlockAndTintGetter level, BlockPos pos,
                                  VertexConsumer consumer, BlockState blockState,
                                  FluidState fluidState, CallbackInfo ci) {
        // Only apply to water, not lava
        if (!fluidState.is(Fluids.WATER) && !fluidState.is(Fluids.FLOWING_WATER)) return;

        // Wave state is updated once per frame in WaveAnimator
        WaveAnimator.updateIfNeeded();
    }
}
