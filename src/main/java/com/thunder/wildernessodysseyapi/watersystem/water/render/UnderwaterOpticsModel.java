package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Converts physical water conditions into bounded underwater optical values.
 *
 * <p>The model is deliberately independent from Minecraft rendering classes so
 * its Beer-Lambert-style attenuation, visibility, and transition behavior can
 * be unit tested. Client event handlers provide biome tint, depth, local flow,
 * daylight, and synchronized sea-state inputs.</p>
 */
public final class UnderwaterOpticsModel {

    private UnderwaterOpticsModel() {
    }

    /**
     * Evaluates fog, visibility, caustics, and distortion for one camera sample.
     * All inputs are sanitized so malformed config or network values cannot
     * propagate NaN values into shader uniforms.
     */
    public static OpticalProperties evaluate(
            float depthBelowSurface,
            float waterColumnDepth,
            float disturbance,
            float daylight,
            float tintRed,
            float tintGreen,
            float tintBlue,
            float seaState,
            float maximumVisibility,
            float turbidityStrength
    ) {
        float depth = clamp(finiteOrZero(depthBelowSurface), -0.08f, 96.0f);
        float columnDepth = clamp(finiteOrZero(waterColumnDepth), 0.0f, 64.0f);
        float movement = clamp(finiteOrZero(disturbance), 0.0f, 1.0f);
        float light = clamp(finiteOrZero(daylight), 0.0f, 1.0f);
        float storm = clamp(finiteOrZero(seaState), 0.0f, 1.0f);
        float visibilityLimit = clamp(finiteOrZero(maximumVisibility), 8.0f, 128.0f);
        float turbidityScale = clamp(finiteOrZero(turbidityStrength), 0.0f, 2.0f);

        // Shallow moving water carries more suspended material. Storm energy
        // adds turbidity without changing the canonical water volume itself.
        float shallowSediment = 1.0f - smoothStep(1.5f, 8.0f, columnDepth);
        float turbidity = (0.08f + shallowSediment * 0.16f + movement * 0.28f + storm * 0.18f)
                * turbidityScale;
        float clarity = clamp(1.0f - turbidity, 0.20f, 1.0f);
        float positiveDepth = Math.max(0.0f, depth);
        float attenuationDepth = positiveDepth / Math.max(0.20f, clarity);

        float tintR = clamp(finiteOrZero(tintRed), 0.0f, 1.0f);
        float tintG = clamp(finiteOrZero(tintGreen), 0.0f, 1.0f);
        float tintB = clamp(finiteOrZero(tintBlue), 0.0f, 1.0f);
        // Preserve a readable ambient floor underwater. Direct daylight still
        // drives caustics, but terrain no longer collapses toward black when
        // vanilla sky light is briefly low under a wave or overhang.
        float lightScale = 0.42f + light * 0.58f;

        // Red wavelengths attenuate first, followed by green and then blue.
        // The biome tint remains recognizable while depth approaches a cool,
        // low-luminance ocean color rather than an opaque blue wall.
        float red = (0.025f + tintR * 0.36f)
                * exponentialAttenuation(0.070f, attenuationDepth) * lightScale;
        float green = (0.080f + tintG * 0.48f)
                * exponentialAttenuation(0.029f, attenuationDepth) * lightScale;
        float blue = (0.155f + tintB * 0.56f)
                * exponentialAttenuation(0.014f, attenuationDepth) * lightScale;

        float visibility = visibilityLimit * clarity
                * exponentialAttenuation(0.018f, positiveDepth)
                * (0.88f + light * 0.12f);
        visibility = clamp(visibility, 6.0f, visibilityLimit);
        float immersionBlend = smoothStep(-0.04f, 0.18f, depth);
        float causticStrength = light * clarity
                * exponentialAttenuation(0.10f, positiveDepth)
                * (1.0f - storm * 0.22f);
        float distortionStrength = (0.0025f + storm * 0.0040f + movement * 0.0025f)
                * immersionBlend;

        return new OpticalProperties(
                clamp(red, 0.0f, 1.0f),
                clamp(green, 0.0f, 1.0f),
                clamp(blue, 0.0f, 1.0f),
                clarity,
                visibility,
                immersionBlend,
                clamp(causticStrength, 0.0f, 1.0f),
                clamp(distortionStrength, 0.0f, 0.012f)
        );
    }

    private static float exponentialAttenuation(float coefficient, float distance) {
        return (float) Math.exp(-coefficient * distance);
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Immutable, shader-safe result of the underwater optical model. */
    public record OpticalProperties(
            float fogRed,
            float fogGreen,
            float fogBlue,
            float clarity,
            float visibilityBlocks,
            float immersionBlend,
            float causticStrength,
            float distortionStrength
    ) {
    }
}
