package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class DebugMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void logMethods(CallbackInfo ci) {
        for (var method : ParticleEngine.class.getDeclaredMethods()) {
            System.out.println(method);
        }
    }
}
