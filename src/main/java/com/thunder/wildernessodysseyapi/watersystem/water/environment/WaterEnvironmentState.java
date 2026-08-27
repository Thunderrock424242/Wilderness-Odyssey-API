package com.thunder.wildernessodysseyapi.watersystem.water.environment;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;

/**
 * Read-only composition of weather, tide, current, and coarse body exposure.
 *
 * <p>This record owns no simulation state. It translates existing synchronized
 * sea state and generated-water metadata into one bounded description shared
 * by wave and optical consumers. Fetch is a constant-time proxy based on body
 * type, local depth, chunk-column volume, and shoreline contact; it never scans
 * blocks or loads chunks.</p>
 */
public record WaterEnvironmentState(
        float windSpeed,
        float windDirectionX,
        float windDirectionZ,
        float stormIntensity,
        float rainIntensity,
        float tideHeight,
        float tideRate,
        float currentStrength,
        float turbidity,
        float fetch,
        WaveSpectrumState waveSpectrum
) {

    /** Calm, sheltered default for dry or unavailable surface columns. */
    public static final WaterEnvironmentState CALM_POND = derive(
            WaterBodyClassifier.WaterType.POND,
            OceanSeaState.CALM,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            WaterVolumeChunk.UNITS_PER_BLOCK,
            true
    );

    public WaterEnvironmentState {
        windSpeed = finiteClamp(windSpeed, 0.0f, 40.0f);
        stormIntensity = unit(stormIntensity);
        rainIntensity = unit(rainIntensity);
        tideHeight = finiteClamp(tideHeight, -4.0f, 4.0f);
        tideRate = finiteClamp(tideRate, -1.0f, 1.0f);
        currentStrength = finiteClamp(currentStrength, 0.0f, 8.0f);
        turbidity = unit(turbidity);
        fetch = unit(fetch);
        waveSpectrum = waveSpectrum == null ? WaveSpectrumState.NEUTRAL : waveSpectrum;

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

    /** Builds a bounded environment view from existing subsystem outputs. */
    public static WaterEnvironmentState derive(
            WaterBodyClassifier.WaterType waterType,
            OceanSeaState.Sample seaState,
            float rainIntensity,
            float tideHeight,
            float tideRate,
            float currentX,
            float currentZ,
            float depth,
            long estimatedVolumeUnits,
            boolean shoreline
    ) {
        WaterBodyClassifier.WaterType type = waterType == null
                ? WaterBodyClassifier.WaterType.POND
                : waterType;
        OceanSeaState.Sample sea = seaState == null ? OceanSeaState.CALM : seaState;
        float rain = unit(rainIntensity);
        float current = finiteClamp((float) Math.hypot(currentX, currentZ), 0.0f, 8.0f);
        float fetch = fetchFactor(type, depth, estimatedVolumeUnits, shoreline);
        float baseTurbidity = switch (type) {
            case OCEAN -> 0.10f;
            case COAST -> 0.28f;
            case RIVER -> 0.44f;
            case LAKE -> 0.22f;
            case POND -> 0.36f;
        };
        float turbidity = unit(baseTurbidity
                + rain * 0.14f
                + sea.strength() * 0.10f
                + Math.min(1.0f, current / 1.5f) * 0.12f);
        WaveSpectrumState spectrum = waveSpectrumFor(type, sea, rain, fetch);
        return new WaterEnvironmentState(
                sea.windSpeed(),
                sea.windDirectionX(),
                sea.windDirectionZ(),
                sea.strength(),
                rain,
                WaterBodyClassifier.isOceanic(type) ? tideHeight : 0.0f,
                WaterBodyClassifier.isOceanic(type) ? tideRate : 0.0f,
                current,
                turbidity,
                fetch,
                spectrum
        );
    }

    static float fetchFactor(
            WaterBodyClassifier.WaterType type,
            float depth,
            long estimatedVolumeUnits,
            boolean shoreline
    ) {
        float boundedDepth = Math.max(0.0f, Float.isFinite(depth) ? depth : 0.0f);
        float volumeBlocks = Math.max(0L, estimatedVolumeUnits)
                / (float) WaterVolumeChunk.UNITS_PER_BLOCK;
        return switch (type) {
            case OCEAN -> 1.0f;
            case COAST -> 0.76f;
            case RIVER -> 0.22f + smoothStep(1.0f, 5.0f, boundedDepth) * 0.12f;
            case LAKE -> unit(
                    0.12f
                            + smoothStep(2.0f, 10.0f, boundedDepth) * 0.34f
                            + smoothStep(48.0f, 1_024.0f, volumeBlocks) * 0.44f
                            - (shoreline ? 0.10f : 0.0f)
            );
            case POND -> 0.06f + smoothStep(1.0f, 4.0f, boundedDepth) * 0.08f;
        };
    }

    /**
     * Composes the type-aware wave spectrum used by authoritative and client
     * surface samplers. The fetch argument is a bounded exposure estimate, not
     * a request to scan terrain or load neighboring chunks.
     */
    public static WaveSpectrumState waveSpectrumFor(
            WaterBodyClassifier.WaterType type,
            OceanSeaState.Sample sea,
            float rain,
            float fetch
    ) {
        WaterBodyClassifier.WaterType safeType = type == null
                ? WaterBodyClassifier.WaterType.POND
                : type;
        OceanSeaState.Sample safeSea = sea == null ? OceanSeaState.CALM : sea;
        float safeRain = unit(rain);
        float safeFetch = unit(fetch);
        WaveSpectrumState atmospheric = safeSea.spectrum();
        return switch (safeType) {
            case OCEAN -> atmospheric;
            case COAST -> new WaveSpectrumState(
                    lerp(0.70f, atmospheric.swellScale(), safeFetch),
                    lerp(0.65f, atmospheric.chopScale() * 1.08f, safeFetch),
                    atmospheric.windDirectionX(),
                    atmospheric.windDirectionZ(),
                    atmospheric.directionBlend() * safeFetch
            );
            case LAKE -> new WaveSpectrumState(
                    lerp(0.58f, atmospheric.swellScale(), safeFetch * 0.62f),
                    lerp(0.62f, atmospheric.chopScale(), safeFetch * 0.82f),
                    atmospheric.windDirectionX(),
                    atmospheric.windDirectionZ(),
                    atmospheric.directionBlend() * safeFetch
            );
            case POND -> new WaveSpectrumState(
                    0.86f,
                    0.86f + safeRain * 0.18f,
                    atmospheric.windDirectionX(),
                    atmospheric.windDirectionZ(),
                    safeRain * 0.08f
            );
            case RIVER -> WaveSpectrumState.NEUTRAL;
        };
    }

    private static float smoothStep(float minimum, float maximum, float value) {
        float t = unit((value - minimum) / (maximum - minimum));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float first, float second, float factor) {
        return first + (second - first) * unit(factor);
    }

    private static float unit(float value) {
        return finiteClamp(value, 0.0f, 1.0f);
    }

    private static float finiteClamp(float value, float minimum, float maximum) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }
}
