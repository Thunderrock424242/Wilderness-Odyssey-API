package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.NaturalAquaticWaterCompatibility;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.CoralFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Extends the base coral feature's exact water checks to standalone Wilderness water.
 *
 * <p>All vanilla coral tree, claw, and mushroom features share
 * {@code CoralFeature.placeCoralBlock}; targeting that one boundary avoids
 * duplicating hooks across each coral shape implementation.</p>
 */
@Mixin(CoralFeature.class)
public abstract class CoralFeatureWaterMixin {

    @Redirect(
            method = "placeCoralBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"
            )
    )
    private boolean wildernessOdyssey$acceptStandaloneWildernessWater(BlockState state, Block requestedBlock) {
        return NaturalAquaticWaterCompatibility.matchesRequestedBlock(state, requestedBlock);
    }
}
