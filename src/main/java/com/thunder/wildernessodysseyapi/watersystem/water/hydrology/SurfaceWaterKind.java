package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;

/**
 * Ownership category for reversible watershed-created surface water.
 *
 * <p>The ordinal is persisted, so new values must be appended. {@link #FLOOD}
 * retains the original version-one meaning while the remaining categories let
 * recession use groundwater- and weather-specific retention rules.</p>
 */
public enum SurfaceWaterKind {
    NONE,
    FLOOD,
    RAIN_POND,
    WETLAND,
    SPRING;

    /** Decodes a persisted id with a safe legacy-flood fallback. */
    public static SurfaceWaterKind fromId(int id) {
        SurfaceWaterKind[] values = values();
        return id >= 0 && id < values.length ? values[id] : FLOOD;
    }

    /** Returns the synchronized dynamic watershed feature represented by this water. */
    public WaterFeature waterFeature() {
        return switch (this) {
            case RAIN_POND -> WaterFeature.POND;
            case WETLAND -> WaterFeature.WETLAND;
            case SPRING -> WaterFeature.STREAM;
            case NONE, FLOOD -> WaterFeature.NONE;
        };
    }

    /** Returns whether this kind is groundwater- or rain-fed standing water. */
    public boolean standingWater() {
        return this == RAIN_POND || this == WETLAND || this == SPRING;
    }
}
