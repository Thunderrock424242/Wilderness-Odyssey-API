package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.VanillaWaterParity;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows full Wilderness source blocks to participate in vanilla bubble columns. */
@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnWaterMixin {

    /** Extends only the private host predicate used to build and continue a column. */
    @Inject(method = "canExistIn", at = @At("RETURN"), cancellable = true)
    private static void wildernessOdysseyApi$acceptWildernessSource(
            BlockState state,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!callbackInfo.getReturnValue() && VanillaWaterParity.isFullBubbleColumnWater(state)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
