package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Sodium's fluid heights and baked-top ownership aligned with Wilderness water.
 *
 * <p>Sodium replaces Minecraft's {@code LiquidBlockRenderer}, so the vanilla
 * tagged-water culling and fallback-ownership mixins do not run in its chunk
 * compiler. This bridge extends Sodium's internal visual comparisons only; it
 * does not change either fluid's gameplay identity.</p>
 *
 * <p>The pseudo target and mixin-config plugin keep Sodium optional. Exact
 * method descriptors and required injection counts intentionally fail fast when
 * a future Sodium release changes the internal renderer contract.</p>
 */
@Pseudo
@Mixin(
        targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer",
        remap = false
)
public abstract class SodiumFluidRenderMixin {

    /**
     * Keeps fallback surface heights level where tagged vanilla water, such as
     * water inside aquatic flora, touches generated Wilderness water.
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
            // Sodium 0.8.12 performs exactly two identity checks here: the
            // sampled cell and the cell above it.
            require = 2
    )
    private boolean wildernessOdysseyApi$sampleTaggedWaterHeight(
            Fluid rendered,
            Fluid sampled,
            Operation<Boolean> original
    ) {
        WaterRenderCoordinator.recordExternalRendererBridgeUse();
        return original.call(rendered, sampled) || bothTaggedWater(rendered, sampled);
    }

    /**
     * Suppresses Sodium's baked top only after the replacement mesh has
     * atomically published ownership for the same surface cell.
     */
    @Inject(
            method = "isFullBlockFluidOccluded("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/material/FluidState;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void wildernessOdysseyApi$hideOwnedFallbackTop(
            BlockAndTintGetter level,
            BlockPos pos,
            Direction direction,
            BlockState blockState,
            FluidState rendered,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        WaterRenderCoordinator.recordExternalRendererBridgeUse();
        if (direction == Direction.UP
                && rendered.is(FluidTags.WATER)
                && WaterRenderCoordinator.ownsBakedTop(pos)) {
            callbackInfo.setReturnValue(true);
        }
    }

    private static boolean bothTaggedWater(Fluid first, Fluid second) {
        return first.defaultFluidState().is(FluidTags.WATER)
                && second.defaultFluidState().is(FluidTags.WATER);
    }
}
