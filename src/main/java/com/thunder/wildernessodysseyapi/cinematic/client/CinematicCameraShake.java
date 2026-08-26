package com.thunder.wildernessodysseyapi.cinematic.client;

import net.minecraft.util.Mth;

/** Reusable deterministic camera-shake curve with bounded intensity and selectable falloff. */
public final class CinematicCameraShake {
    private CinematicCameraShake() {
    }

    /**
     * Samples one angular shake axis.
     *
     * @param time continuous client game time
     * @param intensity maximum angular magnitude in degrees
     * @param progress normalized shake duration progress
     * @param falloff how intensity decays over the requested duration
     */
    public static float sample(float time, float intensity, float progress, Falloff falloff) {
        float boundedIntensity = Mth.clamp(intensity, 0.0F, 8.0F);
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        float envelope = switch (falloff) {
            case NONE -> 1.0F;
            case LINEAR -> 1.0F - t;
            case SMOOTH -> 1.0F - t * t * (3.0F - 2.0F * t);
        };
        float wave = (Mth.sin(time * 2.17F) + Mth.sin(time * 1.13F + 0.7F)) * 0.5F;
        return wave * boundedIntensity * envelope;
    }

    public enum Falloff {
        NONE,
        LINEAR,
        SMOOTH
    }
}
