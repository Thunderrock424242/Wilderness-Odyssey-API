package com.thunder.wildernessodysseyapi.environment.glacial;

/** Pure RGB interpolation for family-specific seasonal glacial water colors. */
public final class GlacialWaterColorModel {

    private static final int WINTER_SURFACE = 0x1D6FA8;
    private static final int WINTER_UNDERWATER = 0x082E67;

    private GlacialWaterColorModel() {
    }

    /** Blends cold-season cobalt into the warmer family surface color. */
    public static int surfaceTint(
            GlacialBiomeManager.Family family,
            int biomeTint,
            double meltFraction
    ) {
        int summer = blend(biomeTint, summerColor(family), 0.62);
        return blend(WINTER_SURFACE, summer, unit(meltFraction));
    }

    /** Produces the deeper cobalt absorption color used by underwater optics. */
    public static int underwaterTint(
            GlacialBiomeManager.Family family,
            int biomeTint,
            double meltFraction
    ) {
        int surface = surfaceTint(family, biomeTint, meltFraction);
        return blend(WINTER_UNDERWATER, surface, 0.34 + unit(meltFraction) * 0.20);
    }

    static int summerColor(GlacialBiomeManager.Family family) {
        return switch (family) {
            case ICEBERG_COAST -> 0x31D7E8;
            case MELTWATER_VALLEY -> 0x2AD4E5;
            case GLACIAL_BASIN -> 0x26BFE0;
            case GLACIAL_HIGHLANDS -> 0x279FD0;
            case POLAR_ICE_SHEET -> 0x268BC2;
        };
    }

    static int blend(int from, int to, double amount) {
        double weight = unit(amount);
        int red = channel(from, 16, to, weight);
        int green = channel(from, 8, to, weight);
        int blue = channel(from, 0, to, weight);
        return red << 16 | green << 8 | blue;
    }

    private static int channel(int from, int shift, int to, double amount) {
        int start = from >>> shift & 0xFF;
        int end = to >>> shift & 0xFF;
        return (int) Math.round(start + (end - start) * amount);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
