package com.thunder.wildernessodysseyapi.performance.ram;

import java.util.List;

public record RamRecommendation(
        long minimumBytes,
        long recommendedBytes,
        long maximumSensibleBytes,
        long currentHeapMaxBytes,
        long stableLiveSetBytes,
        long peakObservedHeapBytes,
        double confidence,
        Pressure pressure,
        List<String> warnings
) {
    public enum Pressure { LOW, MODERATE, HIGH, CRITICAL }

    public int minimumGiB() {
        return (int) Math.round(RamUnits.gib(minimumBytes));
    }

    public int recommendedGiB() {
        return (int) Math.round(RamUnits.gib(recommendedBytes));
    }

    public int maximumSensibleGiB() {
        return (int) Math.round(RamUnits.gib(maximumSensibleBytes));
    }

    public int currentHeapMaxGiB() {
        return (int) Math.round(RamUnits.gib(currentHeapMaxBytes));
    }

    public int confidencePercent() {
        return (int) Math.round(confidence * 100.0);
    }
}
