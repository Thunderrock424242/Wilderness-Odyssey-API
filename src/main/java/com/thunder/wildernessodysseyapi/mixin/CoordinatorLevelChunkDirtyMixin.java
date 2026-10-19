package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.opc.saving.SavingCoordinator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla setBlockState writes the inherited dirty field directly. Observe that
 * exact write, preserving block callbacks and avoiding notices for no-op changes.
 * No NeoForge repeated-dirty event exists. Runtime/startup and save-owner gates
 * match CoordinatorDirtyChunkMixin; missing targets preserve normal mutation.
 */
@Mixin(LevelChunk.class)
public abstract class CoordinatorLevelChunkDirtyMixin {
    @Inject(method = "setBlockState", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;unsaved:Z", opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER), require = 0)
    private void wildernessodysseyapi$observeBlockDirty(CallbackInfoReturnable<BlockState> callback) {
        // Mixin merges this handler into LevelChunk before it can execute.
        //noinspection DataFlowIssue
        SavingCoordinator.dirtyStateChanged((LevelChunk) (Object) this, true);
    }
}
