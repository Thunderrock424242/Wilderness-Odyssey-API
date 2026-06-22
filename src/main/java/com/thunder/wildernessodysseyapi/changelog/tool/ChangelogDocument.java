package com.thunder.wildernessodysseyapi.changelog.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChangelogDocument {

    private static final Pattern VERSION_HEADER = Pattern.compile("(?m)^##\\s+([^\\r\\n]+)\\s*$");
    private static final Pattern STATE_LINE = Pattern.compile("(?m)^<!-- changelog-generator .*-->\\R?");

    private final String preamble;
    private final LinkedHashMap<String, String> sections;
    private final Optional<ChangelogState> state;

    private ChangelogDocument(String preamble, LinkedHashMap<String, String> sections,
            Optional<ChangelogState> state) {
        this.preamble = preamble;
        this.sections = sections;
        this.state = state;
    }

    static ChangelogDocument parse(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        Optional<ChangelogState> state = ChangelogState.parse(normalized);
        String withoutState = STATE_LINE.matcher(normalized).replaceAll("");
        Matcher matcher = VERSION_HEADER.matcher(withoutState);
        List<HeaderMatch> headers = new ArrayList<>();
        while (matcher.find()) {
            headers.add(new HeaderMatch(matcher.group(1).trim(), matcher.start(), matcher.end()));
        }

        int firstHeader = headers.isEmpty() ? withoutState.length() : headers.getFirst().start();
        String preamble = withoutState.substring(0, firstHeader).strip();
        if (preamble.isEmpty()) {
            preamble = "# Wilderness Odyssey Changelog";
        }

        LinkedHashMap<String, String> sections = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            HeaderMatch header = headers.get(index);
            int end = index + 1 < headers.size() ? headers.get(index + 1).start() : withoutState.length();
            sections.putIfAbsent(header.version(), withoutState.substring(header.start(), end).strip());
        }
        return new ChangelogDocument(preamble, sections, state);
    }

    Optional<ChangelogState> state() {
        return state;
    }

    Optional<String> previousVersion(String currentVersion, ChangelogState existingState) {
        if (existingState != null) {
            if (existingState.version().equals(currentVersion)) {
                return Optional.ofNullable(existingState.previousVersion());
            }
            return Optional.of(existingState.version());
        }
        return sections.keySet().stream().filter(version -> !version.equals(currentVersion)).findFirst();
    }

    String withGeneratedSection(String generatedSection, String currentVersion, ChangelogState newState) {
        StringBuilder output = new StringBuilder();
        output.append(preamble.strip()).append('\n');
        output.append(newState.metadataLine()).append("\n\n");
        output.append(generatedSection.strip()).append("\n\n");
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (entry.getKey().equals(currentVersion)) {
                continue;
            }
            output.append(entry.getValue().strip()).append("\n\n");
        }
        return output.toString().stripTrailing() + "\n";
    }

    static String renderSection(String version, String rangeDescription,
            Map<ChangeCategory, List<String>> changes) {
        StringBuilder section = new StringBuilder("## ").append(version).append('\n');
        section.append(rangeDescription).append('\n');
        boolean foundChanges = false;
        for (ChangeCategory category : ChangeCategory.values()) {
            List<String> categoryChanges = changes.getOrDefault(category, List.of());
            if (categoryChanges.isEmpty()) {
                continue;
            }
            foundChanges = true;
            section.append(category.heading()).append(":\n");
            categoryChanges.forEach(change -> section.append("- ").append(change).append('\n'));
        }
        if (!foundChanges) {
            section.append("Changed:\n- No committed project changes were found for this range.\n");
        }
        return section.toString();
    }

    private record HeaderMatch(String version, int start, int end) {
    }
}
