package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.datafixers.util.Either;
import com.thunder.wildernessodysseyapi.util.ChunkErrorReporter;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Captures global chunk generation pipeline failures and logs full diagnostics with suspected mod hints.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapGenerationErrorMixin {
    @Shadow @Final
    private ServerLevel level;

    @Inject(method = "applyStep", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$reportChunkGenerationFailures(GenerationChunkHolder holder,
                                                                    ChunkStep step,
                                                                    StaticCache2D<GenerationChunkHolder> cache,
                                                                    boolean loading,
                                                                    CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        ChunkPos chunkPos = holder.getPos();
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> wrapped = original.whenComplete((result, throwable) -> {
            if (throwable == null) {
                return;
            }
            ChunkErrorReporter.reportChunkError("generation/%s".formatted(step), this.level, chunkPos, throwable);
        });
        cir.setReturnValue(wrapped);
    }
}
