package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Extends Sodium's full-block fluid occlusion check across tagged water fluids.
 *
 * <p>Sodium 0.8 compares the neighboring {@link FluidState} by identity inside
 * its occlusion cache. Returning the rendered state for a tagged-water neighbor
 * makes that one visual comparison succeed without globally impersonating
 * vanilla water or changing world state.</p>
 */
@Pseudo
@Mixin(
        targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache",
        remap = false
)
public abstract class SodiumBlockOcclusionCacheMixin {

    /** Treats adjacent tagged water as one visual volume for face decisions. */
    @WrapOperation(
            method = "shouldDrawFullBlockFluidSide("
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/BlockGetter;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/world/level/material/FluidState;"
                    + "Lnet/minecraft/world/phys/shapes/VoxelShape;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "getFluidState()Lnet/minecraft/world/level/material/FluidState;"
            ),
            remap = false,
            // The supported Sodium build reads the neighboring fluid once.
            require = 1
    )
    private FluidState wildernessOdysseyApi$cullTaggedWaterBoundary(
            BlockState neighborBlockState,
            Operation<FluidState> original,
            BlockState selfBlockState,
            BlockGetter level,
            BlockPos selfPos,
            Direction direction,
            FluidState rendered,
            VoxelShape fluidShape
    ) {
        FluidState neighbor = original.call(neighborBlockState);
        WaterRenderCoordinator.recordExternalRendererBridgeUse();
        if (neighbor.is(FluidTags.WATER) && rendered.is(FluidTags.WATER)) {
            return rendered;
        }
        return neighbor;
    }
}
