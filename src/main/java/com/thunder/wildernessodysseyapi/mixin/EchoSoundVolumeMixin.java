package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.temporalrift.client.EchoClientEffects;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The sound event can replace/cancel an instance but cannot adjust its effective volume.
 * This narrow return hook preserves identity, user volume settings and modded streaming audio.
 */
@Mixin(SoundEngine.class)
public abstract class EchoSoundVolumeMixin {
    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void wildernessodysseyapi$echoVolume(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValueF() * EchoClientEffects.soundMultiplier(source));
    }
}
