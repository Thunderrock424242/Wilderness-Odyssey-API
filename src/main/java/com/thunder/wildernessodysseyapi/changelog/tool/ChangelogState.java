package com.thunder.wildernessodysseyapi.changelog.tool;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ChangelogState(
        String version,
        String previousVersion,
        String baseReference,
        String headCommit,
        LocalDate generatedDate
) {

    private static final Pattern STATE_PATTERN = Pattern.compile(
            "<!-- changelog-generator version=([^ ]+) previous=([^ ]+) base=([^ ]+) head=([^ ]+) generated=([^ ]+) -->"
    );

    static Optional<ChangelogState> parse(String document) {
        Matcher matcher = STATE_PATTERN.matcher(document);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String previous = matcher.group(2).equals("none") ? null : matcher.group(2);
        return Optional.of(new ChangelogState(
                matcher.group(1),
                previous,
                matcher.group(3),
                matcher.group(4),
                LocalDate.parse(matcher.group(5))
        ));
    }

    String metadataLine() {
        return "<!-- changelog-generator version=" + version
                + " previous=" + (previousVersion == null ? "none" : previousVersion)
                + " base=" + baseReference
                + " head=" + headCommit
                + " generated=" + generatedDate + " -->";
    }
}
