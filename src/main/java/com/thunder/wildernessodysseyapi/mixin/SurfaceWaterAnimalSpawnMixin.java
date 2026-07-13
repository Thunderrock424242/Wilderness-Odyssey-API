package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.NaturalAquaticWaterCompatibility;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Allows vanilla surface-water fauna to spawn in generated Wilderness water.
 *
 * <p>The generic in-water spawn placement already uses {@code FluidTags.WATER};
 * only the final surface predicate uses an exact block identity check. This
 * redirect changes that single comparison without replacing vanilla biome,
 * height, light, density, or mob-cap rules.</p>
 */
@Mixin(WaterAnimal.class)
public abstract class SurfaceWaterAnimalSpawnMixin {

    @Redirect(
            method = "checkSurfaceWaterAnimalSpawnRules",
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
