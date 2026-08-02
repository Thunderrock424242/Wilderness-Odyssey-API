package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterCompat;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterState;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds animated authority-water contact into vanilla entity decisions.
 *
 * <p>Fluid tags remain the normal compatibility mechanism. These hooks only
 * replace a result when the central once-per-tick cache observed a nearby
 * authority surface, so vanilla and third-party tagged fluids retain their
 * original swimming, eye-submersion, and breathing behavior.</p>
 */
@Mixin(Entity.class)
public abstract class EntityWaterParityMixin {

    /** Makes vanilla movement and swim-pose logic follow the animated surface. */
    @Inject(method = "isInWater", at = @At("RETURN"), cancellable = true)
    private void wildernessOdysseyApi$useAnimatedWaterContact(CallbackInfoReturnable<Boolean> callbackInfo) {
        EntityWaterState state = EntityWaterCompat.stateFor((Entity) (Object) this);
        if (state.authoritativeContactKnown()) {
            callbackInfo.setReturnValue(state.touchingWater());
        }
    }

    /** Makes vanilla water-tag eye checks follow the same animated eye sample. */
    @Inject(method = "isEyeInFluid", at = @At("RETURN"), cancellable = true)
    private void wildernessOdysseyApi$useAnimatedWaterEyeState(
            TagKey<Fluid> fluidTag,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (fluidTag != FluidTags.WATER) {
            return;
        }
        EntityWaterState state = EntityWaterCompat.stateFor((Entity) (Object) this);
        if (state.authoritativeContactKnown()) {
            callbackInfo.setReturnValue(state.eyesSubmerged());
        }
    }

    /**
     * Supplies NeoForge's breathing hook with the Wilderness water fluid type.
     *
     * <p>{@code CommonHooks.onLivingBreathe} reads this method directly rather
     * than the deprecated water tag check. Returning the actual namespaced type
     * preserves its normal drowning, riding, and fluid-effect contracts.</p>
     */
    @Inject(method = "getEyeInFluidType", at = @At("RETURN"), cancellable = true)
    private void wildernessOdysseyApi$useAnimatedEyeFluidType(
            CallbackInfoReturnable<FluidType> callbackInfo
    ) {
        EntityWaterState state = EntityWaterCompat.stateFor((Entity) (Object) this);
        if (!state.authoritativeContactKnown()) {
            return;
        }

        FluidType wildernessWater = WildernessFluidRegistry.WILDERNESS_WATER_TYPE.get();
        if (state.eyesSubmerged()) {
            callbackInfo.setReturnValue(wildernessWater);
        } else if (callbackInfo.getReturnValue() == wildernessWater) {
            callbackInfo.setReturnValue(NeoForgeMod.EMPTY_TYPE.value());
        }
    }
}
