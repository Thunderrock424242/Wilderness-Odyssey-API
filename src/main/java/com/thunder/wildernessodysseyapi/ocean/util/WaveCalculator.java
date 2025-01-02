package com.thunder.wildernessodysseyapi.ocean.util;

public class WaveCalculator {
    public static double calculateWaveHeight(long time, int x, int z) {
        double tide = Math.sin(0.001 * time) * 2; // Long-period tides
        double wave = Math.sin(0.1 * (x + time)) * 0.5; // Shorter waves
        return tide + wave;
    }

    public static double calculateFlowSpeed(long time, int x, int z) {
        return Math.sin(0.05 * (time + x)) * 0.1;
    }
}
