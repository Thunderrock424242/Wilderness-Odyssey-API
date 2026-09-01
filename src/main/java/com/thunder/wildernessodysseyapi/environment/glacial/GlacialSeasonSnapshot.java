package com.thunder.wildernessodysseyapi.environment.glacial;

/** Immutable, side-safe glacier state derived from the existing seasonal weather boundary. */
public record GlacialSeasonSnapshot(
        GlacialSeason season,
        double meltFraction,
        double freezeFraction,
        boolean calendarAvailable,
        boolean debugOverride
) {
    public static final GlacialSeasonSnapshot POLAR_COLD = new GlacialSeasonSnapshot(
            GlacialSeason.POLAR_COLD,
            0.08,
            0.92,
            false,
            false
    );

    public GlacialSeasonSnapshot {
        season = season == null ? GlacialSeason.POLAR_COLD : season;
        meltFraction = unit(meltFraction);
        freezeFraction = unit(freezeFraction);
    }

    /** Quantized signature used to avoid redundant network packets and mesh invalidations. */
    public int visualSignature() {
        int meltBucket = Math.max(0, Math.min(15, (int) Math.round(meltFraction * 15.0)));
        return season.ordinal() | (meltBucket << 4) | (calendarAvailable ? 1 << 8 : 0)
                | (debugOverride ? 1 << 9 : 0);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
