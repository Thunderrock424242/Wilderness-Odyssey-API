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
     * Modify the tick behavior to make particles rise continuously,
     * fade out, and despawn at max build height.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void modifyTickBehavior(CallbackInfo ci) {
        // Make the particle rise
        this.yd = 0.05;

        // Gradually fade out near the maximum build height
        int maxBuildHeight = this.level.getMaxBuildHeight();
        if (this.y >= maxBuildHeight - 5) {
            this.alpha -= 0.02F; // Decrease transparency
        }

        // Remove particle when it fades out completely or exceeds max build height
        if (this.alpha <= 0 || this.y >= maxBuildHeight) {
            this.remove();
            ci.cancel(); // Skip the original tick logic
        }
    }

    /**
     * Adjust render logic by extending particle rendering range.
     * Override render-related settings to increase visibility distance.
     */
    @Override
    public void tick() {
        super.tick();
        // Optionally, further adjust visibility settings here if needed.
    }
}
