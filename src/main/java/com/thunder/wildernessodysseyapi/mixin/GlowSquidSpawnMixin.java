package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.NaturalAquaticWaterCompatibility;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends glow squid's dark-water block identity check to Wilderness water. */
@Mixin(GlowSquid.class)
public abstract class GlowSquidSpawnMixin {

    @Redirect(
            method = "checkGlowSquidSpawnRules",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"
            )
    )
    private static boolean wildernessOdyssey$acceptStandaloneWildernessWater(
            BlockState state,
            Block requestedBlock
    ) {
        return NaturalAquaticWaterCompatibility.matchesRequestedBlock(state, requestedBlock);
    }
}
