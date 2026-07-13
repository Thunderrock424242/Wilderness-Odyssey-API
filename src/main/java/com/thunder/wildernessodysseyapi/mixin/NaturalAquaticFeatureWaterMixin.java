package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.NaturalAquaticWaterCompatibility;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.KelpFeature;
import net.minecraft.world.level.levelgen.feature.SeaPickleFeature;
import net.minecraft.world.level.levelgen.feature.SeagrassFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets vanilla kelp, seagrass, and sea-pickle features recognize standalone
 * Wilderness water after the direct generation adapter has stored it.
 *
 * <p>A mixin is required because these vanilla features use exact
 * {@code Blocks.WATER} identity checks and expose no feature predicate hook.
 * Non-water identity checks retain their original behavior.</p>
 */
@Mixin({KelpFeature.class, SeagrassFeature.class, SeaPickleFeature.class})
public abstract class NaturalAquaticFeatureWaterMixin {

    @Redirect(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"
            )
    )
    private boolean wildernessOdyssey$acceptStandaloneWildernessWater(BlockState state, Block requestedBlock) {
        return NaturalAquaticWaterCompatibility.matchesRequestedBlock(state, requestedBlock);
    }
}
