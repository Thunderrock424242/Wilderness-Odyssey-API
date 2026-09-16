package com.thunder.wildernessodysseyapi.performance.ram;

import java.util.ArrayList;
import java.util.List;

public final class RamAdvisorFormatter {
    private RamAdvisorFormatter() {}

    public static List<String> format(RamRecommendation r, int samples, int gcSamples) {
        List<String> lines = new ArrayList<>();
        lines.add("Wilderness Odyssey RAM Advisor");
        lines.add("Current heap max: " + r.currentHeapMaxGiB() + " GiB");
        lines.add(String.format("Stable post-GC live set: %.2f GiB", RamUnits.gib(r.stableLiveSetBytes())));
        lines.add(String.format("Peak observed heap (P99): %.2f GiB", RamUnits.gib(r.peakObservedHeapBytes())));
        lines.add("Pressure: " + r.pressure());
        lines.add("Minimum: " + r.minimumGiB() + " GiB");
        lines.add("Recommended: " + r.recommendedGiB() + " GiB");
        lines.add("Maximum sensible: " + r.maximumSensibleGiB() + " GiB");
        lines.add("Confidence: " + r.confidencePercent() + "% (" + samples + " samples, " + gcSamples + " GC events)");
        if (!r.warnings().isEmpty()) {
            lines.add("Warnings:");
            for (String warning : r.warnings()) lines.add("- " + warning);
        }
        return List.copyOf(lines);
    }
}
