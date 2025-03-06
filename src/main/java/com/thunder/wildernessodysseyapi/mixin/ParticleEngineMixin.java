package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.HigherSmoke.Particles.CustomCampfireParticle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The type Particle engine mixin.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "register", at = @At("HEAD"), remap = true)
    private void modifyCampfireParticles(ParticleType<? extends ParticleOptions> type, ParticleProvider<? extends ParticleOptions> provider, CallbackInfo ci) {
        if (type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE) {
            ((ParticleEngine) (Object) this).register((SimpleParticleType) type, CustomCampfireParticle.Factory::new);
        }
    }
}

// this corresponds to the camp fire block mixin file.