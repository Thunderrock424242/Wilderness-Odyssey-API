package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;

/** Pure visual profile for the large-scale silhouette and wind-shaped snow cover. */
final class GlacialTerrainProfile {

    private GlacialTerrainProfile() {
    }

    static int iceRise(
            GlacialBiomeManager.Family family,
            double broad,
            double ridge,
            double signature
    ) {
        double rolling = signedUnit(broad);
        double sharpness = unit(ridge);
        double landform = unit(signature);
        return switch (family) {
            case POLAR_ICE_SHEET -> 4 + (int) Math.round(rolling * 6.0);
            case GLACIAL_HIGHLANDS -> 4
                    + (int) Math.round(sharpness * 14.0 + landform * 8.0);
            case GLACIAL_BASIN -> 1
                    + (int) Math.round(Math.pow(landform, 1.65) * 13.0
                    + Math.max(0.0, broad) * 3.0);
            case MELTWATER_VALLEY -> 1 + (int) Math.round(Math.max(0.0, broad) * 3.0);
            case ICEBERG_COAST -> 1 + (int) Math.round(Math.max(0.0, broad));
        };
    }

    static int snowLayers(
            GlacialBiomeManager.Family family,
            double broadDrift,
            double fineDrift
    ) {
        double drift = signedUnit(broadDrift) * 0.72 + signedUnit(fineDrift) * 0.28;
        int minimum = switch (family) {
            case POLAR_ICE_SHEET, GLACIAL_BASIN -> 2;
            case GLACIAL_HIGHLANDS, MELTWATER_VALLEY, ICEBERG_COAST -> 1;
        };
        int maximum = switch (family) {
            case POLAR_ICE_SHEET, GLACIAL_BASIN -> 7;
            case GLACIAL_HIGHLANDS, MELTWATER_VALLEY -> 5;
            case ICEBERG_COAST -> 4;
        };
        return minimum + (int) Math.round(drift * (maximum - minimum));
    }

    private static double signedUnit(double value) {
        return (Math.max(-1.0, Math.min(1.0, value)) + 1.0) * 0.5;
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
