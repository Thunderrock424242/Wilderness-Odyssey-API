package com.thunder.wildernessodysseyapi.temporalrift.echo;

/** Pure celestial-only desynchronization. Never adjusts server time or game scheduling. */
public final class EchoTimeModel {
    private EchoTimeModel() { }

    /** Rare 40 celestial-tick pause followed by a 40-tick recovery, coherent within a region. */
    public static long visualDayTime(long dayTime, long region, boolean enabled, double intensity) {
        if (!enabled || intensity < 0.75) return dayTime;
        long day = Math.floorDiv(dayTime, 24000L);
        long hash = EchoRegionModel.mix(region ^ day * 0x9E3779B97F4A7C15L);
        if (Math.floorMod(hash, 4) != 0) return dayTime;
        long start = 1000 + Math.floorMod(hash >>> 8, 22000);
        long phase = Math.floorMod(dayTime, 24000L) - start;
        if (phase < 0 || phase >= 80) return dayTime;
        return dayTime - (phase < 40 ? phase : 80 - phase);
    }
}
