package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.loading.WorldLoadCancellation;
import com.thunder.wildernessodysseyapi.loading.WorldLoadTimeoutException;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IntegratedServer.halt(false) still waits on executeBlocking, so it cannot
 * cancel blocked startup. This mirrors the base halt's volatile stop request
 * and unwinds spawn preparation at a server-owned wait boundary. Native
 * runServer finally/stopServer still save, flush, close resources and unlock
 * storage. No chunks are touched off-thread and final saving is never aborted.
 */
@Mixin(MinecraftServer.class)
public abstract class WorldLoadServerCancellationMixin implements WorldLoadCancellation {
    @Shadow private volatile boolean running;
    @Shadow private boolean stopped;
    @Unique private volatile boolean wildernessodysseyapi$worldLoadCancelled;
    @Unique private boolean wildernessodysseyapi$abortIssued;

    @Override
    public void wildernessodysseyapi$cancelWorldLoad() {
        wildernessodysseyapi$worldLoadCancelled = true;
        running = false;
    }

    @Inject(method = "waitUntilNextTick", at = @At("HEAD"))
    private void wildernessodysseyapi$abortLoadingAtServerWait(CallbackInfo ci) {
        if (wildernessodysseyapi$worldLoadCancelled && !wildernessodysseyapi$abortIssued && !stopped) {
            wildernessodysseyapi$abortIssued = true;
            throw new WorldLoadTimeoutException();
        }
    }
}
