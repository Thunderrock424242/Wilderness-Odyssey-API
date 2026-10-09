package com.thunder.wildernessodysseyapi.diagnostics.performance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Presence-based compatibility evidence, never an active-patch assertion.
 * Unknown configuration preserves observation-only behavior. No Phase 0 entry
 * grants WO permission to take over Minecraft or a third-party subsystem.
 */
public final class PerformanceOwnershipRegistry {
    private PerformanceOwnershipRegistry() { }

    public static Map<PerformanceArea, Ownership> detect(Map<String, String> installedMods) {
        Map<PerformanceArea, List<Candidate>> found = new EnumMap<>(PerformanceArea.class);
        for (PerformanceArea area : PerformanceArea.values()) found.put(area, new ArrayList<>());
        installedMods.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(mod -> {
            String id = mod.getKey();
            if (id.equals("c2me") || id.startsWith("c2me_")) {
                add(found, mod, "chunk scheduler", PerformanceArea.CHUNK_LOADING, PerformanceArea.WORLDGEN);
                add(found, mod, "chunk persistence", PerformanceArea.SAVE, PerformanceArea.CHUNK_IO);
            }
            switch (id) {
                case "shinoyuki_betterautosave", "betterautosave" ->
                        add(found, mod, "chunk persistence", PerformanceArea.SAVE, PerformanceArea.CHUNK_IO);
                case "smoothchunk" -> add(found, mod, "chunk persistence", PerformanceArea.SAVE);
                case "fastasyncworldsave" -> add(found, mod, "metadata persistence", PerformanceArea.SAVE);
                case "lithium", "canary", "radium" -> add(found, mod, "tick internals", PerformanceArea.TICK_OPTIMIZATION);
                case "modernfix" -> add(found, mod, "startup/resources", PerformanceArea.RESOURCE_LOADING);
                case "ferritecore" -> add(found, mod, "memory deduplication", PerformanceArea.MEMORY);
                case "scalablelux", "starlight" -> add(found, mod, "light engine", PerformanceArea.LIGHTING);
                case "sodium", "embeddium" -> add(found, mod, "terrain rendering", PerformanceArea.RENDERING);
                case "immediatelyfast" -> add(found, mod, "immediate drawing", PerformanceArea.RENDERING);
                default -> { }
            }
        });
        Map<PerformanceArea, Ownership> result = new EnumMap<>(PerformanceArea.class);
        found.forEach((area, candidates) -> {
            boolean overlap = candidates.size() > 1;
            if (area == PerformanceArea.RENDERING) {
                overlap = candidates.stream().filter(candidate -> candidate.scope().equals("terrain rendering")).count() > 1;
            }
            // Component IDs from one C2ME installation are one candidate family.
            if (candidates.size() > 1 && candidates.stream().allMatch(candidate -> candidate.modId().startsWith("c2me"))) {
                overlap = false;
            }
            String baseline = area == PerformanceArea.WO_BACKGROUND_WORK
                    ? "WO existing Async/Background/Tick/Data Engine owners"
                    : "Minecraft/NeoForge; unrecognized patches remain possible";
            result.put(area, new Ownership(baseline, List.copyOf(candidates), overlap, false));
        });
        return Map.copyOf(result);
    }

    private static void add(Map<PerformanceArea, List<Candidate>> found, Map.Entry<String, String> mod,
                            String scope, PerformanceArea... areas) {
        Candidate candidate = new Candidate(mod.getKey(), mod.getValue(), scope, "activity unverified");
        for (PerformanceArea area : areas) found.get(area).add(candidate);
    }

    public record Candidate(String modId, String version, String scope, String activity) { }
    public record Ownership(String baseline, List<Candidate> candidates, boolean overlap, boolean woTakeoverAllowed) { }
}
