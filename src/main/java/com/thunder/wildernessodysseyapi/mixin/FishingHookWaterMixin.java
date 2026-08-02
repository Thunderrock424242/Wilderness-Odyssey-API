package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.VanillaWaterParity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restores fishing approach and splash particles over Wilderness water.
 *
 * <p>Open-water loot validation already uses {@code FluidTags.WATER} and source
 * checks in this Minecraft version. Only the two visual water comparisons in
 * {@code catchingFish} still use exact block identity.</p>
 */
@Mixin(FishingHook.class)
public abstract class FishingHookWaterMixin {

    @Redirect(
            method = "catchingFish",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "is(Lnet/minecraft/world/level/block/Block;)Z"
            ),
            require = 2
    )
    private boolean wildernessOdysseyApi$renderFishingEffectsOverWildernessWater(
            BlockState state,
            Block requestedBlock
    ) {
        return VanillaWaterParity.matchesFishingEffectWater(state, requestedBlock);
    }
}
