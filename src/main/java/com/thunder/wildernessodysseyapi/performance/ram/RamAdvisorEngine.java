package com.thunder.wildernessodysseyapi.performance.ram;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RamAdvisorEngine {
    private static final int MAX_SAMPLES = 7_200; // ~2 hours at one sample/sec
    private static final int MAX_GC_SAMPLES = 1_024;
    private static final long MIN_HEAP = 6L * RamUnits.GIB;

    private final Deque<RamSample> samples = new ArrayDeque<>();
    private final Deque<GcSample> gcSamples = new ArrayDeque<>();
    private long startedAtMillis;

    public synchronized void reset() {
        samples.clear();
        gcSamples.clear();
        startedAtMillis = System.currentTimeMillis();
    }

    public synchronized void addSample(RamSample sample) {
        if (startedAtMillis == 0L) startedAtMillis = sample.timestampMillis();
        samples.addLast(sample);
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
    }

    public synchronized void addGcSample(GcSample sample) {
        gcSamples.addLast(sample);
        while (gcSamples.size() > MAX_GC_SAMPLES) gcSamples.removeFirst();
    }

    public synchronized int sampleCount() {
        return samples.size();
    }

    public synchronized int gcSampleCount() {
        return gcSamples.size();
    }

    public synchronized RamRecommendation recommend() {
        if (samples.isEmpty()) {
            return new RamRecommendation(0, 0, 0, Runtime.getRuntime().maxMemory(), 0, 0,
                    0.0, RamRecommendation.Pressure.LOW, List.of("Not enough samples yet."));
        }

        List<Long> heapUsed = new ArrayList<>(samples.size());
        long systemTotal = 0L;
        long currentMax = 0L;
        for (RamSample sample : samples) {
            heapUsed.add(sample.heapUsedBytes());
            systemTotal = Math.max(systemTotal, sample.systemTotalBytes());
            currentMax = Math.max(currentMax, sample.heapMaxBytes());
        }

        List<Long> postGc = new ArrayList<>(gcSamples.size());
        long totalGcPause = 0L;
        for (GcSample gc : gcSamples) {
            if (gc.heapAfterBytes() > 0) postGc.add(gc.heapAfterBytes());
            totalGcPause += Math.max(0L, gc.durationMillis());
        }

        long p95Heap = Percentiles.percentile(heapUsed, 0.95);
        long p99Heap = Percentiles.percentile(heapUsed, 0.99);
        long stableLive = postGc.size() >= 5
                ? Percentiles.percentile(postGc, 0.95)
                : Percentiles.percentile(heapUsed, 0.75);

        // Transient pressure above the live set captures worldgen/loading bursts without treating
        // every temporary allocation as permanently required memory.
        long transientBurst = Math.max(0L, p99Heap - stableLive);
        long gcHeadroom = Math.max(2L * RamUnits.GIB, stableLive / 4L);
        long burstHeadroom = Math.min(3L * RamUnits.GIB, Math.max(1L * RamUnits.GIB, transientBurst));

        long minimum = RamUnits.ceilGiB(Math.max(MIN_HEAP, Math.max(stableLive + RamUnits.GIB, p95Heap)));
        long recommended = RamUnits.ceilGiB(Math.max(minimum, stableLive + gcHeadroom + burstHeadroom));

        long systemCap = calculateSystemCap(systemTotal, recommended);
        long maximumSensible = RamUnits.ceilGiB(Math.max(recommended, Math.min(systemCap, recommended + 4L * RamUnits.GIB)));

        double peakRatio = currentMax > 0 ? (double) p99Heap / currentMax : 0.0;
        double liveRatio = currentMax > 0 ? (double) stableLive / currentMax : 0.0;
        RamRecommendation.Pressure pressure = pressureFor(Math.max(peakRatio, liveRatio));

        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - startedAtMillis);
        double timeConfidence = Math.min(1.0, elapsedMillis / (30.0 * 60_000.0));
        double gcConfidence = Math.min(1.0, gcSamples.size() / 20.0);
        double sampleConfidence = Math.min(1.0, samples.size() / 900.0); // 15 min at 1 Hz
        double confidence = clamp01(0.45 * timeConfidence + 0.35 * gcConfidence + 0.20 * sampleConfidence);

        List<String> warnings = new ArrayList<>();
        if (JvmArgumentInspector.hasDuplicateXmx()) {
            warnings.add("Multiple -Xmx arguments detected: " + String.join(", ", JvmArgumentInspector.xmxArguments()));
        }
        if (JvmArgumentInspector.hasDuplicateXms()) {
            warnings.add("Multiple -Xms arguments detected: " + String.join(", ", JvmArgumentInspector.xmsArguments()));
        }
        if (currentMax > 0 && recommended > currentMax) {
            warnings.add("Current maximum heap is below the measured recommendation.");
        }
        if (systemTotal > 0 && currentMax > systemCap) {
            warnings.add("Current heap leaves too little RAM for the OS, native memory, launchers, and other applications.");
        }
        if (gcSamples.size() < 5) {
            warnings.add("Few GC cycles observed; post-GC live-set confidence is still low.");
        }
        if (totalGcPause > 10_000 && elapsedMillis < 15 * 60_000L) {
            warnings.add("High cumulative GC time observed early in the session.");
        }

        return new RamRecommendation(
                minimum,
                recommended,
                maximumSensible,
                currentMax,
                stableLive,
                p99Heap,
                confidence,
                pressure,
                List.copyOf(warnings)
        );
    }

    private static long calculateSystemCap(long systemTotal, long recommended) {
        if (systemTotal <= 0) return Math.max(recommended, 16L * RamUnits.GIB);

        // Keep at least 8 GiB or 40% of physical RAM outside the Java heap, whichever is larger.
        long reserve = Math.max(8L * RamUnits.GIB, (long) (systemTotal * 0.40));
        long cap = systemTotal - reserve;
        return Math.max(recommended, Math.max(6L * RamUnits.GIB, cap));
    }

    private static RamRecommendation.Pressure pressureFor(double ratio) {
        if (ratio >= 0.94) return RamRecommendation.Pressure.CRITICAL;
        if (ratio >= 0.85) return RamRecommendation.Pressure.HIGH;
        if (ratio >= 0.72) return RamRecommendation.Pressure.MODERATE;
        return RamRecommendation.Pressure.LOW;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
