package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thunder.wildernessodysseyapi.diagnostics.performance.PerformanceDiagnostics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Observes synchronous getChunk waits; NeoForge has no wait-duration
 * event. Cache hits have no timing hook. Off-thread marshaling, managed-block and the NeoForge
 * currently-loading bypass all execute unchanged. C2ME/chunk schedulers can
 * overlap; wrappers compose and missing targets skip (require=0). Startup
 * wilderness.perf.instrumentation=false gates the mixin. OFF bypasses clocks.
 * Only strings/coordinates/timing reach diagnostics. Dimension/server identity
 * is captured during construction, never read from a live level by a worker.
 */
@Mixin(ServerChunkCache.class)
public abstract class PerformanceChunkWaitDiagnosticsMixin {
    @Shadow @Final private ServerLevel level;
    @Unique private String wildernessodysseyapi$dimension = "unknown";
    @Unique private MinecraftServer wildernessodysseyapi$server;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void wildernessodysseyapi$captureDiagnosticIdentity(CallbackInfo callback) {
        wildernessodysseyapi$dimension = level.dimension().location().toString();
        wildernessodysseyapi$server = level.getServer();
    }

    @WrapOperation(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"), require = 0)
    private void wildernessodysseyapi$observeServerWait(@Coerce Object processor, BooleanSupplier complete,
                                                        Operation<Void> original,
                                                        @Local(argsOnly = true, ordinal = 0) int x,
                                                        @Local(argsOnly = true, ordinal = 1) int z) {
        PerformanceDiagnostics.measureChunkRequest(wildernessodysseyapi$server, wildernessodysseyapi$dimension,
                x, z, () -> original.call(processor, complete));
    }

    // Verified ordinal 0 is the off-thread marshal/join branch in the 1.21.1
    // generated source and bytecode. The later completed-result join is untouched.
    @WrapOperation(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;", ordinal = 0), require = 0)
    private Object wildernessodysseyapi$observeCallerWait(CompletableFuture<?> future, Operation<Object> original,
                                                          @Local(argsOnly = true, ordinal = 0) int x,
                                                          @Local(argsOnly = true, ordinal = 1) int z) {
        return PerformanceDiagnostics.measureChunkRequest(wildernessodysseyapi$server, wildernessodysseyapi$dimension,
                x, z, () -> original.call(future));
    }
}
