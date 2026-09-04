package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;

/**
 * Pure deterministic model for the visible lifetime of one coastal wave.
 *
 * <p>Stable segment identity, synchronized game time, and synchronized regional
 * sea state are the complete clock. Clients therefore do not need per-wave
 * packets and cannot accumulate independent random wave timelines.</p>
 */
public final class CoastalWaveModel {

    private static final float BASE_CYCLE_SECONDS = 7.4f;
    private static final float INCOMING_END = 0.24f;
    private static final float SHOALING_END = 0.43f;
    private static final float BREAKING_END = 0.57f;
    private static final float RUN_UP_END = 0.79f;
    private static final float RUN_UP_START = BREAKING_END;

    private CoastalWaveModel() {
    }

    /**
     * Samples a segment at one client frame.
     *
     * @param segmentId stable topology-derived segment identifier
     * @param gameTime synchronized level time in ticks
     * @param partialTick render interpolation fraction
     * @param profile immutable shoreline character
     * @param seaState regional authoritative sea response
     * @param averageSlope average rise per landward block
     * @param onshoreWindFactor dot product of wind and landward normal in [-1, 1]
     */
    public static Sample sample(
            long segmentId,
            long gameTime,
            float partialTick,
            CoastalWaveProfile profile,
            OceanSeaState.Sample seaState,
            float averageBeachSlope,
            float onshoreWindFactor
    ) {
        return sample(
                segmentId, gameTime, partialTick, profile, seaState,
                averageBeachSlope, 0.20f, 3.0f, onshoreWindFactor);
    }

    /** Samples a segment with its cached land and underwater slope metadata. */
    public static Sample sample(
            long segmentId,
            long gameTime,
            float partialTick,
            CoastalWaveProfile profile,
            OceanSeaState.Sample seaState,
            float averageBeachSlope,
            float underwaterSlope,
            float averageWaterDepth,
            float onshoreWindFactor
    ) {
        CoastalWaveProfile safeProfile = profile == null
                ? CoastalWaveProfile.TEMPERATE : profile;
        OceanSeaState.Sample safeSea = seaState == null ? OceanSeaState.CALM : seaState;
        // Promote before adding the float; otherwise long + float loses ticks
        // in old worlds even though the final variable is declared double.
        double frameTick = (double) Math.max(0L, gameTime) + clamp01(partialTick);
        float cycleSeconds = cycleSeconds(safeProfile, safeSea);
        float cycleTicks = cycleSeconds * 20.0f;
        float offset = stableUnitFloat(segmentId) * cycleTicks;
        double cyclePosition = (frameTick + offset) / cycleTicks;
        long cycleIndex = (long) Math.floor(cyclePosition);
        float phase = (float) (cyclePosition - Math.floor(cyclePosition));
        return sampleAtPhase(
                cycleIndex,
                phase,
                cycleSeconds,
                safeProfile,
                safeSea,
                averageBeachSlope,
                underwaterSlope,
                averageWaterDepth,
                onshoreWindFactor,
                segmentId ^ cycleIndex * 0x9E3779B97F4A7C15L
        );
    }

    static Sample sampleAtPhase(
            long cycleIndex,
            float normalizedPhase,
            float cycleSeconds,
            CoastalWaveProfile profile,
            OceanSeaState.Sample seaState,
            float averageSlope,
            float onshoreWindFactor
    ) {
        return sampleAtPhase(
                cycleIndex, normalizedPhase, cycleSeconds, profile, seaState,
                averageSlope, 0.20f, 3.0f, onshoreWindFactor, cycleIndex);
    }

    static Sample sampleAtPhase(
            long cycleIndex,
            float normalizedPhase,
            float cycleSeconds,
            CoastalWaveProfile profile,
            OceanSeaState.Sample seaState,
            float averageSlope,
            float underwaterSlope,
            float averageWaterDepth,
            float onshoreWindFactor,
            long spatialSeed
    ) {
        float phase = wrap01(normalizedPhase);
        Stage stage;
        float stagePhase;
        if (phase < INCOMING_END) {
            stage = Stage.INCOMING;
            stagePhase = phase / INCOMING_END;
        } else if (phase < SHOALING_END) {
            stage = Stage.SHOALING;
            stagePhase = (phase - INCOMING_END) / (SHOALING_END - INCOMING_END);
        } else if (phase < BREAKING_END) {
            stage = Stage.BREAKING;
            stagePhase = (phase - SHOALING_END) / (BREAKING_END - SHOALING_END);
        } else if (phase < RUN_UP_END) {
            stage = Stage.RUN_UP;
            stagePhase = (phase - BREAKING_END) / (RUN_UP_END - BREAKING_END);
        } else {
            stage = Stage.RETREAT;
            stagePhase = (phase - RUN_UP_END) / (1.0f - RUN_UP_END);
        }

        float onshore = clamp01(onshoreWindFactor * 0.5f + 0.5f);
        float baseEnergy = clamp01(
                0.12f
                        + seaState.strength() * 0.58f
                        + seaState.breakingStrength() * 0.24f
                        + Math.min(1.0f, seaState.windSpeed() / 24.0f) * 0.10f
        );
        float coastalEnergy = clamp01(baseEnergy * (0.72f + onshore * 0.28f));
        float safeSlope = finiteClamp(averageSlope, 0.0f, 2.0f, 0.0f);
        float slopeRunUpScale = Math.max(0.28f, 1.0f / (1.0f + safeSlope * 2.8f));
        float maximumRunUp = profile.runUpDistanceBlocks()
                * (0.30f + coastalEnergy * 0.70f)
                * slopeRunUpScale;
        // Ocean swell persists in calm local weather. Wind adds storm energy;
        // it must not reduce ordinary beach surf to a few centimetres.
        float waveHeight = profile.waveHeightMultiplier()
                * (0.75f + coastalEnergy * 1.75f)
                * (1.0f + safeSlope * 0.10f);
        float safeUnderwaterSlope = finiteClamp(underwaterSlope, 0.0f, 4.0f, 0.20f);
        float safeWaterDepth = finiteClamp(averageWaterDepth, 0.0f, 64.0f, 3.0f);
        float breakingDepth = waveHeight * (0.72f + coastalEnergy * 0.46f);
        float bathymetricDistance = breakingDepth / Math.max(0.07f, safeUnderwaterSlope + 0.045f);
        float depthAvailability = clamp01(safeWaterDepth / Math.max(0.05f, breakingDepth));
        float breakerVariation = 0.86f + stableUnitFloat(spatialSeed) * 0.22f;
        float breakerDistance = finiteClamp(
                bathymetricDistance * (0.72f + depthAvailability * 0.28f) * breakerVariation,
                0.35f,
                profile.breakerDistanceBlocks(),
                profile.breakerDistanceBlocks() * 0.55f
        );

        float shoaling = switch (stage) {
            case INCOMING -> smoothStep(stagePhase) * 0.55f;
            case SHOALING -> 0.55f + smoothStep(stagePhase) * 0.45f;
            case BREAKING -> 1.0f;
            case RUN_UP -> 1.0f - stagePhase * 0.72f;
            case RETREAT -> 0.20f * (1.0f - stagePhase);
        };
        float breakerEnvelope = stage == Stage.BREAKING
                ? (float) Math.sin(Math.PI * stagePhase)
                : 0.0f;
        // Preserve the incoming crest at break onset, curl it upward, then
        // collapse into wash. A sine-only lift made the crest vanish at onset.
        float breakerLift = stage == Stage.BREAKING
                ? waveHeight * (1.0f + breakerEnvelope * profile.breakerStrength() * 0.35f)
                * (1.0f - smoothStep(clamp01((stagePhase - 0.5f) * 2.0f)))
                : 0.0f;
        float foam = switch (stage) {
            case INCOMING -> 0.0f;
            case SHOALING -> smoothStep(stagePhase) * 0.14f;
            case BREAKING -> 0.35f + breakerEnvelope * 0.65f;
            case RUN_UP -> 1.0f - stagePhase * 0.38f;
            case RETREAT -> 0.62f * (1.0f - stagePhase);
        };
        foam = clamp01(foam * profile.foamAmount() * (0.38f + coastalEnergy * 0.72f));

        float runUpDistance = switch (stage) {
            case INCOMING, SHOALING, BREAKING -> 0.0f;
            case RUN_UP -> maximumRunUp * easeOutCubic(stagePhase);
            case RETREAT -> maximumRunUp
                    * (1.0f - smoothStep(clamp01(stagePhase * profile.retreatSpeed())));
        };

        float elapsedSinceWashStart = phase >= RUN_UP_START
                ? phase - RUN_UP_START
                : phase + 1.0f - RUN_UP_START;
        float wetnessAgeTicks = elapsedSinceWashStart * cycleSeconds * 20.0f;
        float wetness = clamp01(1.0f
                - wetnessAgeTicks / profile.shorelineWetnessDurationTicks());
        float steepImpact = clamp01(safeSlope / 0.85f);
        float impactScale = 0.72f + steepImpact * 0.58f;
        float spray = stage == Stage.BREAKING
                ? clamp01(breakerEnvelope * coastalEnergy
                * profile.turbulence() * impactScale)
                : 0.0f;
        float crestDistance = switch (stage) {
            case INCOMING -> breakerDistance + 1.35f
                    + (1.0f - smoothStep(stagePhase))
                    * Math.max(1.5f, profile.breakerDistanceBlocks() - breakerDistance);
            case SHOALING -> breakerDistance + (1.0f - smoothStep(stagePhase)) * 1.35f;
            case BREAKING -> breakerDistance * (1.0f - stagePhase * 0.10f);
            case RUN_UP, RETREAT -> 0.0f;
        };

        return new Sample(
                stage,
                cycleIndex,
                phase,
                clamp01(stagePhase),
                coastalEnergy,
                waveHeight * shoaling,
                breakerLift,
                breakerDistance,
                Math.max(0.0f, crestDistance),
                foam,
                Math.max(0.0f, runUpDistance),
                Math.max(0.0f, maximumRunUp),
                wetness,
                spray
        );
    }

    private static float cycleSeconds(
            CoastalWaveProfile profile,
            OceanSeaState.Sample seaState
    ) {
        float weatherFrequency = 0.86f + seaState.strength() * 0.30f;
        return finiteClamp(
                BASE_CYCLE_SECONDS / (profile.waveFrequencyMultiplier() * weatherFrequency),
                2.6f,
                14.0f,
                BASE_CYCLE_SECONDS
        );
    }

    private static float stableUnitFloat(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (mixed >>> 40) / (float) (1 << 24);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0f - clamp01(value);
        return 1.0f - inverse * inverse * inverse;
    }

    private static float smoothStep(float value) {
        float bounded = clamp01(value);
        return bounded * bounded * (3.0f - 2.0f * bounded);
    }

    private static float wrap01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return value - (float) Math.floor(value);
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    /** Visible wave phase used by geometry, foam, sound, and particles. */
    public enum Stage {
        INCOMING,
        SHOALING,
        BREAKING,
        RUN_UP,
        RETREAT
    }

    /** Complete bounded visual state for one segment at one frame. */
    public record Sample(
            Stage stage,
            long cycleIndex,
            float normalizedPhase,
            float stagePhase,
            float energy,
            float waveHeight,
            float breakerLift,
            float breakerDistanceBlocks,
            float crestDistanceFromShoreBlocks,
            float foam,
            float runUpDistanceBlocks,
            float maximumRunUpDistanceBlocks,
            float wetness,
            float spray
    ) {
    }
}
