package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;

/** Pure RGB blending used by client-only drought presentation. */
public final class VegetationColorModel {

    private static final int DRY_GRASS_COLOR = 0xB7A05A;

    private VegetationColorModel() {
    }

    /** Blends a biome-owned grass color toward dry straw under sustained drought. */
    public static int applyDrought(int biomeColor, VegetationClimateState climate) {
        VegetationClimateState safe = climate == null ? VegetationClimateState.DEFAULT : climate;
        double drought = unit((safe.droughtLevel() - 0.28) / 0.72);
        double moistureProtection = 1.0 - safe.moisture() * 0.22;
        return blend(biomeColor, DRY_GRASS_COLOR, drought * moistureProtection * 0.72);
    }

    private static int blend(int from, int to, double amount) {
        double alpha = unit(amount);
        int red = mix((from >> 16) & 0xFF, (to >> 16) & 0xFF, alpha);
        int green = mix((from >> 8) & 0xFF, (to >> 8) & 0xFF, alpha);
        int blue = mix(from & 0xFF, to & 0xFF, alpha);
        return red << 16 | green << 8 | blue;
    }

    private static int mix(int from, int to, double amount) {
        return Math.max(0, Math.min(255, (int) Math.round(from + (to - from) * amount)));
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
