package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.VanillaWaterParity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fixes the remaining exact-water ascent check for floating ground mobs. */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorWaterMixin {

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
