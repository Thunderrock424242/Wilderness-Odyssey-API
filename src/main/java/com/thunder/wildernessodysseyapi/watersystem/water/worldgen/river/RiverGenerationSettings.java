package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

/** Immutable new-terrain settings captured before a catchment enters generation. */
public record RiverGenerationSettings(boolean enabled, double frequency, int minimumContributingCells,
                                      int streamDepth, int lakeDepth, int maximumCut,
                                      int maximumLakeColumns, int maximumWrites,
                                      boolean lakes, boolean waterfalls, boolean naturalBanks) {
    public RiverGenerationSettings {
        if (!Double.isFinite(frequency) || frequency < 0 || frequency > 1
                || minimumContributingCells < 1 || minimumContributingCells > 64
                || streamDepth < 1 || streamDepth > 3 || lakeDepth < 1 || lakeDepth > 4
                || maximumCut < 1 || maximumCut > 10 || maximumLakeColumns < 1 || maximumLakeColumns > 512
                || maximumWrites < 1 || maximumWrites > 32768) {
            throw new IllegalArgumentException("River generation settings exceed safe bounds");
        }
    }

    /** Invalidates mixed captures after topology-affecting configuration changes. */
    public long fingerprint() {
        long value = Double.doubleToLongBits(frequency) ^ minimumContributingCells;
        value = value * 31 + streamDepth;
        value = value * 31 + lakeDepth;
        value = value * 31 + maximumCut;
        value = value * 31 + maximumLakeColumns;
        value = value * 31 + maximumWrites;
        return value * 31 + (enabled ? 1 : 0) + (lakes ? 2 : 0)
                + (waterfalls ? 4 : 0) + (naturalBanks ? 8 : 0);
    }
}
