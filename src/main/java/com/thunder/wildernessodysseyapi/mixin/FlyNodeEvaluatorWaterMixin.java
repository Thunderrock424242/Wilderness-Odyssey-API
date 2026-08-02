package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.VanillaWaterParity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fixes the exact-water start-height scan used by water-capable flying mobs. */
@Mixin(FlyNodeEvaluator.class)
public abstract class FlyNodeEvaluatorWaterMixin {

    @Redirect(
            method = "getStart",
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
