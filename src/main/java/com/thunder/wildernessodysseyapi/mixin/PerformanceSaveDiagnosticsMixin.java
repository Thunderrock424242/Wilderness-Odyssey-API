package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.diagnostics.performance.PerformanceDiagnostics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Read-only save/stop timing: NeoForge events cannot bracket native final-save
 * and flush durations. Original operations execute once, including serialization,
 * events, attachments and errors. Save mods may overlap these targets; wrappers
 * compose, missing targets skip (require=0), and no method is canceled. Startup
 * wilderness.perf.instrumentation=false removes this mixin; observation OFF
 * bypasses timing at runtime. No save data crosses a worker boundary.
 */
@Mixin(MinecraftServer.class)
public abstract class PerformanceSaveDiagnosticsMixin {
    @WrapMethod(method = "saveEverything(ZZZ)Z", require = 0)
    private boolean wildernessodysseyapi$observeSaveEverything(boolean suppressLog, boolean flush, boolean forced,
                                                               Operation<Boolean> original) {
        return PerformanceDiagnostics.measure(this, "native/saveEverything", () -> original.call(suppressLog, flush, forced));
    }

    @WrapMethod(method = "saveAllChunks(ZZZ)Z", require = 0)
    private boolean wildernessodysseyapi$observeSaveAllChunks(boolean suppressLog, boolean flush, boolean forced,
                                                              Operation<Boolean> original) {
        return PerformanceDiagnostics.measure(this, "native/saveAllChunks", () -> original.call(suppressLog, flush, forced));
    }

    @WrapMethod(method = "stopServer()V", require = 0)
    private void wildernessodysseyapi$observeNativeStop(Operation<Void> original) {
        PerformanceDiagnostics.measure(this, "native/stopServer", () -> original.call());
    }

    @WrapOperation(method = "saveAllChunks(ZZZ)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;save(Lnet/minecraft/util/ProgressListener;ZZ)V"), require = 0)
    private void wildernessodysseyapi$observeDimensionSave(ServerLevel level, ProgressListener progress,
                                                           boolean flush, boolean skipSave, Operation<Void> original) {
        PerformanceDiagnostics.measure(this, "native/dimension/" + level.dimension().location(),
                () -> original.call(level, progress, flush, skipSave));
    }
}
