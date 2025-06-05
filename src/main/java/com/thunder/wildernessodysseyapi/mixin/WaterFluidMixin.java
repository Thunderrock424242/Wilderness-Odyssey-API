package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In 1.21.1, FluidState has a method:
 *     public void onEntityCollision(Level, BlockPos, Entity)
 * whenever an entity is inside (colliding with) a fluid.
 * We hook into that and call our wave logic.
 */
@Mixin(FluidState.class)
public class WaterFluidMixin {

    /**
     * Inject into FluidState.onEntityCollision(Level, BlockPos, Entity).
     * Signature: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V
     */
    @Inject(
            method = "onEntityCollision(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD")
    )
    private void onEntityCollision(Level world, BlockPos pos, Entity entity, CallbackInfo ci) {
        WaterSystem.applyWaveForces(entity);
    }
}
