package com.thunder.wildernessodysseyapi.changelog.tool;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ChangelogClassifier {

    private static final Pattern CONVENTIONAL_PREFIX = Pattern.compile(
            "(?i)^(feat|feature|fix|perf|refactor|docs|build|chore|remove)(\\([^)]*\\))?!?:\\s*"
    );

    private ChangelogClassifier() {
    }

    static Map<ChangeCategory, List<String>> classify(List<ChangelogCommit> commits) {
        Map<ChangeCategory, LinkedHashMap<String, AggregatedChange>> aggregated =
                new EnumMap<>(ChangeCategory.class);
        for (ChangeCategory category : ChangeCategory.values()) {
            aggregated.put(category, new LinkedHashMap<>());
        }

        for (ChangelogCommit commit : commits) {
            String summary = normalizeSummary(commit.subject());
            ChangeCategory category = categoryFor(commit.subject());
            String key = summary.toLowerCase(Locale.ROOT);
            AggregatedChange change = aggregated.get(category)
                    .computeIfAbsent(key, ignored -> new AggregatedChange(summary));
            change.areas().addAll(inferAreas(commit.changedPaths()));
        }

        Map<ChangeCategory, List<String>> result = new EnumMap<>(ChangeCategory.class);
        for (ChangeCategory category : ChangeCategory.values()) {
            List<String> lines = aggregated.get(category).values().stream()
                    .map(AggregatedChange::render)
                    .toList();
            result.put(category, lines);
        }
        return result;
    }

    private static ChangeCategory categoryFor(String subject) {
        String normalized = CONVENTIONAL_PREFIX.matcher(subject.strip()).replaceFirst("")
                .toLowerCase(Locale.ROOT);
        String original = subject.strip().toLowerCase(Locale.ROOT);
        if (startsWithAny(original, "feat", "feature", "add", "added", "create", "implement", "introduce",
                "enable", "register")) {
            return ChangeCategory.ADDED;
        }
        if (startsWithAny(original, "fix", "fixed", "repair", "resolve", "resolved", "correct", "prevent",
                "avoid", "harden", "restore")) {
            return ChangeCategory.FIXED;
        }
        if (startsWithAny(original, "remove", "removed", "delete", "drop", "revert", "disable")) {
            return ChangeCategory.REMOVED;
        }
        if (startsWithAny(normalized, "add", "create", "implement", "enable", "register")) {
            return ChangeCategory.ADDED;
        }
        return ChangeCategory.CHANGED;
    }

    private static boolean startsWithAny(String text, String... prefixes) {
        for (String prefix : prefixes) {
            if (text.equals(prefix) || text.startsWith(prefix + " ") || text.startsWith(prefix + ":")
                    || text.startsWith(prefix + "(")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSummary(String subject) {
        String summary = CONVENTIONAL_PREFIX.matcher(subject.strip()).replaceFirst("").strip();
        if (summary.isEmpty()) {
            return "Updated project files";
        }
        return Character.toUpperCase(summary.charAt(0)) + summary.substring(1);
    }

    private static Set<String> inferAreas(List<String> paths) {
        Set<String> areas = new LinkedHashSet<>();
        for (String rawPath : paths) {
            String path = rawPath.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (path.startsWith("docs/")) {
                areas.add("Documentation");
            } else if (path.equals("build.gradle") || path.startsWith("gradle")) {
                areas.add("Build");
            } else if (path.contains("/watersystem/") || path.contains("gerstner_water")) {
                areas.add("Water system");
            } else if (path.contains("/worldgen/") || path.contains("/structures/")) {
                areas.add("World generation");
            } else if (path.contains("/cloak/")) {
                areas.add("Cloak");
            } else if (path.contains("/riftfall/") || path.contains("/temporalrift/")) {
                areas.add("Rift systems");
            } else if (path.contains("/telemetry/")) {
                areas.add("Telemetry");
            } else if (path.contains("/ai/")) {
                areas.add("AI");
            } else if (path.contains("/mixin/")) {
                areas.add("Mixins");
            } else if (path.contains("/config/")) {
                areas.add("Configuration");
            } else if (path.contains("/assets/")) {
                areas.add("Assets");
            } else if (path.contains("/data/")) {
                areas.add("Data packs");
            }
        }
        return areas;
    }

    private record AggregatedChange(String summary, Set<String> areas) {

        AggregatedChange(String summary) {
            this(summary, new LinkedHashSet<>());
        }

        String render() {
            if (areas.isEmpty()) {
                return summary;
            }
            List<String> visibleAreas = new ArrayList<>(areas).subList(0, Math.min(areas.size(), 3));
            return summary + " [" + String.join(", ", visibleAreas) + "]";
        }
    }
}
