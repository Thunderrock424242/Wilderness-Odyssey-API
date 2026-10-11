package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thunder.wildernessodysseyapi.saving.coordinator.NativeChunkSaveAccess;
import com.thunder.wildernessodysseyapi.saving.coordinator.SavingCoordinator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

/**
 * Supported events cannot request one normal chunk save or bracket its write
 * future. The invokers expose existing methods only; optional drains preserve
 * ChunkSerializer, NeoForge save events, POI flush and IOWorker ordering.
 * Native/third-party calls execute once and retain their original future.
 * Save/C2ME candidates veto drain; startup instrumentation=false removes this
 * mixin. If the write hook is replaced, the caller re-marks and disables drain.
 */
@Mixin(ChunkMap.class)
public abstract class CoordinatorChunkSaveMixin implements NativeChunkSaveAccess {
    @Override @Invoker("save")
    public abstract boolean wildernessodysseyapi$saveChunk(ChunkAccess chunk);

    @Override @Invoker("getVisibleChunkIfPresent")
    public abstract ChunkHolder wildernessodysseyapi$visibleChunk(long position);

    @WrapOperation(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"), require = 0)
    private CompletableFuture<Void> wildernessodysseyapi$observeOptionalWrite(ChunkMap owner, ChunkPos position,
                                                                             CompoundTag data, Operation<CompletableFuture<Void>> original) {
        CompletableFuture<Void> result = original.call(owner, position, data);
        SavingCoordinator.observeWrite(owner, position.toLong(), result);
        return result;
    }
}
