package com.thunder.wildernessodysseyapi.mixin;

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
    private static final float CROUCH_VISIBILITY_MULTIPLIER = 0.5F;

    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$reduceMobDetectionWhenCrouching(
            Entity viewer,
            CallbackInfoReturnable<Float> callbackInfo
    ) {
        if (!(viewer instanceof Mob)) {
            return;
        }
        if (!((Object) this instanceof Player player) || !player.isCrouching()) {
            return;
        }
        float visibility = callbackInfo.getReturnValue();
        callbackInfo.setReturnValue(Math.max(0.0F, visibility * CROUCH_VISIBILITY_MULTIPLIER));
    }
}
