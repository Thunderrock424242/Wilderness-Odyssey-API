package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Invalidates snapshot water topology after a physical client block changes.
 *
 * <p>Generated-water attachments are intentionally immutable, while client
 * packet application mutates the live chunk. NeoForge has no event covering
 * every client-side chunk write, so this narrow post-write hook lets the next
 * bounded mesh rebuild verify whether the synchronized surface is still real
 * water and still exposed.</p>
 */
@Mixin(LevelChunk.class)
public abstract class ClientLevelChunkWaterMeshMixin {

    /** Requeues only successful state changes near a synchronized water top. */
    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Z)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void wildernessOdysseyApi$invalidatePhysicalWaterSurface(
            BlockPos position,
            BlockState newState,
            boolean moved,
            CallbackInfoReturnable<BlockState> callbackInfo
    ) {
        BlockState previousState = callbackInfo.getReturnValue();
        if (previousState == null || previousState == newState) {
            return;
        }
        LevelChunk chunk = (LevelChunk) (Object) this;
        ClientWaterSnapshotStore.notifyBlockChange(chunk.getLevel(), position);
    }
}
