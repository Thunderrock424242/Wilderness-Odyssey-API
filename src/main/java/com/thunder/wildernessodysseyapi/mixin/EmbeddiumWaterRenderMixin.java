package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Embeddium's fluid mesh continuous across Wilderness/vanilla water.
 *
 * <p>Embeddium replaces Minecraft's {@code LiquidBlockRenderer}, so the normal
 * tagged-water culling and fallback ownership mixins do not run on its chunk
 * compiler. Natural aquatic flora still hosts vanilla water inside a generated
 * Wilderness ocean. These narrow wrappers extend only Embeddium's visual fluid
 * identity checks; they do not change either fluid's gameplay identity.</p>
 *
 * <p>The pseudo target leaves clients without Embeddium untouched. Operation
 * wrappers compose with other Embeddium integrations instead of replacing
 * their renderer calls outright.</p>
 */
@Pseudo
@Mixin(
        targets = "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.FluidRenderer",
        remap = false
)
public abstract class EmbeddiumWaterRenderMixin {

    /** Treats adjacent tagged water as one volume for all six face decisions. */
    @WrapOperation(
            method = "isFluidOccluded("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "IIILnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/world/level/material/Fluid;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/Fluid;"
                            + "isSame(Lnet/minecraft/world/level/material/Fluid;)Z"
            ),
            remap = false,
            require = 1
    )
    private boolean wildernessOdysseyApi$cullTaggedWaterBoundary(
            Fluid neighbor,
            Fluid rendered,
            Operation<Boolean> original
    ) {
        return original.call(neighbor, rendered) || bothTaggedWater(neighbor, rendered);
    }

    /**
     * Keeps fallback surface heights level where flora-hosted vanilla water
     * touches generated Wilderness water.
     */
    @WrapOperation(
            method = "fluidHeight("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/world/level/material/Fluid;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction;)F",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/Fluid;"
                            + "isSame(Lnet/minecraft/world/level/material/Fluid;)Z"
            ),
            remap = false,
            require = 2
    )
    private boolean wildernessOdysseyApi$sampleTaggedWaterHeight(
            Fluid rendered,
            Fluid sampled,
            Operation<Boolean> original
    ) {
        return original.call(rendered, sampled) || bothTaggedWater(rendered, sampled);
    }

    /**
     * Suppresses Embeddium's baked top only after the replacement mesh has
     * atomically published ownership for the same surface cell.
     */
    @Inject(
            method = "isFluidOccluded("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "IIILnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/world/level/material/Fluid;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void wildernessOdysseyApi$hideOwnedFallbackTop(
            BlockAndTintGetter level,
            int x,
            int y,
            int z,
            Direction direction,
            Fluid rendered,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (direction == Direction.UP
                && rendered.defaultFluidState().is(FluidTags.WATER)
                && WaterRenderCoordinator.ownsBakedTop(new BlockPos(x, y, z))) {
            callbackInfo.setReturnValue(true);
        }
    }

    private static boolean bothTaggedWater(Fluid first, Fluid second) {
        return first.defaultFluidState().is(FluidTags.WATER)
                && second.defaultFluidState().is(FluidTags.WATER);
    }
}
