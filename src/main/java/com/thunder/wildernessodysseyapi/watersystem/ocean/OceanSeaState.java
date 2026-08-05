package com.thunder.wildernessodysseyapi.watersystem.ocean;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Defines the bounded physical response of ocean water to weather.
 *
 * <p>Localized Wilderness weather is converted through
 * {@link #targetFromWeather(WeatherSample, Sample)} and retained by
 * {@link OceanSeaStateField}. {@link #vanillaFallback(Level, float)} remains a
 * deliberate compatibility path when Wilderness does not own weather.</p>
 */
public final class OceanSeaState {

    private static final long WIND_TURN_PERIOD_TICKS = 96_000L;
    private static final long GUST_PERIOD_TICKS = 420L;
    private static final long DAY_PERIOD_TICKS = 24_000L;

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

    /**
     * Derives a localized target from one immutable atmospheric sample.
     *
     * <p>Chop follows current surface wind, while accumulated storm energy and
     * pressure deficit contribute organized swell. When the atmosphere is
     * calm, the previous direction is retained so waves do not snap to an
     * arbitrary compass heading.</p>
     */
    public static Sample targetFromWeather(WeatherSample weather, Sample previous) {
        WeatherSample safeWeather = weather == null ? WeatherSample.CLEAR : weather;
        Sample safePrevious = previous == null ? CALM : previous;
        float windX = (float) safeWeather.wind().x();
        float windZ = (float) safeWeather.wind().z();
        float windMagnitude = finiteClamp((float) Math.hypot(windX, windZ), 0.0f, 1.4142136f, 0.0f);
        float normalizedWind = clamp01(windMagnitude / 1.4142136f);
        if (windMagnitude <= 0.035f) {
            windX = safePrevious.windDirectionX();
            windZ = safePrevious.windDirectionZ();
        }

        float storm = clamp01((float) safeWeather.stormEnergy());
        float precipitation = clamp01((float) safeWeather.precipitationIntensity());
        float thunder = clamp01((float) safeWeather.thunderIntensity());
        float pressureDeficit = clamp01((float) ((1.0 - safeWeather.pressure()) / 0.22));
        float weatherEnergy = clamp01(
                normalizedWind * 0.52f
                        + storm * 0.34f
                        + precipitation * 0.10f
                        + thunder * 0.12f
                        + pressureDeficit * 0.10f
        );
        float strength = clamp01(0.10f + weatherEnergy * 0.90f);
        float windSpeed = 1.5f + normalizedWind * 18.0f + storm * 4.0f;
        float swellScale = 0.62f + strength * 0.92f + storm * 0.28f;
        float chopScale = 0.42f + normalizedWind * 1.45f + precipitation * 0.18f;
        float directionBlend = 0.08f + normalizedWind * 0.78f;
        float breakingStrength = smoothStep(0.20f, 0.88f, strength)
                * (0.72f + storm * 0.28f);

        return new Sample(
                strength,
                windX,
                windZ,
                windSpeed,
                swellScale,
                chopScale,
                directionBlend,
                breakingStrength
        );
    }

    /** Samples synchronized vanilla/external weather when localized authority is inactive. */
    public static Sample vanillaFallback(Level level, float partialTick) {
        float framePartialTick = finiteClamp(partialTick, 0.0f, 1.0f, 0.0f);
        float rain = clamp01(level.getRainLevel(framePartialTick));
        float thunder = clamp01(level.getThunderLevel(framePartialTick));
        long gameTime = level.getGameTime();
        int dimensionHash = level.dimension().location().hashCode();
        double dimensionPhase = ((dimensionHash & 0xFFFF) / 65_535.0) * Math.PI * 2.0;

        double gustPhase = OceanFallbackAnimationClock.periodicPhase(
                gameTime, framePartialTick, GUST_PERIOD_TICKS);
        float gust = 0.5f + 0.5f * (float) Math.sin(gustPhase + dimensionPhase * 1.7);
        float weatherEnergy = clamp01(rain * 0.55f + thunder * 0.70f);
        float seaStrength = clamp01(0.14f + weatherEnergy * (0.68f + gust * 0.18f));

        // Wind direction changes over several Minecraft days. Short gusts alter
        // energy, not heading, avoiding visually noisy direction reversals.
        double windAngle = dimensionPhase
                + OceanFallbackAnimationClock.periodicPhase(
                        gameTime, framePartialTick, WIND_TURN_PERIOD_TICKS)
                + Math.sin(OceanFallbackAnimationClock.periodicPhase(
                        gameTime, framePartialTick, DAY_PERIOD_TICKS) + dimensionPhase) * 0.22;
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

    /**
     * Retained compatibility alias for integrations compiled against the
     * original dimension-wide model. New water code should sample the regional
     * field with world coordinates.
     */
    @Deprecated(forRemoval = false)
    public static Sample sample(Level level, float partialTick) {
        return vanillaFallback(level, partialTick);
    }

    /** Resolves the authoritative regional state for server or client callers. */
    public static Sample sampleAt(
            Level level,
            double worldX,
            double worldZ,
            float partialTick
    ) {
        if (level instanceof ServerLevel serverLevel) {
            return OceanSeaStateField.sampleAt(serverLevel, worldX, worldZ, partialTick);
        }
        return ClientOceanSeaState.sampleAt(level, worldX, worldZ, partialTick);
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
            float startAngle = (float) Math.atan2(windDirectionZ, windDirectionX);
            float targetAngle = (float) Math.atan2(
                    target.windDirectionZ,
                    target.windDirectionX
            );
            float angleDelta = (float) Math.atan2(
                    Math.sin(targetAngle - startAngle),
                    Math.cos(targetAngle - startAngle)
            );
            float blendedAngle = startAngle + angleDelta * t;
            float blendedWindX = (float) Math.cos(blendedAngle);
            float blendedWindZ = (float) Math.sin(blendedAngle);
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

        /**
         * Advances toward a target with slower post-storm decay than buildup.
         *
         * @param target bounded environmental target
         * @param elapsedTicks number of server ticks represented by this update
         * @param buildTimeSeconds response time while energy is increasing
         * @param decayTimeSeconds response time while energy is decreasing
         */
        public Sample approach(
                Sample target,
                long elapsedTicks,
                float buildTimeSeconds,
                float decayTimeSeconds
        ) {
            Sample safeTarget = target == null ? CALM : target;
            float seconds = Math.max(0.0f, elapsedTicks) / 20.0f;
            float responseSeconds = safeTarget.strength >= strength
                    ? Math.max(0.05f, buildTimeSeconds)
                    : Math.max(0.05f, decayTimeSeconds);
            float response = clamp01(1.0f - (float) Math.exp(-seconds / responseSeconds));
            return interpolate(safeTarget, response);
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
