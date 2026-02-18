package com.thunder.wildernessodysseyapi.watersystem.ocean.tide;

import net.minecraft.server.level.ServerLevel;

import static java.lang.Math.PI;

public final class TideAstronomy {
    private TideAstronomy() {
    }

    public static double getMoonPhaseAmplitudeFactor(ServerLevel level) {
        return switch (level.getMoonPhase()) {
            case 0 -> 1.2D;
            case 1, 7 -> 1.1D;
            case 2, 6 -> 1.0D;
            case 3, 5 -> 0.9D;
            case 4 -> 0.8D;
            default -> 1.0D;
        };
    }

    static TideSample computeTideSample(long dayTime, ServerLevel level, TideConfig.TideConfigValues config) {
        long cycleTicks = Math.max(1L, config.cycleTicks());
        long adjustedTime = (dayTime + config.phaseOffsetTicks()) % cycleTicks;
        double phase = (adjustedTime / (double) cycleTicks) * 2.0D * PI;
        double lunar = Math.sin(phase);
        double harmonic = Math.sin(2.0D * phase) * config.harmonicWeight();
        long solarCycleTicks = Math.max(1L, Math.round(cycleTicks * config.solarCycleRatio()));
        long solarAdjustedTime = (dayTime + config.phaseOffsetTicks()) % solarCycleTicks;
        double solarPhase = (solarAdjustedTime / (double) solarCycleTicks) * 2.0D * PI;
        double solar = Math.sin(solarPhase) * config.solarWeight();
        double weightTotal = 1.0D + Math.abs(config.harmonicWeight()) + Math.abs(config.solarWeight());
        double combined = (lunar + harmonic + solar) / weightTotal;
        if (level.isThundering()) {
            combined += config.thunderOffsetNormalized();
        } else if (level.isRaining()) {
            combined += config.rainOffsetNormalized();
        }
        combined = clamp(combined, -1.0D, 1.0D);
        double lunarRate = Math.cos(phase) * (2.0D * PI / cycleTicks);
        double harmonicRate = Math.cos(2.0D * phase) * (4.0D * PI / cycleTicks) * config.harmonicWeight();
        double solarRate = Math.cos(solarPhase) * (2.0D * PI / solarCycleTicks) * config.solarWeight();
        double changePerTick = (lunarRate + harmonicRate + solarRate) / weightTotal;
        return new TideSample(combined, changePerTick);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record TideSample(double height, double changePerTick) {
    }
}
