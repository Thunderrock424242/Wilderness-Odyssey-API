package com.thunder.wildernessodysseyapi.watersystem.water.wave;

/**
 * Describes the Gerstner surface at one horizontal world position.
 *
 * <p>The displacement and normal drive client rendering, while velocity gives
 * boats and floating entities the orbital motion from the same wave field.
 * Keeping those values together prevents visuals and water physics from using
 * subtly different equations.</p>
 *
 * @param displacementX horizontal displacement along world X, in blocks
 * @param height vertical displacement from the undisturbed surface, in blocks
 * @param displacementZ horizontal displacement along world Z, in blocks
 * @param normalX normalized surface normal X component
 * @param normalY normalized surface normal Y component
 * @param normalZ normalized surface normal Z component
 * @param velocityX horizontal orbital velocity along X, in blocks per second
 * @param velocityY vertical orbital velocity, in blocks per second
 * @param velocityZ horizontal orbital velocity along Z, in blocks per second
 */
public record WaveSurfaceSample(
        float displacementX,
        float height,
        float displacementZ,
        float normalX,
        float normalY,
        float normalZ,
        float velocityX,
        float velocityY,
        float velocityZ
) {
    private static final WaveSurfaceSample FLAT = new WaveSurfaceSample(
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f
    );

    /**
     * Returns an undisturbed, stationary water surface.
     */
    public static WaveSurfaceSample flat() {
        return FLAT;
    }

    /**
     * Scales displacement and velocity while preserving a valid unit normal.
     *
     * <p>This is used to fade waves out in shallow flowing-water blocks so a
     * large ocean swell cannot pull a thin water edge through nearby terrain.</p>
     *
     * @param factor blend factor from {@code 0} (flat) to {@code 1} (full wave)
     * @return a safely attenuated sample
     */
    public WaveSurfaceSample attenuated(float factor) {
        float clamped = Math.max(0.0f, Math.min(1.0f, factor));
        if (clamped <= 0.0f) {
            return flat();
        }
        if (clamped >= 1.0f) {
            return this;
        }

        float blendedNormalX = normalX * clamped;
        float blendedNormalY = 1.0f + (normalY - 1.0f) * clamped;
        float blendedNormalZ = normalZ * clamped;
        float inverseLength = inverseLength(blendedNormalX, blendedNormalY, blendedNormalZ);

        return new WaveSurfaceSample(
                displacementX * clamped,
                height * clamped,
                displacementZ * clamped,
                blendedNormalX * inverseLength,
                blendedNormalY * inverseLength,
                blendedNormalZ * inverseLength,
                velocityX * clamped,
                velocityY * clamped,
                velocityZ * clamped
        );
    }

    /**
     * Adds a uniform vertical offset, such as an ocean tide, without changing
     * the local surface normal or orbital velocity.
     */
    public WaveSurfaceSample withHeightOffset(float offset) {
        if (offset == 0.0f) {
            return this;
        }

        return new WaveSurfaceSample(
                displacementX,
                height + offset,
                displacementZ,
                normalX,
                normalY,
                normalZ,
                velocityX,
                velocityY,
                velocityZ
        );
    }

    private static float inverseLength(float x, float y, float z) {
        float lengthSquared = x * x + y * y + z * z;
        if (lengthSquared <= 1.0e-8f) {
            return 1.0f;
        }
        return 1.0f / (float) Math.sqrt(lengthSquared);
    }
}
