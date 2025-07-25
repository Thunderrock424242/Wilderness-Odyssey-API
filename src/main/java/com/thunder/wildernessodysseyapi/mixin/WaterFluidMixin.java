package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tweaks water tick delay so newly placed water flows every tick
 * instead of every 5 ticks, giving smoother water propagation when
 * using a bucket.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {
    @Inject(method = "getTickDelay", at = @At("HEAD"), cancellable = true)
    private void wildernessapi$fastTicks(LevelReader level, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(1);
    }
}
