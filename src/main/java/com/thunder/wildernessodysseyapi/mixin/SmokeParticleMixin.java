package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BaseAshSmokeParticle.class)
public abstract class SmokeParticleMixin extends TextureSheetParticle {

    protected SmokeParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    /**
     * Modify the `tick` method to allow natural upward movement
     * and despawn particles at the max build height.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void modifyTickBehavior(CallbackInfo ci) {
        // Increase upward speed for a natural rise
        if (this.yd < 0.05) {
            this.yd += 0.005; // Gradually accelerate upwards
        }

        // Gradually fade out near the maximum build height
        int maxBuildHeight = this.level.getMaxBuildHeight();
        if (this.y >= maxBuildHeight - 5) {
            this.alpha -= 0.02F; // Reduce transparency
        }

        // Remove particle when it fully fades or reaches max build height
        if (this.alpha <= 0 || this.y >= maxBuildHeight) {
            this.remove();
            ci.cancel(); // Skip the original tick logic
        }
    }
}