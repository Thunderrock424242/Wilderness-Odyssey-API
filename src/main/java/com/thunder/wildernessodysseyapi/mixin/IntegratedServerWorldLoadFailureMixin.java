package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.loading.WorldLoadTimeoutException;
import net.minecraft.CrashReport;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A timed-out world has its own failure screen; other server crashes retain vanilla behavior. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerWorldLoadFailureMixin {
    @Inject(method = "onServerCrash", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$keepClientOpenForWorldTimeout(CrashReport report, CallbackInfo ci) {
        if (report.getException() instanceof WorldLoadTimeoutException) {
            // The server already logged/saved its crash report and still executes
            // native finally cleanup. Only the delayed whole-client crash is skipped.
            ci.cancel();
        }
    }
}
