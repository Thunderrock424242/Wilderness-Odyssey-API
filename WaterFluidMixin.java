package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
public class WaterFluidMixin {

    @Inject(method = "animateTick", at = @At("HEAD"))
    public void onanimateTick(Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        WaterSystem.applyWaveForces(level);
    }
}


// this corresponds to the ocean package.