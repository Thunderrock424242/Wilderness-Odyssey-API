package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes internal liquid faces between vanilla water and Wilderness water.
 *
 * <p>Vanilla's liquid renderer normally culls faces only when neighboring
 * fluids are the exact same registry fluid. The replacement ocean intentionally
 * uses a namespaced water fluid tagged as {@code #minecraft:water}; without
 * this bridge, mixed vanilla/Wilderness columns render tall translucent walls
 * inside oceans. A client-only mixin is required because this is a chunk-mesh
 * render decision, not a server fluid or block-state rule.</p>
 */
@Mixin(LiquidBlockRenderer.class)
public class TaggedWaterFaceCullingMixin {

    /**
     * Treat any two tagged water fluids as visually continuous for liquid face culling.
     */
    @Inject(
            method = "shouldRenderFace("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/FluidState;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/world/level/material/FluidState;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void wildernessOdysseyApi$hideTaggedWaterInternalFace(
            BlockAndTintGetter level,
            BlockPos pos,
            FluidState selfState,
            BlockState selfBlockState,
            Direction direction,
            FluidState otherState,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (selfState.is(FluidTags.WATER) && otherState.is(FluidTags.WATER)) {
            callbackInfo.setReturnValue(false);
        }
    }

    /**
     * Culls the NeoForge block-state overload used by liquid chunk meshing.
     */
    @Inject(
            method = "shouldRenderFace("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/FluidState;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void wildernessOdysseyApi$hideTaggedWaterInternalBlockFace(
            BlockAndTintGetter level,
            BlockPos pos,
            FluidState selfState,
            BlockState selfBlockState,
            Direction direction,
            BlockState otherState,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (selfState.is(FluidTags.WATER) && otherState.getFluidState().is(FluidTags.WATER)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
