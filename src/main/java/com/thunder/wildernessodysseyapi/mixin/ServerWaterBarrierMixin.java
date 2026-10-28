package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WaterBarrierUpdates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes successful runtime terrain writes absent from NeoForge player events.
 * Admission excludes clients, generation/provisional writes and water projection
 * feedback; actual flow remains owned by the canonical server ticker.
 */
@Mixin(LevelChunk.class)
public abstract class ServerWaterBarrierMixin {

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Z)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void wildernessOdysseyApi$wakeWaterAfterBarrierChange(
            BlockPos position, BlockState newState, boolean moved,
            CallbackInfoReturnable<BlockState> callbackInfo
    ) {
        // Mixin merges this handler into the target chunk before this cast.
        //noinspection DataFlowIssue
        WaterBarrierUpdates.onBlockChanged((LevelChunk) (Object) this, position,
                callbackInfo.getReturnValue(), newState);
    }
}
