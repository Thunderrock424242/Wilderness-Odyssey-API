package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Protects canonical volume when external code directly mutates its fluid projection.
 *
 * <p>NeoForge has no event covering every non-player {@link Level#setBlock}
 * call. The reconciler itself immediately exits unless the old or new block is
 * the standalone Wilderness liquid, keeping this otherwise global hook narrow.</p>
 */
@Mixin(Level.class)
public abstract class WaterProjectionMutationMixin {

    /**
     * Lets canonical authority own successful projection writes and their return value.
     *
     * <p>When authority commits, its recursive projection performs the actual
     * block write. Cancelling this outer call avoids writing twice and reports
     * success even though the target block is already present.</p>
     */
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void wildernessOdysseyApi$reconcileProjectedWater(
            BlockPos position,
            BlockState newState,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        WorldFluidMutationReconciler.MutationDecision decision =
                WorldFluidMutationReconciler.beforeSetBlock(
                        (Level) (Object) this,
                        position,
                        newState
                );
        if (decision.interceptsOriginal()) {
            callbackInfo.setReturnValue(decision.returnValue());
        }
    }
}
