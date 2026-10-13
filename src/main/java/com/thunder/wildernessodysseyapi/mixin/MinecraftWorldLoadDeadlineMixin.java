package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thunder.wildernessodysseyapi.loading.client.LoadingStallDetector;
import com.thunder.wildernessodysseyapi.loading.client.WorldLoadFailureHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen events cannot return from Minecraft's synchronous integrated-server
 * startup loops. Cancel at the progress-listener wait or the ready check,
 * before opening a local connection. This targets 1.21.1's two startup waits;
 * generation, progress listeners and the normal success path remain native.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftWorldLoadDeadlineMixin {
    @Shadow private boolean isLocalServer;

    @WrapMethod(method = "doWorldLoad")
    private void wildernessodysseyapi$guardWorldStartup(LevelStorageSource.LevelStorageAccess storage,
                                                        PackRepository packs, WorldStem stem, boolean newWorld,
                                                        Operation<Void> original) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (WorldLoadFailureHandler.isWaitingForShutdown() || LoadingStallDetector.worldLoadTimedOut()) {
            // Ownership has not transferred to an integrated server yet.
            stem.close();
            storage.safeClose();
            LoadingStallDetector.finishTimedOutLoad();
            minecraft.setScreen(WorldLoadFailureHandler.screen(WorldLoadFailureHandler.isWaitingForShutdown()));
            return;
        }
        WorldLoadFailureHandler.setStartingServer(true);
        try {
            LoadingStallDetector.beginWorldLoad();
            original.call(storage, packs, stem, newWorld);
        } finally {
            WorldLoadFailureHandler.setStartingServer(false);
        }
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"), cancellable = true)
    private void wildernessodysseyapi$abortBeforeProgressListener(LevelStorageSource.LevelStorageAccess storage,
                                                                  PackRepository packs, WorldStem stem, boolean newWorld,
                                                                  CallbackInfo ci) {
        if (LoadingStallDetector.worldLoadTimedOut()) {
            wildernessodysseyapi$abortStartup(false);
            ci.cancel();
        }
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isReady()Z"), cancellable = true)
    private void wildernessodysseyapi$abortSpawnWait(LevelStorageSource.LevelStorageAccess storage,
                                                     PackRepository packs, WorldStem stem, boolean newWorld,
                                                     CallbackInfo ci) {
        if (LoadingStallDetector.worldLoadTimedOut()) {
            wildernessodysseyapi$abortStartup(true);
            ci.cancel();
        }
    }

    @Unique
    private void wildernessodysseyapi$abortStartup(boolean profilerPushed) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (profilerPushed) {
            minecraft.getProfiler().pop();
        }
        WorldLoadFailureHandler.fail(minecraft);
        // Vanilla disconnect only resets this flag when a client level exists.
        isLocalServer = false;
    }
}
