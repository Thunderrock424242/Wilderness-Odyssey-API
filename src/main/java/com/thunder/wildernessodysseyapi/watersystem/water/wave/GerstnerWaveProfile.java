package com.thunder.wildernessodysseyapi.watersystem.water.wave;

/**
 * GerstnerWaveProfile
 *
 * Defines the wave parameters for a single water body type.
 * Multiple wave trains are summed to produce natural-looking seas.
 *
 * Gerstner waves (trochoidal waves) are more physically accurate than
 * sine waves — they produce the characteristic sharp crests and broad
 * troughs seen in real ocean water.
 *
 * Formula for one wave train:
 *   x_offset = amplitude * sin(dot(direction, pos) * frequency + time * speed)
 *   y_offset = amplitude * cos(dot(direction, pos) * frequency + time * speed)
 *   (y is vertical, x is horizontal displacement along wave direction)
 */
public class GerstnerWaveProfile {

    // Number of overlapping wave trains
    public final int waveCount;

    // Per-wave parameters (arrays indexed by wave train index)
    public final float[] amplitude;   // crest height (blocks)
    public final float[] frequency;   // spatial frequency (radians/block)
    public final float[] speed;       // phase speed (radians/second)
    public final float[] dirX;        // wave travel direction (normalised XZ)
    public final float[] dirZ;
    public final float[] steepness;   // Q in Gerstner — 0=sine, 1=sharp crest

    // How far entities/boats are pushed by this wave type
    public final float entityPushStrength;

    // Vertical bobbing strength for boats
    public final float boatBobStrength;

    private GerstnerWaveProfile(Builder b) {
        this.waveCount          = b.waveCount;
        this.amplitude          = b.amplitude;
        this.frequency          = b.frequency;
        this.speed              = b.speed;
        this.dirX               = b.dirX;
        this.dirZ               = b.dirZ;
        this.steepness          = b.steepness;
        this.entityPushStrength = b.entityPushStrength;
        this.boatBobStrength    = b.boatBobStrength;
    }

    // -------------------------------------------------------------------------
    // Evaluate total Y displacement at world (x, z, t)
    // -------------------------------------------------------------------------

    public float getHeightAt(float worldX, float worldZ, float time) {
        float y = 0f;
        for (int i = 0; i < waveCount; i++) {
            float phase = (dirX[i] * worldX + dirZ[i] * worldZ) * frequency[i]
                        + time * speed[i];
            y += amplitude[i] * (float) Math.cos(phase);
        }
        return y;
    }

    /**
     * Get the horizontal push vector at (worldX, worldZ, time).
     * This is what nudges boats and entities sideways with the wave.
     */
    public float[] getPushAt(float worldX, float worldZ, float time) {
        float px = 0f, pz = 0f;
        for (int i = 0; i < waveCount; i++) {
            float phase = (dirX[i] * worldX + dirZ[i] * worldZ) * frequency[i]
                        + time * speed[i];
            float s = steepness[i] * amplitude[i] * (float) Math.sin(phase);
            px -= dirX[i] * s;
            pz -= dirZ[i] * s;
        }
        return new float[]{px * entityPushStrength, pz * entityPushStrength};
    }

    // -------------------------------------------------------------------------
    // Pre-built profiles for each water type
    // -------------------------------------------------------------------------

    /** Large rolling ocean swells with secondary chop */
    public static final GerstnerWaveProfile OCEAN = new Builder(4)
        // Primary swell — large, slow
        .wave(0, 0.22f, 0.55f, 0.9f,  0.85f,  0.53f,  0.55f)
        // Secondary swell — different angle
        .wave(1, 0.14f, 0.80f, 1.1f, -0.60f,  0.80f,  0.45f)
        // Chop — short, fast
        .wave(2, 0.06f, 1.50f, 2.0f,  0.50f, -0.87f,  0.30f)
        // Cross-chop
        .wave(3, 0.04f, 2.00f, 2.5f, -0.70f, -0.71f,  0.20f)
        .entityPush(0.06f)
        .boatBob(0.18f)
        .build();

    /** Gentle directional river current with small ripples */
    public static final GerstnerWaveProfile RIVER = new Builder(3)
        .wave(0, 0.055f, 1.20f, 1.8f,  1.00f,  0.00f,  0.20f)
        .wave(1, 0.030f, 1.80f, 2.2f,  0.95f,  0.31f,  0.15f)
        .wave(2, 0.015f, 2.50f, 3.0f,  0.98f, -0.20f,  0.10f)
        .entityPush(0.018f)
        .boatBob(0.04f)
        .build();

    /** Very subtle surface texture for ponds */
    public static final GerstnerWaveProfile POND = new Builder(2)
        .wave(0, 0.012f, 1.80f, 0.6f,  0.71f,  0.71f,  0.08f)
        .wave(1, 0.008f, 2.40f, 0.8f, -0.71f,  0.71f,  0.05f)
        .entityPush(0.003f)
        .boatBob(0.008f)
        .build();

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        final int waveCount;
        final float[] amplitude, frequency, speed, dirX, dirZ, steepness;
        float entityPushStrength = 0.01f;
        float boatBobStrength    = 0.05f;

        Builder(int n) {
            waveCount = n;
            amplitude = new float[n]; frequency = new float[n];
            speed     = new float[n]; dirX      = new float[n];
            dirZ      = new float[n]; steepness = new float[n];
        }

        /**
         * @param i          wave index
         * @param amp        amplitude (blocks)
         * @param freq       spatial frequency
         * @param spd        phase speed
         * @param dx         direction X (will be normalised)
         * @param dz         direction Z
         * @param steep      steepness 0–1
         */
        Builder wave(int i, float amp, float freq, float spd,
                     float dx, float dz, float steep) {
            // Normalise direction
            float len = (float) Math.sqrt(dx*dx + dz*dz);
            if (len > 1e-6f) { dx /= len; dz /= len; }
            amplitude[i] = amp; frequency[i] = freq; speed[i]     = spd;
            dirX[i]      = dx;  dirZ[i]      = dz;   steepness[i] = steep;
            return this;
        }

        Builder entityPush(float v) { entityPushStrength = v; return this; }
        Builder boatBob(float v)    { boatBobStrength    = v; return this; }

        GerstnerWaveProfile build() { return new GerstnerWaveProfile(this); }
    }
}
