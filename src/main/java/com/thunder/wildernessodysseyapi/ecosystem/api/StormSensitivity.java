package com.thunder.wildernessodysseyapi.ecosystem.api;

import java.util.Objects;

/**
 * Immutable species response profile for significant approaching weather.
 *
 * @param detectionDistanceBlocks maximum distance to the current storm edge
 * @param minimumIntensity minimum projected tracker intensity worth sensing
 * @param shelterPreference preferred bounded shelter category
 * @param alertness normalized tendency to react earlier and remain vigilant
 */
public record StormSensitivity(
        int detectionDistanceBlocks,
        double minimumIntensity,
        ShelterPreference shelterPreference,
        double alertness
) {

    public static final StormSensitivity GENERIC = new StormSensitivity(
            1_200, 0.50, ShelterPreference.ANY_COVER, 0.55);
    public static final StormSensitivity HERD = new StormSensitivity(
            1_500, 0.42, ShelterPreference.ANY_COVER, 0.76);
    public static final StormSensitivity BIRD = new StormSensitivity(
            1_800, 0.34, ShelterPreference.DENSE_CANOPY, 0.88);
    public static final StormSensitivity AQUATIC = new StormSensitivity(
            800, 0.58, ShelterPreference.NONE, 0.34);

    public StormSensitivity {
        detectionDistanceBlocks = Math.max(64, Math.min(4_096, detectionDistanceBlocks));
        minimumIntensity = unit(minimumIntensity);
        shelterPreference = Objects.requireNonNullElse(shelterPreference, ShelterPreference.ANY_COVER);
        alertness = unit(alertness);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
