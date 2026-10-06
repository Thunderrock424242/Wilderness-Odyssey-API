package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only loading percentage access. NeoForge's screen events expose the
 * screen but no public getter for its existing spawn-progress listener.
 */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
    @Accessor("progressListener")
    StoringChunkProgressListener wildernessOdysseyApi$getProgressListener();
}
