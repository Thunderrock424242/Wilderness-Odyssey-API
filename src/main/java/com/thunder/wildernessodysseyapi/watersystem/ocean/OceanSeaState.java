package com.thunder.wildernessodysseyapi.watersystem.ocean;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import net.minecraft.world.level.Level;

/**
 * Derives a deterministic ocean state from server-owned world weather.
 *
 * <p>Rain, thunder, game time, and the dimension key are already synchronized
 * Minecraft state. The server samples them into a compact snapshot for clients,
 * keeping wind direction, wave energy, shore breaking, and gameplay forces on
 * one timeline without altering vanilla water tags or fluid blocks.</p>
 */
public final class OceanSeaState {

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float WIND_TURN_PERIOD_TICKS = 96_000.0f;
    private static final float GUST_PERIOD_TICKS = 420.0f;

    /** Calm fallback used before a multiplayer snapshot arrives. */
    public static final Sample CALM = new Sample(
            0.18f,
            1.0f,
            0.0f,
            2.5f,
            0.82f,
            0.72f,
            0.14f,
            0.0f
    );

    private OceanSeaState() {
    }

    /** Samples the authoritative environmental sea state for a world. */
    public static Sample sample(Level level, float partialTick) {
        float rain = clamp01(level.getRainLevel(partialTick));
        float thunder = clamp01(level.getThunderLevel(partialTick));
        float time = level.getGameTime() + partialTick;
        int dimensionHash = level.dimension().location().hashCode();
        float phase = ((dimensionHash & 0xFFFF) / 65_535.0f) * TWO_PI;

        float gust = 0.5f + 0.5f * (float) Math.sin(time * TWO_PI / GUST_PERIOD_TICKS + phase * 1.7f);
        float weatherEnergy = clamp01(rain * 0.55f + thunder * 0.70f);
        float seaStrength = clamp01(0.14f + weatherEnergy * (0.68f + gust * 0.18f));

        // Wind direction changes over several Minecraft days. Short gusts alter
        // energy, not heading, avoiding visually noisy direction reversals.
        float windAngle = phase
                + time * TWO_PI / WIND_TURN_PERIOD_TICKS
                + (float) Math.sin(time * TWO_PI / 24_000.0f + phase) * 0.22f;
        float windX = (float) Math.cos(windAngle);
        float windZ = (float) Math.sin(windAngle);
        float windSpeed = 2.0f + seaStrength * 13.0f;
        float swellScale = 0.72f + seaStrength * 1.02f;
        float chopScale = 0.50f + seaStrength * 1.85f;
        float directionBlend = 0.10f + seaStrength * 0.72f;
        float breakingStrength = smoothStep(0.22f, 0.92f, seaStrength);

        return new Sample(
                seaStrength,
                windX,
                windZ,
                windSpeed,
                swellScale,
                chopScale,
                directionBlend,
                breakingStrength
        );
    }

    /** Complete bounded state shared by rendering, physics, and networking. */
    public record Sample(
            float strength,
            float windDirectionX,
            float windDirectionZ,
            float windSpeed,
            float swellScale,
            float chopScale,
            float directionBlend,
            float breakingStrength
    ) {
        public Sample {
            strength = finiteClamp(strength, 0.0f, 1.0f, 0.18f);
            windSpeed = finiteClamp(windSpeed, 0.0f, 40.0f, 2.5f);
            swellScale = finiteClamp(swellScale, 0.0f, 3.0f, 1.0f);
            chopScale = finiteClamp(chopScale, 0.0f, 4.0f, 1.0f);
            directionBlend = finiteClamp(directionBlend, 0.0f, 1.0f, 0.0f);
            breakingStrength = finiteClamp(breakingStrength, 0.0f, 1.0f, 0.0f);

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

        /** Converts the environmental state into Gerstner spectrum modifiers. */
        public WaveSpectrumState spectrum() {
            return new WaveSpectrumState(
                    swellScale,
                    chopScale,
                    windDirectionX,
                    windDirectionZ,
                    directionBlend
            );
        }

        /** Smooths infrequent network snapshots without predicting server weather. */
        public Sample interpolate(Sample target, float factor) {
            float t = clamp01(factor);
            float blendedWindX = lerp(windDirectionX, target.windDirectionX, t);
            float blendedWindZ = lerp(windDirectionZ, target.windDirectionZ, t);
            return new Sample(
                    lerp(strength, target.strength, t),
                    blendedWindX,
                    blendedWindZ,
                    lerp(windSpeed, target.windSpeed, t),
                    lerp(swellScale, target.swellScale, t),
                    lerp(chopScale, target.chopScale, t),
                    lerp(directionBlend, target.directionBlend, t),
                    lerp(breakingStrength, target.breakingStrength, t)
            );
        }
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float first, float second, float factor) {
        return first + (second - first) * factor;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
