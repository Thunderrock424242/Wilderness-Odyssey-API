package com.thunder.wildernessodysseyapi.ocean.util;

/**
 * The type Wave calculator.
 */
public class WaveCalculator {
    /**
     * Calculate wave height double.
     *
     * @param time the time
     * @param x    the x
     * @param z    the z
     * @return the double
     */
    public static double calculateWaveHeight(long time, int x, int z) {
        double tide = Math.sin(0.001 * time) * 2; // Long-period tides
        double wave = Math.sin(0.1 * (x + time)) * 0.5; // Shorter waves
        return tide + wave;
    }

    /**
     * Calculate flow speed double.
     *
     * @param time the time
     * @param x    the x
     * @param z    the z
     * @return the double
     */
    public static double calculateFlowSpeed(long time, int x, int z) {
        return Math.sin(0.05 * (time + x)) * 0.1;
    }
}
