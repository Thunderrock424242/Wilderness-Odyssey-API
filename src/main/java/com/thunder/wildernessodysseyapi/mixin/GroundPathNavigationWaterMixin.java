package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.VanillaWaterParity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends the floating navigation surface scan to standalone Wilderness water. */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationWaterMixin {

    @Redirect(
            method = "getSurfaceY",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "is(Lnet/minecraft/world/level/block/Block;)Z"
            )
    )
    private boolean wildernessOdysseyApi$recognizeWildernessWater(
            BlockState state,
            Block requestedBlock
    ) {
        return VanillaWaterParity.matchesRequestedWaterBlock(state, requestedBlock);
    }
}
