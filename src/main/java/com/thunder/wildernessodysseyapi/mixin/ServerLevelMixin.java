package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.chunk.ChunkTickThrottler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$skipWarmCached(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        if (ChunkTickThrottler.shouldSkipWarmTicking(chunk.getPos())) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "tickChunk", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wildernessodysseyapi$scaleRandomTicks(int randomTickSpeed, LevelChunk chunk) {
        return ChunkTickThrottler.scaleRandomTickDensity((ServerLevel) (Object) this, chunk.getPos(), randomTickSpeed);
    }
}
