package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.crouching.CrouchNoiseHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$reduceMobDetectionWhenCrouching(
            Entity viewer,
            CallbackInfoReturnable<Double> callbackInfo
    ) {
        if (!(viewer instanceof Mob)) {
            return;
        }
        if (!((Object) this instanceof Player player) || !player.isCrouching()) {
            return;
        }
        double multiplier = CrouchNoiseHelper.getCrouchVisibilityMultiplier(player);
        if (multiplier <= 0.0D) {
            callbackInfo.setReturnValue(0.0D);
            return;
        }
        double visibility = callbackInfo.getReturnValue();
        callbackInfo.setReturnValue(Math.max(0.0D, visibility * multiplier));
    }
}
