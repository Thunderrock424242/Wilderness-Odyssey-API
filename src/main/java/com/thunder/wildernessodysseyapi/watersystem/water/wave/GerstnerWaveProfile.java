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
    private static final double TWO_PI_DOUBLE = Math.PI * 2.0;
    private static final float MIN_DIRECTIONAL_ENERGY = 0.55f;
    private static final float DIRECTIONAL_ENERGY_RANGE = 0.90f;

    /**
     * Upper bound for the summed Gerstner horizontal derivative. Keeping the
     * value below one prevents the horizontal surface mapping from folding
     * over itself under unusually energetic weather spectra.
     */
    public static final float MAX_COMBINED_STEEPNESS = 0.82f;

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
        return sampleAt((double) worldX, worldZ, timeSeconds, waveCount);
    }

    /**
     * Double-coordinate variant that retains sub-block phase precision near the
     * Minecraft world border.
     */
    public WaveSurfaceSample sampleAt(double worldX, double worldZ, double timeSeconds) {
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
        return sampleAt((double) worldX, worldZ, timeSeconds, maxWaveTrains, WaveSpectrumState.NEUTRAL);
    }

    /** Double-coordinate quality-scaled variant for authoritative world queries. */
    public WaveSurfaceSample sampleAt(
            double worldX,
            double worldZ,
            double timeSeconds,
            int maxWaveTrains
    ) {
        return sampleAt(worldX, worldZ, timeSeconds, maxWaveTrains, WaveSpectrumState.NEUTRAL);
    }

    /**
     * Evaluates the profile with synchronized environmental energy and wind.
     * Wavelength-based dispersion and carrier headings remain unchanged. Wind
     * shifts energy between the authored directional trains, so weather can
     * reshape the sea without relocating every crest when its heading changes.
     */
    public WaveSurfaceSample sampleAt(
            float worldX,
            float worldZ,
            float timeSeconds,
            int maxWaveTrains,
            WaveSpectrumState spectrumState
    ) {
        return sampleAt((double) worldX, worldZ, timeSeconds, maxWaveTrains, spectrumState);
    }

    /**
     * Evaluates the spectrum using double world coordinates and time.
     *
     * <p>The returned sample remains float-sized for Minecraft consumers, but
     * phase construction stays precise enough to preserve half-block variation
     * after long-running worlds and near the world border.</p>
     */
    public WaveSurfaceSample sampleAt(
            double worldX,
            double worldZ,
            double timeSeconds,
            int maxWaveTrains,
            WaveSpectrumState spectrumState
    ) {
        return sampleAt(worldX, worldZ, timeSeconds, maxWaveTrains, spectrumState, 0.0f, 0.0f);
    }

    /**
     * Evaluates a profile after rotating its authored directional spread onto
     * a local flow heading.
     *
     * <p>Callers use this for river profiles backed by synchronized canonical
     * current. A zero or non-finite flow retains the authored directions.</p>
     */
    public WaveSurfaceSample sampleAt(
            double worldX,
            double worldZ,
            double timeSeconds,
            int maxWaveTrains,
            WaveSpectrumState spectrumState,
            float flowDirectionX,
            float flowDirectionZ
    ) {
        return sampleAt(worldX, worldZ, timeSeconds, maxWaveTrains, spectrumState,
                flowDirectionX, flowDirectionZ, 24.0f);
    }

    /** Samples the existing spectrum with a cached-depth shoaling envelope. */
    public WaveSurfaceSample sampleAt(
            double worldX, double worldZ, double timeSeconds, int maxWaveTrains,
            WaveSpectrumState spectrumState, float flowDirectionX, float flowDirectionZ,
            float waterDepth
    ) {
        float depthScale = DepthWaveResponse.amplitudeScale(waterDepth);
        int count = Math.max(0, Math.min(waveCount, maxWaveTrains));
        if (count == 0) {
            return WaveSurfaceSample.flat();
        }
        WaveSpectrumState spectrum = spectrumState == null
                ? WaveSpectrumState.NEUTRAL
                : spectrumState;
        double safeWorldX = finiteOrZero(worldX);
        double safeWorldZ = finiteOrZero(worldZ);
        double safeTimeSeconds = finiteOrZero(timeSeconds);
        float flowLengthSquared = flowDirectionX * flowDirectionX + flowDirectionZ * flowDirectionZ;
        boolean flowAligned = Float.isFinite(flowLengthSquared) && flowLengthSquared > 1.0e-8f;
        float normalizedFlowX = 1.0f;
        float normalizedFlowZ = 0.0f;
        if (flowAligned) {
            float inverseFlowLength = 1.0f / (float) Math.sqrt(flowLengthSquared);
            normalizedFlowX = flowDirectionX * inverseFlowLength;
            normalizedFlowZ = flowDirectionZ * inverseFlowLength;
        }

        float displacementX = 0.0f;
        float height = 0.0f;
        float displacementZ = 0.0f;
        float velocityX = 0.0f;
        float velocityY = 0.0f;
        float velocityZ = 0.0f;

        float rawCombinedSteepness = 0.0f;
        for (int i = 0; i < count; i++) {
            float directionX = dirX[i];
            float directionZ = dirZ[i];
            if (flowAligned) {
                float rotatedX = normalizedFlowX * directionX
                        - normalizedFlowZ * directionZ;
                float rotatedZ = normalizedFlowZ * directionX
                        + normalizedFlowX * directionZ;
                directionX = rotatedX;
                directionZ = rotatedZ;
            }
            float energyScale = componentEnergyScale(i, spectrum, directionX, directionZ);
            rawCombinedSteepness += steepness[i] * amplitude[i]
                    * energyScale * depthScale * waveNumber[i];
        }
        float horizontalBudgetScale = rawCombinedSteepness > MAX_COMBINED_STEEPNESS
                ? MAX_COMBINED_STEEPNESS / rawCombinedSteepness
                : 1.0f;

        // Tangents start as the flat X and Z axes. Their cross product gives
        // an analytic normal without noisy neighboring height samples.
        float tangentXX = 1.0f;
        float tangentXY = 0.0f;
        float tangentXZ = 0.0f;
        float tangentZX = 0.0f;
        float tangentZY = 0.0f;
        float tangentZZ = 1.0f;

        for (int i = 0; i < count; i++) {
            float authoredDirectionX = dirX[i];
            float authoredDirectionZ = dirZ[i];
            if (flowAligned) {
                // The profile is authored around +X. Use flow and its right
                // vector as a rotated basis without collapsing component spread.
                float rotatedX = normalizedFlowX * authoredDirectionX
                        - normalizedFlowZ * authoredDirectionZ;
                float rotatedZ = normalizedFlowZ * authoredDirectionX
                        + normalizedFlowX * authoredDirectionZ;
                authoredDirectionX = rotatedX;
                authoredDirectionZ = rotatedZ;
            }
            float directionX = authoredDirectionX;
            float directionZ = authoredDirectionZ;
            float k = waveNumber[i];
            float omega = angularFrequency[i];
            float energyScale = componentEnergyScale(i, spectrum, directionX, directionZ);
            float waveAmplitude = amplitude[i] * energyScale * depthScale;
            float horizontalScale = steepness[i] * waveAmplitude * horizontalBudgetScale;
            double phase = Math.IEEEremainder(
                    k * (directionX * safeWorldX + directionZ * safeWorldZ)
                            - omega * safeTimeSeconds
                            + phaseOffset[i],
                    TWO_PI_DOUBLE
            );
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

        float horizontalJacobian = tangentXX * tangentZZ - tangentXZ * tangentZX;

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

        float slope = Math.min(
                4.0f,
                (float) Math.sqrt(normalX * normalX + normalZ * normalZ)
                        / Math.max(1.0e-4f, Math.abs(normalY))
        );
        float crestStrength = clamp01((1.0f - horizontalJacobian) / 0.35f);

        return new WaveSurfaceSample(
                displacementX,
                height,
                displacementZ,
                normalX,
                normalY,
                normalZ,
                velocityX,
                velocityY,
                velocityZ,
                slope,
                crestStrength
        );
    }

    /**
     * Returns the effective summed steepness after the fold-prevention budget
     * has been applied to the supplied environmental spectrum.
     */
    public float combinedSteepness(WaveSpectrumState spectrumState) {
        WaveSpectrumState spectrum = spectrumState == null
                ? WaveSpectrumState.NEUTRAL
                : spectrumState;
        float combined = 0.0f;
        for (int i = 0; i < waveCount; i++) {
            float energyScale = componentEnergyScale(i, spectrum, dirX[i], dirZ[i]);
            combined += steepness[i] * amplitude[i] * energyScale * waveNumber[i];
        }
        return Math.min(MAX_COMBINED_STEEPNESS, combined);
    }

    private float componentEnergyScale(
            int index,
            WaveSpectrumState spectrum,
            float directionX,
            float directionZ
    ) {
        float componentBlend = waveCount <= 1 ? 0.0f : index / (float) (waveCount - 1);
        float energyScale = spectrum.swellScale()
                + (spectrum.chopScale() - spectrum.swellScale()) * componentBlend;
        float windAlignment = Math.max(
                0.0f,
                directionX * spectrum.windDirectionX()
                        + directionZ * spectrum.windDirectionZ()
        );
        float alignedEnergy = MIN_DIRECTIONAL_ENERGY
                + windAlignment * DIRECTIONAL_ENERGY_RANGE;
        return energyScale * (
                1.0f + spectrum.directionBlend() * (alignedEnergy - 1.0f)
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
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
            .wave(0, 0.185f, 22.0f, 0.85f, 0.53f, 0.68f, 0.00f)
            .wave(1, 0.095f, 11.5f, 0.72f, 0.69f, 0.58f, 1.31f)
            .wave(2, 0.038f, 4.8f, 0.35f, -0.94f, 0.44f, 2.17f)
            .wave(3, 0.016f, 2.0f, -0.60f, -0.80f, 0.28f, 0.73f)
            .entityPush(0.012f)
            .boatBob(1.00f)
            .build();

    /** Steeper coastal swell that retains short breaking-wave detail. */
    public static final GerstnerWaveProfile COAST = new Builder(4, 6.0f)
            .wave(0, 0.160f, 15.0f, 0.92f, 0.39f, 0.78f, 0.00f)
            .wave(1, 0.082f, 7.5f, 0.83f, 0.56f, 0.67f, 1.18f)
            .wave(2, 0.039f, 3.6f, 0.45f, -0.89f, 0.49f, 2.36f)
            .wave(3, 0.017f, 1.7f, -0.55f, -0.84f, 0.31f, 0.69f)
            .entityPush(0.010f)
            .boatBob(0.95f)
            .build();

    /** Directional river motion with small, depth-limited ripples. */
    public static final GerstnerWaveProfile RIVER = new Builder(3, 2.0f)
            .wave(0, 0.038f, 6.0f, 1.00f, 0.00f, 0.26f, 0.00f)
            .wave(1, 0.018f, 2.6f, 0.95f, 0.31f, 0.20f, 1.57f)
            .wave(2, 0.009f, 1.2f, 0.98f, -0.20f, 0.12f, 2.64f)
            .entityPush(0.004f)
            .boatBob(0.65f)
            .build();

    /** Subtle wind ripples for enclosed ponds. */
    public static final GerstnerWaveProfile POND = new Builder(2, 1.0f)
            .wave(0, 0.014f, 3.8f, 0.71f, 0.71f, 0.12f, 0.00f)
            .wave(1, 0.007f, 1.9f, -0.71f, 0.71f, 0.08f, 1.91f)
            .entityPush(0.0005f)
            .boatBob(0.35f)
            .build();

    /** Broad but subdued wind waves for inland lakes. */
    public static final GerstnerWaveProfile LAKE = new Builder(3, 8.0f)
            .wave(0, 0.070f, 10.0f, 0.82f, 0.57f, 0.38f, 0.00f)
            .wave(1, 0.031f, 4.6f, 0.62f, -0.78f, 0.28f, 1.46f)
            .wave(2, 0.012f, 2.1f, -0.48f, -0.88f, 0.17f, 2.57f)
            .entityPush(0.003f)
            .boatBob(0.55f)
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
