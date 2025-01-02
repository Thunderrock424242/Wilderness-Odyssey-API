package com.thunder.wildernessodysseyapi.ocean.util;

public class WaveCalculator {
    public static double calculateWaveHeight(long time, int x, int z, String waveType) {
        double baseWave = Math.sin(0.1 * (time + x)); // Base wave
        double directionModifier = Math.cos(0.05 * (time + z)); // Directional variation

        if ("beach".equals(waveType)) {
            return baseWave * 0.5 + directionModifier * 0.2; // Smaller waves near beaches
        } else if ("ocean".equals(waveType)) {
            return baseWave * 2 + directionModifier * 1; // Larger waves in oceans
        }
        return 0;
    }

    public static double calculateWaveFlowSpeed(long time, int x, int z) {
        return Math.sin(0.05 * (time + x)) * 0.1; // Controls flow speed for moving entities
    }
}