package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
public class WaterFluidMixin {

    @Inject(method = "entityInside", at = @At("HEAD"))
    public void onEntityInside(FluidState state, Entity entity, CallbackInfo ci) {
        WaterSystem.applyWaveForces(entity);
    }
}


// this corresponds to the ocean package.