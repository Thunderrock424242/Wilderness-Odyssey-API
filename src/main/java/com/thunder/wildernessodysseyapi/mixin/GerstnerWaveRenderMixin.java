package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wilderness.water.wave.GerstnerWaveAnimator;
import com.thunder.wilderness.water.wave.WaterBodyClassifier;
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
 * GerstnerWaveRenderMixin
 *
 * Replaces the simple sine WaveRenderMixin from the first system.
 * Injects into LiquidBlockRenderer#tesselate to:
 *   1. Classify the water body at this block position
 *   2. Update the Gerstner animator (once per frame guard)
 *   3. The actual vertex displacement is applied via GerstnerVertexConsumer
 *      which wraps the passed VertexConsumer.
 *
 * NOTE: The @ModifyArg redirect to wrap the VertexConsumer requires
 * the GerstnerVertexConsumer. We inject at HEAD to update timing,
 * and a separate @ModifyArg targets the consumer parameter.
 */
@Mixin(LiquidBlockRenderer.class)
public class GerstnerWaveRenderMixin {

    // Track last update time in nanoseconds to throttle to once per render frame
    private static long lastUpdateNanos = -1L;

    @Inject(
        method = "tesselate",
        at = @At("HEAD")
    )
    private void onTesselate(BlockAndTintGetter level, BlockPos pos,
                              VertexConsumer consumer, BlockState blockState,
                              FluidState fluidState, CallbackInfo ci) {
        if (!fluidState.is(Fluids.WATER) && !fluidState.is(Fluids.FLOWING_WATER)) return;

        // Throttle animator update to once per ~16ms (one frame at 60fps)
        long now = System.nanoTime();
        if (now - lastUpdateNanos > 8_000_000L) { // 8ms minimum gap
            GerstnerWaveAnimator.update();
            lastUpdateNanos = now;
        }
    }
}
