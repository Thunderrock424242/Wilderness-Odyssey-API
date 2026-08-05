package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;

/**
 * Pure formation and retention rules for ponds, wetlands, and springs.
 *
 * <p>World inspection remains in the runtime manager. Keeping hydrologic
 * thresholds here makes rain-body behavior deterministic and independently
 * testable without loading Minecraft chunks.</p>
 */
public final class TransientSurfaceWaterModel {

    private TransientSurfaceWaterModel() {
    }

    /** Returns how far a candidate surface sits below its lowest sampled rim. */
    public static int depressionDepth(int centerSurfaceY, int... rimSurfaceY) {
        if (rimSurfaceY == null || rimSurfaceY.length == 0) {
            return 0;
        }
        int lowestRim = Integer.MAX_VALUE;
        for (int height : rimSurfaceY) {
            lowestRim = Math.min(lowestRim, height);
        }
        return Math.max(0, Math.min(8, lowestRim - centerSurfaceY));
    }

    /** Classifies one safe terrain candidate, or returns {@link SurfaceWaterKind#NONE}. */
    public static SurfaceWaterKind formationKind(
            WatershedConditions conditions,
            int depressionDepth,
            boolean localSink,
            SurfaceWaterKind adjacentKind,
            float pondThreshold,
            float wetlandThreshold,
            float springThreshold
    ) {
        WatershedConditions safe = conditions == null ? WatershedConditions.NONE : conditions;
        SurfaceWaterKind adjacent = adjacentKind == null ? SurfaceWaterKind.NONE : adjacentKind;
        if (!localSink && !adjacent.standingWater()) {
            return SurfaceWaterKind.NONE;
        }
        if (safe.aquiferStorage() >= unit(springThreshold)
                && safe.normalizedWaterTable() >= 0.82f
                && safe.groundwaterDischarge() >= 0.0015f
                && (depressionDepth > 0 || localSink)) {
            return SurfaceWaterKind.SPRING;
        }
        float ponding = pondingScore(safe);
        if (depressionDepth >= 1
                && ponding >= unit(pondThreshold)
                && (localSink || adjacent == SurfaceWaterKind.RAIN_POND)) {
            return SurfaceWaterKind.RAIN_POND;
        }
        float wetness = wetlandScore(safe);
        if (wetness >= unit(wetlandThreshold)
                && (localSink || adjacent == SurfaceWaterKind.WETLAND)) {
            return SurfaceWaterKind.WETLAND;
        }
        return SurfaceWaterKind.NONE;
    }

    /** Returns whether an owned surface cell should survive this recession pass. */
    public static boolean retains(
            SurfaceWaterKind kind,
            WatershedConditions conditions,
            long ageTicks,
            int minimumLifetimeTicks,
            float pondThreshold,
            float wetlandThreshold,
            float springThreshold
    ) {
        SurfaceWaterKind safeKind = kind == null ? SurfaceWaterKind.FLOOD : kind;
        WatershedConditions safe = conditions == null ? WatershedConditions.NONE : conditions;
        if (ageTicks < Math.max(0, minimumLifetimeTicks)) {
            return true;
        }
        return switch (safeKind) {
            case NONE -> false;
            case FLOOD -> safe.flooding()
                    || safe.floodRisk() >= safe.floodThreshold() * 0.72f;
            case RAIN_POND -> pondingScore(safe) >= unit(pondThreshold) * 0.58f
                    || safe.aquiferStorage() >= unit(springThreshold) * 0.72f;
            case WETLAND -> wetlandScore(safe) >= unit(wetlandThreshold) * 0.72f;
            case SPRING -> safe.aquiferStorage() >= unit(springThreshold) * 0.78f
                    && safe.groundwaterDischarge() >= 0.0008f;
        };
    }

    /** Returns the normalized sustained-rain depression-filling pressure. */
    public static float pondingScore(WatershedConditions conditions) {
        WatershedConditions safe = conditions == null ? WatershedConditions.NONE : conditions;
        return unit(safe.recentRainfall() * 0.36f
                + safe.recentSnowmelt() * 0.18f
                + safe.soilSaturation() * 0.28f
                + safe.normalizedWaterTable() * 0.18f);
    }

    /** Returns the normalized shallow-groundwater wetland pressure. */
    public static float wetlandScore(WatershedConditions conditions) {
        WatershedConditions safe = conditions == null ? WatershedConditions.NONE : conditions;
        return unit(safe.soilSaturation() * 0.48f
                + safe.normalizedWaterTable() * 0.42f
                + Math.max(safe.recentRainfall(), safe.recentSnowmelt()) * 0.10f);
    }

    private static float unit(float value) {
        return Math.max(0.0f, Math.min(1.0f, Float.isFinite(value) ? value : 0.0f));
    }
}
