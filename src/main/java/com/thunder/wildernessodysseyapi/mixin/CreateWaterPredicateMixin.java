package com.thunder.wildernessodysseyapi.mixin;

import com.simibubi.create.foundation.fluid.FluidHelper;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.create.CreateWaterCompatibilityState;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends only Create's local water predicate to namespaced Wilderness water.
 *
 * <p>This does not alter Minecraft or NeoForge fluid identity. Create uses an
 * exact {@code Fluids.WATER} comparison after normalizing flowing fluid, so a
 * focused adapter is required even though the custom fluid is correctly
 * present in {@code #minecraft:water}.</p>
 */
@Mixin(value = FluidHelper.class, remap = false)
public abstract class CreateWaterPredicateMixin {

    /** Recognizes the two Wilderness registry entries when Create compatibility is enabled. */
    @Inject(
            method = "isWater(Lnet/minecraft/world/level/material/Fluid;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void wildernessOdysseyApi$recognizeWildernessWater(
            Fluid fluid,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (CreateWaterCompatibilityState.isCreateWater(fluid)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
