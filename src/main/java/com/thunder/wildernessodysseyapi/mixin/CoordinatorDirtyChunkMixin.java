package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.opc.saving.SavingCoordinator;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge has load/save events but no event for every repeated dirty mark.
 * This tail notification preserves the flag assignment and observes only
 * loaded server chunks when explicitly enabled. Save/C2ME presence vetoes the
 * module; the startup instrumentation property removes this mixin. No dirty
 * state is cleared here, and missing targets leave optional tracking absent.
 */
@Mixin(ChunkAccess.class)
public abstract class CoordinatorDirtyChunkMixin {
    @Inject(method = "setUnsaved(Z)V", at = @At("RETURN"), require = 0)
    private void wildernessodysseyapi$observeDirty(boolean dirty, CallbackInfo callback) {
        // This handler executes after Mixin merges it into ChunkAccess.
        //noinspection DataFlowIssue
        SavingCoordinator.dirtyStateChanged((ChunkAccess) (Object) this, dirty);
    }

    @Inject(method = {"setStartForStructure", "setAllStarts", "addReferenceForStructure", "setAllReferences"},
            at = @At("RETURN"), require = 0)
    private void wildernessodysseyapi$observeStructureDirty(CallbackInfo callback) {
        // This handler executes after Mixin merges it into ChunkAccess.
        //noinspection DataFlowIssue
        SavingCoordinator.dirtyStateChanged((ChunkAccess) (Object) this, true);
    }
}
