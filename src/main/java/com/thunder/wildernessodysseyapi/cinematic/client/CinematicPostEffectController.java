package com.thunder.wildernessodysseyapi.cinematic.client;

import net.minecraft.client.Minecraft;

/** Applies Minecraft's transient, accessibility-aware blur without taking ownership of other post effects. */
public final class CinematicPostEffectController {
    /** Processes the native UI blur for this frame when the presentation requests disorientation. */
    public void process(float partialTick, float strength) {
        Minecraft minecraft = Minecraft.getInstance();
        if (strength <= 0.01F || minecraft.options.screenEffectScale().get() <= 0.01D) {
            return;
        }
        int configuredRadius = minecraft.options.getMenuBackgroundBlurriness();
        int requestedRadius = Math.round(configuredRadius * Math.min(1.0F, strength));
        if (requestedRadius < 1) {
            return;
        }
        if (requestedRadius == configuredRadius) {
            minecraft.gameRenderer.processBlurEffect(partialTick);
            return;
        }

        // Minecraft's native blur reads this option when the pass is
        // processed. Restore it immediately so the cinematic neither persists
        // nor steals ownership of the player's menu-blur preference.
        minecraft.options.menuBackgroundBlurriness().set(requestedRadius);
        try {
            minecraft.gameRenderer.processBlurEffect(partialTick);
        } finally {
            minecraft.options.menuBackgroundBlurriness().set(configuredRadius);
        }
    }
}
