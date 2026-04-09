package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WaterSourceMixin
 *
 * Suppresses the vanilla "infinite water source" mechanic.
 * Vanilla creates a new source block whenever a flowing block
 * is surrounded by 2+ source blocks. This mixin cancels that,
 * making all water finite and physically driven.
 */
@Mixin(WaterFluid.class)
public class WaterSourceMixin {

    @Inject(
        method = "canConvertToSource",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventInfiniteSource(LevelReader level, BlockPos pos,
                                        CallbackInfoReturnable<Boolean> cir) {
        // Always return false — water will never self-replicate
        cir.setReturnValue(false);
    }
}
