package com.thunder.wildernessodysseyapi.watersystem.water.wave;

/**
 * Immutable environmental modifiers applied to a Gerstner wave spectrum.
 *
 * <p>The base profile still owns physically meaningful wavelength and finite-
 * depth dispersion. This state changes total energy and the relative weight of
 * fixed directional carriers without assigning arbitrary animation speeds or
 * rotating an existing crest field. Server weather can therefore drive the
 * same phase-stable model used by rendering and entity physics.</p>
 */
public record WaveSpectrumState(
        float swellScale,
        float chopScale,
        float windDirectionX,
        float windDirectionZ,
        float directionBlend
) {

    /** Unmodified profile used by rivers, ponds, and compatibility callers. */
    public static final WaveSpectrumState NEUTRAL =
            new WaveSpectrumState(1.0f, 1.0f, 1.0f, 0.0f, 0.0f);

    public WaveSpectrumState {
        swellScale = finiteClamp(swellScale, 0.0f, 3.0f, 1.0f);
        chopScale = finiteClamp(chopScale, 0.0f, 4.0f, 1.0f);
        directionBlend = finiteClamp(directionBlend, 0.0f, 1.0f, 0.0f);

        float lengthSquared = windDirectionX * windDirectionX + windDirectionZ * windDirectionZ;
        if (!Float.isFinite(lengthSquared) || lengthSquared <= 1.0e-8f) {
            windDirectionX = 1.0f;
            windDirectionZ = 0.0f;
        } else {
            float inverseLength = 1.0f / (float) Math.sqrt(lengthSquared);
            windDirectionX *= inverseLength;
            windDirectionZ *= inverseLength;
        }
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
