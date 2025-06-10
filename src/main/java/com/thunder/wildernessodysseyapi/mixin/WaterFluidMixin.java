package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * By mixing into BlockBehaviour.entityInside(...), we catch every block that wants to
 * apply “entity‐in‐block” behavior.  Then we simply filter for “is this water?”
 * and, if so, apply our wave‐force logic.
 */
@Mixin(BlockBehaviour.class)
public abstract class WaterFluidMixin {
    @Inject(
            method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD")
    )
    private void onEntityInside(
            BlockState state,
            Level world,
            BlockPos pos,
            Entity entity,
            CallbackInfo ci
    ) {
        if (state.getBlock() instanceof LiquidBlock
                && state.getFluidState().getType() == Fluids.WATER
                && entity instanceof Boat boat) {
            WaterSystem.applyWaveForces(boat);
        }
    }
}