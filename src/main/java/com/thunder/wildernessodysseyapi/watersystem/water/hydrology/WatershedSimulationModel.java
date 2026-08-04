package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Pure chunk-scale rainfall, runoff, discharge, and water-condition model.
 *
 * <p>The model produces metadata only. It does not inspect chunks, mutate
 * blocks, or access saved data, which keeps behavior deterministic and makes
 * storm/drought/flood transitions independently testable.</p>
 */
public final class WatershedSimulationModel {

    private WatershedSimulationModel() {
    }

    /** Advances one quantized watershed state by one configured simulation pass. */
    public static Result advance(Input input) {
        WatershedConditions previous = input.previous == null
                ? WatershedConditions.NONE
                : input.previous;
        WeatherSample weather = input.weather == null ? WeatherSample.CLEAR : input.weather;
        float rainfallRate = unit(input.rainfallAccumulationRate);
        float drainageRate = unit(input.drainageRate);
        float precipitation = input.weatherEnabled && isLiquid(weather.precipitationType())
                ? unit((float) weather.precipitationIntensity())
                : 0.0f;
        float thawWarmth = input.weatherEnabled
                ? unit((float) ((weather.temperature() - 0.5) / 8.0))
                : 0.0f;
        float snowmeltGenerated = unit((float) weather.surface().snowpack()
                * thawWarmth
                * unit(input.snowmeltRate));
        float recentSnowmelt = unit(previous.recentSnowmelt() * (1.0f - drainageRate * 0.55f)
                + snowmeltGenerated);

        // Rain memory and soil saturation build over multiple passes. Saturated
        // soil converts a larger share of later rain into mobile runoff.
        float recentRainfall = unit(previous.recentRainfall() * (1.0f - drainageRate * 0.38f)
                + precipitation * rainfallRate);
        float warmth = unit((float) ((weather.temperature() + 5.0) / 40.0));
        float dryAir = unit(1.0f - (float) weather.humidity());
        float ventilation = unit(0.25f + (float) weather.wind().magnitude() * 0.75f);
        float evaporation = input.weatherEnabled
                ? drainageRate * (0.18f + warmth * dryAir * ventilation * 0.82f)
                : drainageRate * 0.45f;
        float soilSaturation = unit(previous.soilSaturation()
                + precipitation * rainfallRate * (0.42f - previous.soilSaturation() * 0.12f)
                + snowmeltGenerated * 0.36f
                - evaporation * 0.22f);

        float accumulation = unit(previous.drainageAccumulation());
        float runoffGenerated = precipitation
                * rainfallRate
                * (0.08f + soilSaturation * soilSaturation * 0.78f)
                * (0.40f + accumulation * 0.60f);
        runoffGenerated = unit(runoffGenerated
                + snowmeltGenerated * (0.34f + accumulation * 0.46f));
        float storedBeforeRouting = unit(previous.storedRunoff()
                * (1.0f - drainageRate * 0.22f)
                + runoffGenerated
                + unit(input.incomingRunoff));
        float routeFraction = previous.downstreamDirection() == DrainageDirection.SINK
                ? 0.0f
                : 0.10f + accumulation * 0.20f;
        float downstreamTransfer = input.downstreamAvailable
                ? Math.min(storedBeforeRouting, storedBeforeRouting * routeFraction)
                : 0.0f;
        float storedRunoff = unit(storedBeforeRouting - downstreamTransfer);

        float dischargeTarget = unit(
                downstreamTransfer * 2.2f
                        + storedRunoff * 0.52f
                        + recentRainfall * 0.18f
                        + soilSaturation * 0.16f
        );
        float dischargeResponse = dischargeTarget > previous.riverDischarge() ? 0.24f : 0.075f;
        float riverDischarge = approach(previous.riverDischarge(), dischargeTarget, dischargeResponse);

        float hydrologicPressure = unit(
                riverDischarge * 0.54f
                        + soilSaturation * 0.27f
                        + recentRainfall * 0.19f
        );
        float drought = unit(1.0f - recentRainfall * 0.52f - soilSaturation * 0.48f);
        float maximumOffset = Math.max(0.0f, finiteOrZero(input.maximumWaterLevelOffset));
        float featureScale = waterLevelScale(previous.waterFeature());
        float targetOffset = maximumOffset * featureScale
                * ((hydrologicPressure - 0.42f) * 1.45f - drought * 0.34f);
        targetOffset = clamp(targetOffset, -maximumOffset, maximumOffset);
        float waterLevelOffset = approach(previous.waterLevelOffset(), targetOffset,
                targetOffset > previous.waterLevelOffset() ? 0.12f : 0.055f);

        float floodThreshold = unit(input.floodThreshold);
        float floodRisk = unit(
                riverDischarge * 0.52f
                        + soilSaturation * 0.31f
                        + recentRainfall * 0.17f
        );
        boolean canFlood = previous.hasSurfaceWater()
                && previous.waterFeature() != WaterFeature.COASTAL;
        boolean flooding = canFlood && (floodRisk >= floodThreshold
                || previous.flooding() && floodRisk >= floodThreshold * 0.72f);

        float sedimentTarget = input.sedimentEffects
                ? unit(runoffGenerated * 2.1f + riverDischarge * 0.22f + (flooding ? 0.36f : 0.0f))
                : 0.0f;
        float sediment = approach(previous.sediment(), sedimentTarget,
                sedimentTarget > previous.sediment() ? 0.20f : 0.045f);
        float clarity = unit(1.0f - sediment * 0.88f);
        float debrisTarget = input.debrisEffects
                ? unit(runoffGenerated * 1.4f
                + storedRunoff * 0.24f
                + (flooding ? 0.42f : 0.0f)
                + (float) weather.stormEnergy() * 0.10f)
                : 0.0f;
        float debris = approach(previous.debris(), debrisTarget,
                debrisTarget > previous.debris() ? 0.18f : 0.035f);

        float currentStrength = currentBase(previous.waterFeature())
                + riverDischarge * currentScale(previous.waterFeature());
        DrainageDirection direction = previous.downstreamDirection();
        float currentX = direction.unitX() * currentStrength;
        float currentZ = direction.unitZ() * currentStrength;

        return new Result(
                soilSaturation,
                recentRainfall,
                storedRunoff,
                riverDischarge,
                waterLevelOffset,
                floodRisk,
                flooding,
                sediment,
                clarity,
                currentX,
                currentZ,
                debris,
                downstreamTransfer,
                recentSnowmelt
        );
    }

    private static boolean isLiquid(PrecipitationType type) {
        return type == PrecipitationType.RAIN || type == PrecipitationType.HAIL;
    }

    private static float waterLevelScale(WaterFeature feature) {
        return switch (feature) {
            case STREAM -> 0.72f;
            case RIVER -> 1.0f;
            case LAKE -> 0.82f;
            case WETLAND -> 0.68f;
            case COASTAL, AQUIFER, NONE -> 0.0f;
        };
    }

    private static float currentBase(WaterFeature feature) {
        return switch (feature) {
            case STREAM -> 0.08f;
            case RIVER -> 0.12f;
            case WETLAND -> 0.015f;
            case LAKE, COASTAL, AQUIFER, NONE -> 0.0f;
        };
    }

    private static float currentScale(WaterFeature feature) {
        return switch (feature) {
            case STREAM -> 0.72f;
            case RIVER -> 1.18f;
            case WETLAND -> 0.18f;
            case LAKE -> 0.12f;
            case COASTAL, AQUIFER, NONE -> 0.0f;
        };
    }

    private static float approach(float current, float target, float response) {
        return finiteOrZero(current) + (finiteOrZero(target) - finiteOrZero(current))
                * unit(response);
    }

    private static float unit(float value) {
        return clamp(finiteOrZero(value), 0.0f, 1.0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, finiteOrZero(value)));
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    /** Immutable inputs for one hydrology pass. */
    public record Input(
            WatershedConditions previous,
            WeatherSample weather,
            float incomingRunoff,
            float rainfallAccumulationRate,
            float drainageRate,
            float maximumWaterLevelOffset,
            float floodThreshold,
            boolean weatherEnabled,
            boolean downstreamAvailable,
            boolean sedimentEffects,
            boolean debrisEffects,
            float snowmeltRate
    ) {
        /** Retains the version-one pure-model construction shape. */
        public Input(
                WatershedConditions previous,
                WeatherSample weather,
                float incomingRunoff,
                float rainfallAccumulationRate,
                float drainageRate,
                float maximumWaterLevelOffset,
                float floodThreshold,
                boolean weatherEnabled,
                boolean downstreamAvailable,
                boolean sedimentEffects,
                boolean debrisEffects
        ) {
            this(
                    previous, weather, incomingRunoff, rainfallAccumulationRate, drainageRate,
                    maximumWaterLevelOffset, floodThreshold, weatherEnabled, downstreamAvailable,
                    sedimentEffects, debrisEffects, 0.035f
            );
        }
    }

    /** Immutable results committed to one packed chunk state. */
    public record Result(
            float soilSaturation,
            float recentRainfall,
            float storedRunoff,
            float riverDischarge,
            float waterLevelOffset,
            float floodRisk,
            boolean flooding,
            float sediment,
            float clarity,
            float currentX,
            float currentZ,
            float debris,
            float downstreamTransfer,
            float recentSnowmelt
    ) {
        /** Retains the version-one result shape for tests and optional adapters. */
        public Result(
                float soilSaturation,
                float recentRainfall,
                float storedRunoff,
                float riverDischarge,
                float waterLevelOffset,
                float floodRisk,
                boolean flooding,
                float sediment,
                float clarity,
                float currentX,
                float currentZ,
                float debris,
                float downstreamTransfer
        ) {
            this(
                    soilSaturation, recentRainfall, storedRunoff, riverDischarge,
                    waterLevelOffset, floodRisk, flooding, sediment, clarity,
                    currentX, currentZ, debris, downstreamTransfer, 0.0f
            );
        }
    }
}
