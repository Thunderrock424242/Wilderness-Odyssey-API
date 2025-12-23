package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.chunk.ChunkTickThrottler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow @Final
    private ChunkPos chunkPos;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$skipWarmCached(ServerLevel level, int randomTickSpeed, CallbackInfo ci) {
        if (ChunkTickThrottler.shouldSkipWarmTicking(this.chunkPos)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "tick", at = @At("HEAD"), argsOnly = true)
    private int wildernessodysseyapi$scaleRandomTicks(int randomTickSpeed, ServerLevel level) {
        return ChunkTickThrottler.scaleRandomTickDensity(level, this.chunkPos, randomTickSpeed);
    }
}
