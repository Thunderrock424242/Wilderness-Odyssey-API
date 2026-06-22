package com.thunder.wildernessodysseyapi.watersystem.water.wave;

/**
 * Defines a physically coherent Gerstner wave spectrum for one water-body type.
 *
 * <p>Each component is authored using amplitude and wavelength. Its angular
 * frequency is then derived from the finite-depth gravity-wave dispersion
 * relation {@code omega^2 = g * k * tanh(k * depth)}. That keeps short chop,
 * long swell, rivers, and ponds moving at believable relative speeds instead
 * of assigning unrelated animation rates by eye.</p>
 *
 * <p>This is a far-field surface model, not a full volume-fluid solver. Local
 * splashes and breaking shore water remain the responsibility of the existing
 * SPH system.</p>
 */
public final class GerstnerWaveProfile {

    static final float GRAVITY = 9.80665f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /** Number of overlapping wave components in this profile. */
    public final int waveCount;

    /** Per-component amplitude in blocks. */
    public final float[] amplitude;
    /** Per-component wavelength in blocks. */
    public final float[] wavelength;
    /** Per-component wave number in radians per block. */
    public final float[] waveNumber;
    /** Per-component angular frequency in radians per second. */
    public final float[] angularFrequency;
    /** Normalized travel direction along world X. */
    public final float[] dirX;
    /** Normalized travel direction along world Z. */
    public final float[] dirZ;
    /** Gerstner horizontal steepness from zero to one. */
    public final float[] steepness;
    /** Stable phase offset used to prevent components from cresting together. */
    public final float[] phaseOffset;

    /**
     * Legacy alias for {@link #waveNumber}. Kept for API compatibility with
     * integrations that previously read the old spatial-frequency array.
     */
    @Deprecated(forRemoval = false)
    public final float[] frequency;

    /**
     * Legacy alias for {@link #angularFrequency}. Values are now derived from
     * gravity and depth instead of being arbitrary phase speeds.
     */
    @Deprecated(forRemoval = false)
    public final float[] speed;

    /** Scales orbital velocity before it is applied to entities. */
    public final float entityPushStrength;
    /** Scales the visual vertical response applied to boats. */
    public final float boatBobStrength;

    private GerstnerWaveProfile(Builder builder) {
        this.waveCount = builder.waveCount;
        this.amplitude = builder.amplitude;
        this.wavelength = builder.wavelength;
        this.waveNumber = builder.waveNumber;
        this.angularFrequency = builder.angularFrequency;
        this.dirX = builder.dirX;
        this.dirZ = builder.dirZ;
        this.steepness = builder.steepness;
        this.phaseOffset = builder.phaseOffset;
        this.frequency = this.waveNumber;
        this.speed = this.angularFrequency;
        this.entityPushStrength = builder.entityPushStrength;
        this.boatBobStrength = builder.boatBobStrength;
    }

    // Samples all displacement, normal, and velocity data in one pass.

    /**
     * Evaluates every wave component at the supplied position and time.
     *
     * @param worldX world X coordinate in blocks
     * @param worldZ world Z coordinate in blocks
     * @param timeSeconds simulation time in seconds
     * @return the complete surface sample
     */
    public WaveSurfaceSample sampleAt(float worldX, float worldZ, float timeSeconds) {
        return sampleAt(worldX, worldZ, timeSeconds, waveCount);
    }

    /**
     * Evaluates at most {@code maxWaveTrains} components for quality scaling.
     *
     * @param worldX world X coordinate in blocks
     * @param worldZ world Z coordinate in blocks
     * @param timeSeconds simulation time in seconds
     * @param maxWaveTrains maximum number of components to evaluate
     * @return the complete surface sample
     */
    public WaveSurfaceSample sampleAt(float worldX, float worldZ, float timeSeconds, int maxWaveTrains) {
        int count = Math.max(0, Math.min(waveCount, maxWaveTrains));
        if (count == 0) {
            return WaveSurfaceSample.flat();
        }

        float displacementX = 0.0f;
        float height = 0.0f;
        float displacementZ = 0.0f;
        float velocityX = 0.0f;
        float velocityY = 0.0f;
        float velocityZ = 0.0f;

        // Tangents start as the flat X and Z axes. Their cross product gives
        // an analytic normal without noisy neighboring height samples.
        float tangentXX = 1.0f;
        float tangentXY = 0.0f;
        float tangentXZ = 0.0f;
        float tangentZX = 0.0f;
        float tangentZY = 0.0f;
        float tangentZZ = 1.0f;

        for (int i = 0; i < count; i++) {
            float directionX = dirX[i];
            float directionZ = dirZ[i];
            float k = waveNumber[i];
            float omega = angularFrequency[i];
            float waveAmplitude = amplitude[i];
            float horizontalScale = steepness[i] * waveAmplitude;
            float phase = k * (directionX * worldX + directionZ * worldZ)
                    - omega * timeSeconds
                    + phaseOffset[i];
            float sin = (float) Math.sin(phase);
            float cos = (float) Math.cos(phase);

            displacementX += horizontalScale * directionX * cos;
            height += waveAmplitude * sin;
            displacementZ += horizontalScale * directionZ * cos;

            // Time derivatives provide orbital water velocity for shared
            // rendering and entity-physics behavior.
            velocityX += horizontalScale * directionX * omega * sin;
            velocityY -= waveAmplitude * omega * cos;
            velocityZ += horizontalScale * directionZ * omega * sin;

            float horizontalDerivative = horizontalScale * k * sin;
            float verticalDerivative = waveAmplitude * k * cos;

            tangentXX -= horizontalDerivative * directionX * directionX;
            tangentXY += verticalDerivative * directionX;
            tangentXZ -= horizontalDerivative * directionX * directionZ;
            tangentZX -= horizontalDerivative * directionX * directionZ;
            tangentZY += verticalDerivative * directionZ;
            tangentZZ -= horizontalDerivative * directionZ * directionZ;
        }

        // tangentZ x tangentX points upward for an undisturbed XZ plane.
        float normalX = tangentZY * tangentXZ - tangentZZ * tangentXY;
        float normalY = tangentZZ * tangentXX - tangentZX * tangentXZ;
        float normalZ = tangentZX * tangentXY - tangentZY * tangentXX;
        float normalLengthSquared = normalX * normalX + normalY * normalY + normalZ * normalZ;
        if (normalLengthSquared <= 1.0e-8f) {
            normalX = 0.0f;
            normalY = 1.0f;
            normalZ = 0.0f;
        } else {
            float inverseLength = 1.0f / (float) Math.sqrt(normalLengthSquared);
            normalX *= inverseLength;
            normalY *= inverseLength;
            normalZ *= inverseLength;
        }

        return new WaveSurfaceSample(
                displacementX,
                height,
                displacementZ,
                normalX,
                normalY,
                normalZ,
                velocityX,
                velocityY,
                velocityZ
        );
    }

    /**
     * Returns only the vertical displacement for legacy callers.
     */
    public float getHeightAt(float worldX, float worldZ, float timeSeconds) {
        return sampleAt(worldX, worldZ, timeSeconds).height();
    }

    /**
     * Returns vertical displacement with a quality-dependent component cap.
     */
    public float getHeightAt(float worldX, float worldZ, float timeSeconds, int maxWaveTrains) {
        return sampleAt(worldX, worldZ, timeSeconds, maxWaveTrains).height();
    }

    /**
     * Returns the wave-driven horizontal entity velocity.
     */
    public float[] getPushAt(float worldX, float worldZ, float timeSeconds) {
        return getPushAt(worldX, worldZ, timeSeconds, waveCount);
    }

    /**
     * Returns the wave-driven horizontal entity velocity with a component cap.
     */
    public float[] getPushAt(float worldX, float worldZ, float timeSeconds, int maxWaveTrains) {
        WaveSurfaceSample sample = sampleAt(worldX, worldZ, timeSeconds, maxWaveTrains);
        return new float[]{
                sample.velocityX() * entityPushStrength,
                sample.velocityZ() * entityPushStrength
        };
    }

    // Profiles use coherent wavelength bands rather than arbitrary frequencies.

    /** Moderate ocean swell with wind-aligned secondary chop. */
    public static final GerstnerWaveProfile OCEAN = new Builder(4, 20.0f)
            .wave(0, 0.110f, 18.0f, 0.85f, 0.53f, 0.65f, 0.00f)
            .wave(1, 0.060f, 9.5f, 0.72f, 0.69f, 0.55f, 1.31f)
            .wave(2, 0.025f, 4.0f, 0.35f, -0.94f, 0.42f, 2.17f)
            .wave(3, 0.012f, 1.8f, -0.60f, -0.80f, 0.25f, 0.73f)
            .entityPush(0.010f)
            .boatBob(0.65f)
            .build();

    /** Directional river motion with small, depth-limited ripples. */
    public static final GerstnerWaveProfile RIVER = new Builder(3, 2.0f)
            .wave(0, 0.025f, 5.5f, 1.00f, 0.00f, 0.24f, 0.00f)
            .wave(1, 0.012f, 2.4f, 0.95f, 0.31f, 0.18f, 1.57f)
            .wave(2, 0.006f, 1.1f, 0.98f, -0.20f, 0.10f, 2.64f)
            .entityPush(0.004f)
            .boatBob(0.35f)
            .build();

    /** Subtle wind ripples for enclosed ponds. */
    public static final GerstnerWaveProfile POND = new Builder(2, 1.0f)
            .wave(0, 0.008f, 3.5f, 0.71f, 0.71f, 0.10f, 0.00f)
            .wave(1, 0.004f, 1.7f, -0.71f, 0.71f, 0.06f, 1.91f)
            .entityPush(0.0005f)
            .boatBob(0.12f)
            .build();

    /**
     * Builds immutable profiles while deriving their dispersion values.
     */
    public static final class Builder {
        final int waveCount;
        final float effectiveDepth;
        final float[] amplitude;
        final float[] wavelength;
        final float[] waveNumber;
        final float[] angularFrequency;
        final float[] dirX;
        final float[] dirZ;
        final float[] steepness;
        final float[] phaseOffset;
        float entityPushStrength = 0.01f;
        float boatBobStrength = 0.05f;

        Builder(int waveCount) {
            this(waveCount, 1_000.0f);
        }

        Builder(int waveCount, float effectiveDepth) {
            if (waveCount <= 0) {
                throw new IllegalArgumentException("waveCount must be positive");
            }
            if (effectiveDepth <= 0.0f) {
                throw new IllegalArgumentException("effectiveDepth must be positive");
            }

            this.waveCount = waveCount;
            this.effectiveDepth = effectiveDepth;
            this.amplitude = new float[waveCount];
            this.wavelength = new float[waveCount];
            this.waveNumber = new float[waveCount];
            this.angularFrequency = new float[waveCount];
            this.dirX = new float[waveCount];
            this.dirZ = new float[waveCount];
            this.steepness = new float[waveCount];
            this.phaseOffset = new float[waveCount];
        }

        Builder wave(int index, float waveAmplitude, float waveLength,
                     float directionX, float directionZ, float waveSteepness,
                     float stablePhaseOffset) {
            if (index < 0 || index >= waveCount) {
                throw new IndexOutOfBoundsException("wave index " + index + " is outside profile");
            }
            if (waveAmplitude < 0.0f || waveLength <= 0.0f) {
                throw new IllegalArgumentException("amplitude must be non-negative and wavelength must be positive");
            }

            float directionLength = (float) Math.sqrt(
                    directionX * directionX + directionZ * directionZ
            );
            if (directionLength <= 1.0e-6f) {
                throw new IllegalArgumentException("wave direction cannot be zero");
            }

            directionX /= directionLength;
            directionZ /= directionLength;
            float k = TWO_PI / waveLength;
            float omega = (float) Math.sqrt(GRAVITY * k * Math.tanh(k * effectiveDepth));

            amplitude[index] = waveAmplitude;
            wavelength[index] = waveLength;
            waveNumber[index] = k;
            angularFrequency[index] = omega;
            dirX[index] = directionX;
            dirZ[index] = directionZ;
            steepness[index] = Math.max(0.0f, Math.min(1.0f, waveSteepness));
            phaseOffset[index] = stablePhaseOffset;
            return this;
        }

        Builder entityPush(float value) {
            entityPushStrength = Math.max(0.0f, value);
            return this;
        }

        Builder boatBob(float value) {
            boatBobStrength = Math.max(0.0f, value);
            return this;
        }

        GerstnerWaveProfile build() {
            return new GerstnerWaveProfile(this);
        }
    }
}
